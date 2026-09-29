package in.adivritti.core.identity;

import in.adivritti.core.common.util.AadhaarVault;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.DuplicateFlag;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.LinkedSystemRecord;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.entity.ScholarSystemLink;
import in.adivritti.core.identity.repository.ScholarRepository;
import in.adivritti.core.identity.repository.ScholarSystemLinkRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private final UsidResolver resolver;
    private final ScholarRepository scholars;
    private final ScholarSystemLinkRepository links;
    private final AadhaarVault vault;

    public IdentityService(UsidResolver resolver, ScholarRepository scholars,
        ScholarSystemLinkRepository links, AadhaarVault vault) {
        this.resolver = resolver;
        this.scholars = scholars;
        this.links = links;
        this.vault = vault;
    }

    /**
     * Resolve a set of cross-portal records into a single USID.
     *
     * <p>Idempotent: if the same (system, externalId) pair is already linked, the
     * existing USID is returned rather than a duplicate scholar being minted. Any
     * record that already belongs to a different USID raises a duplicate flag for
     * officer adjudication instead of silently merging two people.
     */
    @Transactional
    public IdentityResolveResponse resolve(IdentityResolveRequest req) {
        List<IdentityRecord> records = req.records();
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("At least one identity record is required");
        }
        List<IdentityRecord> sanitized = records.stream().map(this::vaultize).toList();
        IdentityRecord reference = sanitized.get(0);

        // Link each record against the reference. The reference itself is not
        // compared to itself — that always scored 1.0 and inflated confidence.
        List<LinkedSystemRecord> linked = new ArrayList<>(sanitized.size());
        double minScore = 1.0;
        boolean human = false;
        for (IdentityRecord r : sanitized) {
            if (r == reference) continue;
            boolean deterministic = resolver.deterministicMatch(reference, r);
            double score = deterministic ? 1.0 : resolver.score(reference, r);
            minScore = Math.min(minScore, score);
            if (!deterministic && resolver.needsHumanReview(score)) human = true;
            linked.add(new LinkedSystemRecord(r.systemName(), r.externalId(), score,
                deterministic ? "deterministic" : "probabilistic"));
        }
        // A single record is trivially a perfect match; nothing was compared.
        if (linked.isEmpty()) minScore = 1.0;

        Set<UUID> existingUsids = existingUsidsFor(sanitized);
        DuplicateFlag duplicates = detectDuplicates(existingUsids, reference);
        if (duplicates.isDuplicate()) {
            // Never auto-merge two identities. Hand the linked records to the
            // officer queue with the conflicting USIDs attached.
            return new IdentityResolveResponse(
                duplicates.duplicateUsids().get(0), linked, minScore, true, duplicates);
        }

        UUID usid = existingUsids.size() == 1
            ? existingUsids.iterator().next()
            : mintUsid(reference);

        persistLinks(usid, sanitized, reference, linked);

        return new IdentityResolveResponse(usid, linked, minScore, human, duplicates);
    }

    /** Replace any raw Aadhaar with a vault reference key. Never echo the raw value. */
    private IdentityRecord vaultize(IdentityRecord r) {
        if (r == null) throw new IllegalArgumentException("Identity record must not be null");
        if (r.systemName() == null || r.systemName().isBlank()) {
            throw new IllegalArgumentException("Identity record requires a systemName");
        }
        if (r.externalId() == null || r.externalId().isBlank()) {
            throw new IllegalArgumentException("Identity record requires an externalId");
        }
        String refKey = r.aadhaarRefKey();
        if (r.aadhaarNumber() != null && !r.aadhaarNumber().isBlank()) {
            refKey = vault.referenceKey(r.aadhaarNumber());
        }
        return new IdentityRecord(r.systemName(), r.externalId(), r.fullName(), r.dob(),
            r.gender(), r.guardianName(), r.institutionCode(), r.district(),
            r.bankAccountLast4(), r.otrId(), refKey, null);
    }

    private Set<UUID> existingUsidsFor(List<IdentityRecord> records) {
        Set<UUID> usids = new LinkedHashSet<>();
        for (IdentityRecord r : records) {
            links.findFirstBySystemNameAndExternalIdOrderByLinkedAtDesc(
                    r.systemName(), r.externalId())
                .ifPresent(link -> usids.add(link.usid));
        }
        return usids;
    }

    private DuplicateFlag detectDuplicates(Set<UUID> existingUsids, IdentityRecord reference) {
        if (existingUsids.size() <= 1) return new DuplicateFlag(false, List.of());

        // The records already point at more than one USID. Add any scholar whose
        // Aadhaar reference key matches, so two people sharing one Aadhaar surface
        // as a duplicate rather than being merged.
        Set<UUID> all = new LinkedHashSet<>(existingUsids);
        String ref = reference.aadhaarRefKey();
        if (ref != null && !ref.isBlank()) {
            scholars.findByAadhaarRefKey(ref).forEach(s -> all.add(s.usid));
        }
        return new DuplicateFlag(true, new ArrayList<>(all));
    }

    private UUID mintUsid(IdentityRecord reference) {
        Scholar scholar = new Scholar();
        scholar.demographics.put("primarySystem", reference.systemName());
        scholar.demographics.put("fullName", reference.fullName());
        if (reference.dob() != null) scholar.demographics.put("dob", reference.dob());
        if (reference.district() != null) scholar.demographics.put("district", reference.district());
        if (reference.guardianName() != null) {
            scholar.demographics.put("guardianName", reference.guardianName());
        }
        // Store the reference key, never the Aadhaar number itself.
        if (reference.aadhaarRefKey() != null) {
            scholar.demographics.put("aadhaarRefKey", reference.aadhaarRefKey());
        }
        return scholars.save(scholar).usid;
    }

    private void persistLinks(UUID usid, List<IdentityRecord> records,
        IdentityRecord reference, List<LinkedSystemRecord> linked) {
        for (LinkedSystemRecord l : linked) {
            String method = l.resolutionMethod();
            if (links.findBySystemNameAndExternalId(l.systemName(), l.externalId()).isPresent()) {
                continue;
            }
            ScholarSystemLink link = new ScholarSystemLink();
            link.usid = usid;
            link.systemName = l.systemName();
            link.externalId = l.externalId();
            link.matchConfidence = l.confidence();
            link.resolutionMethod = method;
            links.save(link);
        }
        // The reference record is the anchor for a brand-new USID.
        Optional<ScholarSystemLink> anchor = links.findBySystemNameAndExternalId(
            reference.systemName(), reference.externalId());
        if (anchor.isEmpty()) {
            ScholarSystemLink link = new ScholarSystemLink();
            link.usid = usid;
            link.systemName = reference.systemName();
            link.externalId = reference.externalId();
            link.matchConfidence = 1.0;
            link.resolutionMethod = "deterministic";
            links.save(link);
        }
    }
}
