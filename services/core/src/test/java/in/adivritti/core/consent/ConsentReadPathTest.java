package in.adivritti.core.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.claims.ClaimsService;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.exception.ForbiddenException;
import in.adivritti.core.disbursement.DisbursementService;
import in.adivritti.core.disbursement.FailureDecoder;
import in.adivritti.core.disbursement.repository.DisbursementRepository;
import in.adivritti.core.eligibility.EligibilityService;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.jago.JagoToolRouter;
import in.adivritti.core.scholars.DashboardService;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

/**
 * Every personal-data read passes the consent gate with its own purpose, and
 * a gate denial fails the read — consent cannot be bypassed by choosing a
 * different reader for the same data.
 */
class ConsentReadPathTest {

    private static final UUID USID = UUID.randomUUID();

    private static ConsentGate deniedGate() {
        ConsentGate gate = mock(ConsentGate.class);
        doThrow(new ForbiddenException("CONSENT_REQUIRED", "No active consent"))
            .when(gate).requireConsent(any(), any(), any());
        return gate;
    }

    @Test
    @DisplayName("claims read requires claim_verification consent")
    void claimsGated() {
        ConsentGate gate = mock(ConsentGate.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        when(claims.findLiveByUsid(USID)).thenReturn(List.of());
        ClaimsService service = new ClaimsService(claims, gate);

        service.list(USID, false);

        verify(gate).requireConsent(USID, "claim_verification", "claims");
    }

    @Test
    @DisplayName("claims read fails when the gate denies")
    void claimsDenialPropagates() {
        ClaimsService service =
            new ClaimsService(mock(ClaimRepository.class), deniedGate());

        assertThatThrownBy(() -> service.list(USID, false))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("dashboard read requires application_submission consent")
    void dashboardGated() {
        ConsentGate gate = mock(ConsentGate.class);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        when(applications.findByUsidOrderByCreatedAtDesc(USID)).thenReturn(List.of());
        DisbursementRepository disbursements = mock(DisbursementRepository.class);
        when(disbursements.findByUsidOrderByScheme(USID)).thenReturn(List.of());
        DeficiencyRepository deficiencies = mock(DeficiencyRepository.class);
        when(deficiencies.findByUsidAndStatusOrderByCreatedAtDesc(USID, "open"))
            .thenReturn(List.of());
        DashboardService service = new DashboardService(applications, disbursements,
            deficiencies, mock(EligibilityService.class),
            new SlaCalculator(), gate);

        service.dashboard(USID);

        verify(gate).requireConsent(USID, "application_submission", "dashboard");
    }

    @Test
    @DisplayName("dashboard read fails when the gate denies")
    void dashboardDenialPropagates() {
        DashboardService service = new DashboardService(mock(ApplicationRepository.class),
            mock(DisbursementRepository.class), mock(DeficiencyRepository.class),
            mock(EligibilityService.class), new SlaCalculator(), deniedGate());

        assertThatThrownBy(() -> service.dashboard(USID))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("disbursement read requires disbursement_tracking consent")
    void disbursementsGated() {
        ConsentGate gate = mock(ConsentGate.class);
        DisbursementRepository disbursements = mock(DisbursementRepository.class);
        when(disbursements.findByUsidOrderByScheme(USID)).thenReturn(List.of());
        DisbursementService service = new DisbursementService(mock(FailureDecoder.class),
            disbursements, gate);

        service.list(USID);

        verify(gate).requireConsent(USID, "disbursement_tracking", "disbursements");
    }

    @Test
    @DisplayName("disbursement read fails when the gate denies")
    void disbursementsDenialPropagates() {
        DisbursementService service = new DisbursementService(mock(FailureDecoder.class),
            mock(DisbursementRepository.class), deniedGate());

        assertThatThrownBy(() -> service.list(USID))
            .isInstanceOf(ForbiddenException.class);
    }

    @ParameterizedTest(name = "{0} reads under {1}")
    @CsvSource({
        "get_my_applications, application_submission",
        "check_eligibility, claim_verification",
        "explain_deficiency, application_submission",
        "why_is_payment_pending, disbursement_tracking",
        "next_action, application_submission",
        "list_required_documents, application_submission",
        "get_disbursement_history, disbursement_tracking",
    })
    void jagoToolsGatedByPurpose(String tool, String purpose) {
        ConsentGate gate = mock(ConsentGate.class);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        when(applications.findByUsidOrderByCreatedAtDesc(USID)).thenReturn(List.of());
        DisbursementRepository disbursements = mock(DisbursementRepository.class);
        when(disbursements.findByUsidOrderByScheme(USID)).thenReturn(List.of());
        DeficiencyRepository deficiencies = mock(DeficiencyRepository.class);
        when(deficiencies.findByUsidAndStatusOrderByCreatedAtDesc(USID, "open"))
            .thenReturn(List.of());
        EligibilityService eligibility = mock(EligibilityService.class);
        when(eligibility.evaluate(any())).thenReturn(
            new EligibilityResponse(USID, "2026-27", List.of()));
        JagoToolRouter router = new JagoToolRouter(applications, disbursements, deficiencies,
            eligibility, gate);

        JagoToolRouter.ToolResult result = router.invoke(tool, USID, Map.of());

        ArgumentCaptor<String> purposes = ArgumentCaptor.forClass(String.class);
        verify(gate).requireConsent(eq(USID), purposes.capture(), eq("jago:" + tool));
        assertThat(purposes.getValue()).isEqualTo(purpose);
        assertThat(result.tool()).isEqualTo(tool);
    }

    @Test
    @DisplayName("JAGO tool read fails when the gate denies")
    void jagoDenialPropagates() {
        JagoToolRouter router = new JagoToolRouter(mock(ApplicationRepository.class),
            mock(DisbursementRepository.class), mock(DeficiencyRepository.class),
            mock(EligibilityService.class), deniedGate());

        assertThatThrownBy(() -> router.invoke("get_my_applications", USID, Map.of()))
            .isInstanceOf(ForbiddenException.class);
    }
}
