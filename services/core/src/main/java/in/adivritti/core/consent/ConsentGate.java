package in.adivritti.core.consent;

import in.adivritti.core.common.exception.ForbiddenException;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.entity.ConsentArtefact;
import in.adivritti.core.consent.repository.ConsentArtefactRepository;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.repository.ScholarRepository;
import in.adivritti.core.security.ScholarAccessGuard;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The single choke point for DPDP consent on personal-data reads. Every read
 * path that returns somebody's data (claims wallet, dashboard, disbursements,
 * JAGO tools) calls {@link #requireConsent} first: without a live,
 * purpose-bound grant the read is denied with 403, and both grants and
 * denials land in {@code access_audit} — so "who looked at my data" shows
 * allowed accesses and blocked attempts alike.
 *
 * <p>Minor rule: a scholar whose demographics carry a {@code dob} under 18
 * years is readable only under a consent granted by the guardian on file
 * ({@code guardianName}). The match is name-based until guardian USIDs are
 * issued and linked — see {@code Scholar.guardianUsid}, which nothing
 * populates yet. A minor with no guardian name on file is unreadable: there
 * is nobody on record who could have granted verifiable parental consent.
 *
 * <p>A scholar with <em>no</em> dob takes the adult path: dob is populated by
 * identity resolution, so absence means the scholar never went through it. A
 * scholar whose dob is present but unreadable (truncated, a different date
 * format, a placeholder) does NOT: an unreadable age cannot be assumed to be
 * an adult one, and the previous code did exactly that — it caught the parse
 * failure and returned {@code false}, so a minor with a mangled dob skipped
 * the guardian check entirely. It now takes the guardian path, which denies
 * unless a guardian actually granted.
 *
 * <p>Age is measured against an injected {@link Clock}. {@code LocalDate.now()}
 * inside a privacy decision makes the answer untestable (a test written for a
 * 2008 date of birth passes for 18 years and then fails) and makes the audit
 * trail depend on which node served the request.
 */
@Service
public class ConsentGate {

    private static final int MINORITY_AGE_YEARS = 18;

    private final ConsentArtefactRepository consents;
    private final ScholarRepository scholars;
    private final ConsentService consentService;
    private final ScholarAccessGuard access;
    private final Clock clock;

    public ConsentGate(ConsentArtefactRepository consents, ScholarRepository scholars,
        ConsentService consentService, ScholarAccessGuard access, Clock clock) {
        this.consents = consents;
        this.scholars = scholars;
        this.consentService = consentService;
        this.access = access;
        this.clock = clock;
    }

    /** Entry point for read paths: accessor is the caller's audit identity. */
    public void requireConsent(UUID usid, String purpose, String field) {
        requireConsent(usid, purpose, access.accessorName(), field);
    }

    void requireConsent(UUID usid, String purpose, String accessor, String field) {
        if (usid == null) throw new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found");
        Scholar scholar = scholars.findById(usid)
            .orElseThrow(() -> new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found"));
        List<ConsentArtefact> live = consents.findActiveByUsidAndPurpose(usid, purpose);
        if (isMinor(scholar, clock)) {
            requireGuardianGrant(scholar, live, usid, accessor, field, purpose);
            return;
        }
        if (live.isEmpty()) {
            deny(usid, accessor, field, purpose);
        }
        consentService.recordAccess(usid, accessor, field, live.get(0).id);
    }

    private void requireGuardianGrant(Scholar scholar, List<ConsentArtefact> live, UUID usid,
        String accessor, String field, String purpose) {
        String guardian = guardianName(scholar);
        boolean granted = guardian != null && live.stream()
            .anyMatch(c -> c.grantedBy != null && c.grantedBy.trim().equalsIgnoreCase(guardian));
        if (!granted) {
            deny(usid, accessor, field, purpose);
        }
        UUID consentId = live.stream()
            .filter(c -> c.grantedBy != null && c.grantedBy.trim().equalsIgnoreCase(guardian))
            .map(c -> c.id)
            .findFirst()
            .orElse(null);
        consentService.recordAccess(usid, accessor, field, consentId);
    }

    private void deny(UUID usid, String accessor, String field, String purpose) {
        // The denial itself is audited with a null consent id: the "who looked
        // at my data" trail must show blocked attempts, not just allowed ones.
        consentService.recordAccess(usid, accessor, field, null);
        throw new ForbiddenException("CONSENT_REQUIRED",
            "No active consent for purpose '" + purpose + "'");
    }

    static boolean isMinor(Scholar scholar) {
        return isMinor(scholar, Clock.systemUTC());
    }

    /**
     * Overload for tests: pin "today" so a 2008 date of birth is a minor in
     * 2026 and the same assertion is still true in 2030.
     */
    static boolean isMinor(Scholar scholar, Clock clock) {
        Object dob = scholar.demographics == null ? null : scholar.demographics.get("dob");
        // Absent dob means identity resolution never ran for this scholar, which
        // is not the same as "known to be an adult". It takes the adult path
        // because there is no age claim to honour.
        if (!(dob instanceof String s) || s.isBlank()) return false;
        try {
            LocalDate born = LocalDate.parse(s.trim());
            return Period.between(born, LocalDate.now(clock)).getYears() < MINORITY_AGE_YEARS;
        } catch (RuntimeException e) {
            // Fail closed. A dob we cannot read is a dob we cannot rule out as a
            // minor's, and the guardian path denies by default — the strictest
            // outcome available. Swallowing the exception and returning false
            // here is what let a minor's record be read on their own signature.
            return true;
        }
    }

    private static String guardianName(Scholar scholar) {
        if (scholar.demographics == null) return null;
        Object name = scholar.demographics.get("guardianName");
        if (!(name instanceof String s) || s.isBlank()) return null;
        return s.trim();
    }
}
