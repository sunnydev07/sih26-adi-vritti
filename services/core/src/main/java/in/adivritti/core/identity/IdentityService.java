package in.adivritti.core.identity;

import in.adivritti.core.common.util.AadhaarVault;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.DuplicateFlag;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.LinkedSystemRecord;
import in.adivritti.core.identity.entity.IdentityResolution;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.entity.ScholarSystemLink;
import in.adivritti.core.identity.repository.IdentityResolutionRepository;
import in.adivritti.core.identity.repository.ScholarRepository;
import in.adivritti.core.identity.repository.ScholarSystemLinkRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class IdentityService {

    private final UsidResolver resolver;
    private final ScholarRepository scholars;
    private final ScholarSystemLinkRepository links;
    private final IdentityResolutionRepository resolutions;
    private final AadhaarVault vault;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdentityService(UsidResolver resolver, ScholarRepository scholars,
        ScholarSystemLinkRepository links, IdentityResolutionRepository resolutions,
        AadhaarVault vault) {
        this.resolver = resolver;
        this.scholars = scholars;
        this.links = links;
        this.resolutions = resolutions;
        this.vault = vault;
    }

    /**
     * Resolve a set of cross-portal records into a single USID.
     *
     * <p>Idempotent: if the same (system, externalId) pair is already linked, the
     * existing USID is returned rather than a duplicate scholar being minted. Any
     * record that already belongs to a different USID raises a duplicate flag for
     * officer adjudication instead of silently merging two people.
     *
     * <p>Caller idempotency: when the request carries an {@code idempotencyKey}, a
     * repeat submission is answered from the stored {@code identity_resolution} row
     * — same USID, same report — instead of re-running the resolution. This is what
     * protects a genuinely new person from being minted twice when the first
     * response is lost on the wire. Concurrent same-key submissions serialize on
     * the key's unique constraint; the loser's computed response is discarded in
     * favour of the winner's stored one.
     */
    @Transactional
    public IdentityResolveResponse resolve(IdentityResolveRequest req) {
        List<IdentityRecord> records = req.records();
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("At least one identity record is required");
        }
        String idempotencyKey = normalizedKey(req.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<IdentityResolution> prior = resolutions.findByIdempotencyKey(idempotencyKey);
            if (prior.isPresent()) {
                return toResponse(prior.get());
            }
        }
        List<IdentityRecord> sanitized = records.stream().map(this::vaultize).toList();
        IdentityRecord reference = sanitized.get(0);

        // Compare each record against the reference. The reference itself is not
        // compared to itself — that always scored 1.0 and inflated confidence.
        // `linked_records` is a resolution report, not a link table: only the
        // deterministic and above-ceiling entries are persisted as ScholarSystemLink
        // rows (see persistLinks). Review-band entries get the human_adjudication
        // method the contract documents but the old code never produced.
        List<LinkedSystemRecord> linked = new ArrayList<>(sanitized.size());
        double minScore = 1.0;
        boolean human = false;
        boolean persistable = false;
        for (IdentityRecord r : sanitized) {
            if (r == reference) continue;
            boolean deterministic = resolver.deterministicMatch(reference, r);
            double score = deterministic ? 1.0 : resolver.score(reference, r);
            minScore = Math.min(minScore, score);
            String method = linkMethod(deterministic, score);
            persistable |= linkable(method, score);
            human |= "human_adjudication".equals(method);
            linked.add(new LinkedSystemRecord(r.systemName(), r.externalId(), score, method));
        }
        // A batch whose every comparison fell below the floor produced nothing
        // defensible: the entries are reported, only the reference anchor link is
        // written (plus the idempotency row when the caller supplied a key), and
        // the whole resolution still needs a human.
        if (!linked.isEmpty() && !persistable) {
            human = true;
        }
        // A single record is trivially a perfect match; nothing was compared.
        if (linked.isEmpty()) minScore = 1.0;

        Set<UUID> existingUsids = existingUsidsFor(sanitized);
        DuplicateFlag duplicates = detectDuplicates(existingUsids, reference);
        IdentityResolveResponse response;
        if (duplicates.isDuplicate()) {
            // Never auto-merge two identities. Hand the linked records to the
            // officer queue with the conflicting USIDs attached.
            response = new IdentityResolveResponse(
                duplicates.duplicateUsids().get(0), linked, minScore, true, duplicates);
        } else {
            UUID usid = anchorFor(existingUsids, reference);
            persistLinks(usid, sanitized, reference, linked);
            response = new IdentityResolveResponse(usid, linked, minScore, human, duplicates);
        }

        if (idempotencyKey != null) {
            response = storeResolution(idempotencyKey, response);
        }
        return response;
    }

    /** Blank and missing keys are the same: no idempotency requested. */
    private static String normalizedKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    /**
     * Freeze the computed response under the caller key. On a unique-violation
     * race the winner's stored row is authoritative: re-read and return it so
     * every same-key submission converges on one USID.
     */
    private IdentityResolveResponse storeResolution(String key, IdentityResolveResponse response) {
        try {
            resolutions.saveAndFlush(fromRecord(key, response));
            return response;
        } catch (DataIntegrityViolationException race) {
            return toResponse(resolutions.findByIdempotencyKey(key).orElseThrow(() -> race));
        }
    }

    private IdentityResolution fromRecord(String key, IdentityResolveResponse response) {
        try {
            IdentityResolution row = new IdentityResolution();
            row.idempotencyKey = key;
            row.usid = response.usid();
            row.linkedRecords = mapper.writeValueAsString(response.linkedRecords());
            row.overallConfidence = response.overallConfidence();
            row.needsHumanReview = response.needsHumanReview();
            row.duplicateUsids = mapper.writeValueAsString(response.duplicateFlag()
                .duplicateUsids().stream().map(UUID::toString).toList());
            return row;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise identity resolution", e);
        }
    }

    private IdentityResolveResponse toResponse(IdentityResolution row) {
        try {
            List<LinkedSystemRecord> linked =
                mapper.readValue(row.linkedRecords, new TypeReference<>() {});
            List<String> dupStrings =
                mapper.readValue(row.duplicateUsids, new TypeReference<>() {});
            List<UUID> dups = dupStrings.stream().map(UUID::fromString).toList();
            return new IdentityResolveResponse(row.usid, linked, row.overallConfidence,
                row.needsHumanReview, new DuplicateFlag(!dups.isEmpty(), dups));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException(
                "Stored identity resolution for key is corrupt", e);
        }
    }

    /**
     * The USID to attach this resolution to. Reuses the single already-linked USID, or —
     * before minting anything — the one scholar the Aadhaar reference key names. The
     * registry often knows the person under another system while the link table does
     * not, and minting for that case manufactured the duplicate beneficiaries the
     * duplicate check exists to catch. Callers only reach the mint for a genuinely new
     * person: no linked record and no known Aadhaar.
     */
    private UUID anchorFor(Set<UUID> existingUsids, IdentityRecord reference) {
        if (existingUsids.size() == 1) {
            return existingUsids.iterator().next();
        }
        String ref = reference.aadhaarRefKey();
        if (ref != null && !ref.isBlank()) {
            List<Scholar> known = scholars.findByAadhaarRefKey(ref);
            if (known.size() == 1) {
                return known.get(0).usid;
            }
        }
        return mintUsid(reference);
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
        // Union the link-derived USIDs with the Aadhaar-derived ones before counting.
        // The old code returned early on a single link-derived USID and never asked the
        // register, so a re-presented record kept minting fresh USIDs, and two people
        // sharing one Aadhaar only surfaced once links were already in conflict.
        Set<UUID> all = new LinkedHashSet<>(existingUsids);
        String ref = reference.aadhaarRefKey();
        if (ref != null && !ref.isBlank()) {
            scholars.findByAadhaarRefKey(ref).forEach(s -> all.add(s.usid));
        }
        if (all.size() <= 1) {
            return new DuplicateFlag(false, List.of());
        }
        // More than one distinct USID for one identity: never auto-merge. Hand the
        // records to the officer queue with every conflicting USID attached.
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
            if (!linkable(l.resolutionMethod(), l.confidence())) {
                continue;
            }
            if (links.findBySystemNameAndExternalId(l.systemName(), l.externalId()).isPresent()) {
                continue;
            }
            ScholarSystemLink link = new ScholarSystemLink();
            link.usid = usid;
            link.systemName = l.systemName();
            link.externalId = l.externalId();
            link.matchConfidence = l.confidence();
            link.resolutionMethod = l.resolutionMethod();
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

    private String linkMethod(boolean deterministic, double score) {
        if (deterministic) {
            return "deterministic";
        }
        if (resolver.needsHumanReview(score)) {
            return "human_adjudication";
        }
        return "probabilistic";
    }

    /**
     * Whether a resolution report entry may be written as a real link. Used both when
     * flagging review in {@link #resolve} and when writing in {@link #persistLinks} —
     * the two halves of the decision must never disagree about what is linkable.
     */
    static boolean linkable(String method, double confidence) {
        return "deterministic".equals(method) || confidence >= UsidResolver.REVIEW_CEILING;
    }
}
