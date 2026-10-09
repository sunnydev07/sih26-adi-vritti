package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.common.exception.ForbiddenException;
import in.adivritti.core.consent.ConsentGate;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * POST /v1/eligibility/evaluate decrypts the claims wallet, so it is a
 * personal-data read like any other: no live purpose-bound grant, no verdicts.
 * Consent cannot be bypassed by choosing this reader for the same data.
 */
class EligibilityConsentTest {

    private static final UUID USID = UUID.randomUUID();

    private static EligibilityController controller(EligibilityService service,
                                                    ConsentGate gate) {
        return new EligibilityController(service, mock(ScholarAccessGuard.class), gate);
    }

    @Test
    @DisplayName("evaluate requires claim_verification consent")
    void evaluateGated() {
        ConsentGate gate = mock(ConsentGate.class);
        EligibilityService service = mock(EligibilityService.class);
        when(service.evaluate(any())).thenReturn(
            new EligibilityResponse(USID, "2026-27", List.of()));

        controller(service, gate).evaluate(new EligibilityRequest(USID, "2026-27"));

        verify(gate).requireConsent(USID, "claim_verification", "eligibility");
    }

    @Test
    @DisplayName("evaluate fails when the gate denies")
    void evaluateDenialPropagates() {
        ConsentGate gate = mock(ConsentGate.class);
        doThrow(new ForbiddenException("CONSENT_REQUIRED", "No active consent"))
            .when(gate).requireConsent(any(), any(), any());

        assertThatThrownBy(() -> controller(mock(EligibilityService.class), gate)
                .evaluate(new EligibilityRequest(USID, "2026-27")))
            .isInstanceOf(ForbiddenException.class);
    }
}
