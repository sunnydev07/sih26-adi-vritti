package in.adivritti.core.consent;

import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.dto.ConsentDtos.AuditEventDto;
import in.adivritti.core.consent.dto.ConsentDtos.AuditPage;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentCreateRequest;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentDto;
import in.adivritti.core.consent.entity.AccessAudit;
import in.adivritti.core.consent.entity.ConsentArtefact;
import in.adivritti.core.consent.repository.AccessAuditRepository;
import in.adivritti.core.consent.repository.ConsentArtefactRepository;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

    /**
     * Consent is purpose-bound under DPDP: a grant for one purpose never implies
     * another. Unknown purposes are rejected rather than stored verbatim.
     */
    private static final Set<String> PURPOSES = Set.of(
        "application_submission",
        "claim_verification",
        "identity_resolution",
        "disbursement_tracking",
        "coverage_outreach",
        "cross_ministry_gap_analysis");

    private static final int MAX_SCOPE_ENTRIES = 50;

    private final ConsentArtefactRepository consents;
    private final AccessAuditRepository audits;

    public ConsentService(ConsentArtefactRepository consents, AccessAuditRepository audits) {
        this.consents = consents;
        this.audits = audits;
    }

    /** Purpose-bound grant; guardian USID for Pre-Matric minors (verifiable parental consent). */
    @Transactional
    public ConsentDto grant(ConsentCreateRequest req) {
        if (req == null) throw new IllegalArgumentException("Consent request is required");
        if (req.usid() == null) throw new IllegalArgumentException("usid is required");
        if (req.purpose() == null || !PURPOSES.contains(req.purpose())) {
            throw new IllegalArgumentException("purpose must be one of " + PURPOSES);
        }
        if (req.grantedBy() == null || req.grantedBy().isBlank()) {
            throw new IllegalArgumentException("grantedBy is required");
        }
        if (req.expiresAt() != null && !req.expiresAt().isAfter(ZonedDateTime.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        List<String> scope = req.scope() == null ? List.of() : req.scope();
        if (scope.size() > MAX_SCOPE_ENTRIES) {
            throw new IllegalArgumentException(
                "consent scope may contain at most " + MAX_SCOPE_ENTRIES + " entries");
        }

        ConsentArtefact c = new ConsentArtefact();
        c.usid = req.usid();
        c.purpose = req.purpose();
        c.scope = new ArrayList<>(scope);
        c.grantedBy = req.grantedBy();
        c.expiresAt = req.expiresAt();
        return toDto(consents.save(c));
    }

    /**
     * The owner of a consent artefact, so a controller can authorise before revoking.
     * Revocation is a write against somebody's DPDP consent; an id alone must not
     * authorise it. Same 404 either way, so it cannot be used to probe consent ids.
     */
    @Transactional(readOnly = true)
    public UUID usidOf(UUID id) {
        if (id == null) throw new NotFoundException("CONSENT_NOT_FOUND", "Consent not found");
        return consents.findById(id)
            .map(c -> c.usid)
            .orElseThrow(() -> new NotFoundException("CONSENT_NOT_FOUND", "Consent not found"));
    }

    @Transactional
    public ConsentDto revoke(UUID id) {
        if (id == null) throw new NotFoundException("CONSENT_NOT_FOUND", "Consent not found");
        ConsentArtefact c = consents.findById(id)
            .orElseThrow(() -> new NotFoundException("CONSENT_NOT_FOUND", "Consent not found"));
        if (c.revokedAt == null) {
            c.revokedAt = ZonedDateTime.now();
            consents.save(c);
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public AuditPage audit(UUID usid, int page, int pageSize) {
        if (usid == null) throw new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found");
        Page<AccessAudit> result = audits.findByUsidOrderByAccessedAtDesc(usid,
            PageRequest.of(page, pageSize));
        List<AuditEventDto> items = result.getContent().stream()
            .map(a -> new AuditEventDto(a.id, a.accessor, a.fieldAccessed, a.consentId,
                a.accessedAt))
            .toList();
        return new AuditPage(items, result.getTotalElements(), page, pageSize);
    }

    /**
     * Append-only audit write for every field access under a consent. Runs in its own
     * transaction so an audit row survives a rollback of the surrounding read.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccess(UUID usid, String accessor, String field, UUID consentId) {
        if (usid == null || accessor == null || field == null) {
            throw new IllegalArgumentException("usid, accessor and field are required");
        }
        AccessAudit a = new AccessAudit();
        a.usid = usid;
        a.accessor = accessor;
        a.fieldAccessed = field;
        a.consentId = consentId;
        audits.save(a);
    }

    /** True when a live consent covers this purpose. Callers must consult this before
     *  reading personal fields, and call {@link #recordAccess} when they do. */
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(UUID usid, String purpose) {
        return usid != null
            && !consents.findActiveByUsidAndPurpose(usid, purpose).isEmpty();
    }

    private ConsentDto toDto(ConsentArtefact c) {
        return new ConsentDto(c.id, c.usid, c.purpose,
            c.scope == null ? List.of() : List.copyOf(c.scope),
            c.grantedBy, c.grantedAt, c.expiresAt, c.revokedAt);
    }
}
