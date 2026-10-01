package in.adivritti.core.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import in.adivritti.core.common.exception.ForbiddenException;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.entity.ConsentArtefact;
import in.adivritti.core.consent.repository.ConsentArtefactRepository;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.repository.ScholarRepository;
import in.adivritti.core.security.ScholarAccessGuard;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The DPDP gate: reads need a live purpose-bound grant, denials are audited,
 * and minors are readable only under a guardian grant. Consent is about the
 * data subject's grant, not the caller — the caller identity only labels the
 * audit row.
 */
class ConsentGateTest {

    private static final UUID USID = UUID.randomUUID();
    private static final String PURPOSE = "claim_verification";

    private ConsentArtefactRepository consents;
    private ScholarRepository scholars;
    private ConsentService consentService;
    private ConsentGate gate;

    private static Scholar adult() {
        Scholar s = new Scholar();
        s.usid = USID;
        s.demographics = new LinkedHashMap<>(Map.of("name", "Adult Scholar"));
        return s;
    }

    private static Scholar minor() {
        Scholar s = adult();
        s.demographics = new LinkedHashMap<>(Map.of(
            "name", "Minor Scholar",
            "dob", LocalDate.now().minusYears(10).toString(),
            "guardianName", "Meena Devi"));
        return s;
    }

    private static ConsentArtefact grant(String purpose, String grantedBy) {
        ConsentArtefact c = new ConsentArtefact();
        c.id = UUID.randomUUID();
        c.usid = USID;
        c.purpose = purpose;
        c.grantedBy = grantedBy;
        return c;
    }

    @BeforeEach
    void setUp() {
        consents = mock(ConsentArtefactRepository.class);
        scholars = mock(ScholarRepository.class);
        consentService = mock(ConsentService.class);
        gate = new ConsentGate(consents, scholars, consentService,
            mock(ScholarAccessGuard.class));
    }

    @Test
    @DisplayName("adult with a live grant reads, and the access is audited under the consent")
    void adultWithGrantPasses() {
        ConsentArtefact c = grant(PURPOSE, "Adult Scholar");
        when(scholars.findById(USID)).thenReturn(Optional.of(adult()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of(c));

        gate.requireConsent(USID, PURPOSE, "officer-x", "claims");

        verify(consentService).recordAccess(USID, "officer-x", "claims", c.id);
    }

    @Test
    @DisplayName("adult without a grant gets 403, and the denial is audited with no consent id")
    void adultWithoutGrantDenied() {
        when(scholars.findById(USID)).thenReturn(Optional.of(adult()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of());

        assertThatThrownBy(() -> gate.requireConsent(USID, PURPOSE, "officer-x", "claims"))
            .isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("CONSENT_REQUIRED"));
        verify(consentService).recordAccess(eq(USID), eq("officer-x"), eq("claims"), isNull());
    }

    @Test
    @DisplayName("a grant for another purpose does not authorise this read")
    void purposeBound() {
        when(scholars.findById(USID)).thenReturn(Optional.of(adult()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of());

        assertThatThrownBy(() -> gate.requireConsent(USID, PURPOSE, "self", "claims"))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("minor with a guardian grant reads under the guardian consent")
    void minorWithGuardianGrantPasses() {
        ConsentArtefact guardian = grant(PURPOSE, "Meena Devi");
        ConsentArtefact self = grant(PURPOSE, "Minor Scholar");
        when(scholars.findById(USID)).thenReturn(Optional.of(minor()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE))
            .thenReturn(List.of(self, guardian));

        gate.requireConsent(USID, PURPOSE, "self", "dashboard");

        verify(consentService).recordAccess(USID, "self", "dashboard", guardian.id);
    }

    @Test
    @DisplayName("minor with only a self grant is denied: a child cannot consent for themselves")
    void minorWithSelfGrantDenied() {
        when(scholars.findById(USID)).thenReturn(Optional.of(minor()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE))
            .thenReturn(List.of(grant(PURPOSE, "Minor Scholar")));

        assertThatThrownBy(() -> gate.requireConsent(USID, PURPOSE, "self", "dashboard"))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("minor with no guardian on file is unreadable")
    void minorWithoutGuardianOnFileDenied() {
        Scholar s = minor();
        s.demographics.remove("guardianName");
        when(scholars.findById(USID)).thenReturn(Optional.of(s));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE))
            .thenReturn(List.of(grant(PURPOSE, "Someone")));

        assertThatThrownBy(() -> gate.requireConsent(USID, PURPOSE, "officer-x", "claims"))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("unknown dob takes the adult path")
    void unknownDobIsAdultPath() {
        ConsentArtefact c = grant(PURPOSE, "Adult Scholar");
        when(scholars.findById(USID)).thenReturn(Optional.of(adult()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of(c));

        gate.requireConsent(USID, PURPOSE, "self", "claims");

        verify(consentService).recordAccess(USID, "self", "claims", c.id);
    }

    @Test
    @DisplayName("unparseable dob takes the adult path rather than denying")
    void unparseableDobIsAdultPath() {
        Scholar s = adult();
        s.demographics.put("dob", "not-a-date");
        ConsentArtefact c = grant(PURPOSE, "Adult Scholar");
        when(scholars.findById(USID)).thenReturn(Optional.of(s));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of(c));

        gate.requireConsent(USID, PURPOSE, "self", "claims");

        verify(consentService).recordAccess(USID, "self", "claims", c.id);
    }

    @Test
    @DisplayName("unknown scholar is 404 with no audit row")
    void unknownScholarNotFound() {
        when(scholars.findById(USID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate.requireConsent(USID, PURPOSE, "self", "claims"))
            .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(consents, consentService);
    }

    @Test
    @DisplayName("null usid is 404")
    void nullUsidNotFound() {
        assertThatThrownBy(() -> gate.requireConsent(null, PURPOSE, "self", "claims"))
            .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(consents, consentService, scholars);
    }

    @Test
    @DisplayName("three-arg entry labels the audit row with the caller's guard identity")
    void accessorComesFromGuard() {
        ScholarAccessGuard guard = mock(ScholarAccessGuard.class);
        when(guard.accessorName()).thenReturn("officer-neha");
        ConsentGate guarded = new ConsentGate(consents, scholars, consentService, guard);
        ConsentArtefact c = grant(PURPOSE, "Adult Scholar");
        when(scholars.findById(USID)).thenReturn(Optional.of(adult()));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of(c));

        guarded.requireConsent(USID, PURPOSE, "claims");

        verify(consentService).recordAccess(USID, "officer-neha", "claims", c.id);
    }

    @Test
    @DisplayName("guardian match ignores case and surrounding whitespace")
    void guardianMatchIsLenient() {
        when(scholars.findById(USID)).thenReturn(Optional.of(minor()));
        ConsentArtefact c = grant(PURPOSE, "  meena devi ");
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of(c));

        gate.requireConsent(USID, PURPOSE, "self", "claims");

        verify(consentService).recordAccess(USID, "self", "claims", c.id);
    }

    @Test
    @DisplayName("exactly-18 dob is the adult path; boundary is strictly under 18")
    void eighteenIsAdult() {
        Scholar s = adult();
        s.demographics.put("dob", LocalDate.now().minusYears(18).toString());
        ConsentArtefact c = grant(PURPOSE, "Adult Scholar");
        when(scholars.findById(USID)).thenReturn(Optional.of(s));
        when(consents.findActiveByUsidAndPurpose(USID, PURPOSE)).thenReturn(List.of(c));

        gate.requireConsent(USID, PURPOSE, "self", "claims");

        verify(consentService).recordAccess(USID, "self", "claims", c.id);
        assertThat(ConsentGate.isMinor(s)).isFalse();
    }
}
