package in.adivritti.core.scholars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.disbursement.repository.DisbursementRepository;
import in.adivritti.core.eligibility.EligibilityService;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse.SchemeVerdict;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The home screen shows the eligibility verdict for each applied scheme. The
 * verdict map was keyed by uppercased labels while applications carry lowercase
 * scheme keys, so every scheme rendered {@code unknown}.
 */
class DashboardEligibilityLookupTest {

    @Test
    @DisplayName("applied scheme shows its verdict instead of unknown")
    void verdictMatchesApplicationScheme() {
        UUID usid = UUID.randomUUID();
        Application app = new Application();
        app.id = UUID.randomUUID();
        app.usid = usid;
        app.scheme = "pre-matric";
        app.stage = "district_nodal";
        app.currentActor = "district";
        app.createdAt = ZonedDateTime.now().minusDays(3);

        ApplicationRepository applications = mock(ApplicationRepository.class);
        when(applications.findByUsidOrderByCreatedAtDesc(usid)).thenReturn(List.of(app));
        DisbursementRepository disbursements = mock(DisbursementRepository.class);
        when(disbursements.findByUsidOrderByScheme(usid)).thenReturn(List.of());
        DeficiencyRepository deficiencies = mock(DeficiencyRepository.class);
        when(deficiencies.findByUsidAndStatusOrderByCreatedAtDesc(usid, "open"))
            .thenReturn(List.of());
        EligibilityService eligibility = mock(EligibilityService.class);
        when(eligibility.evaluate(any(EligibilityRequest.class))).thenReturn(
            new EligibilityResponse(usid, "2026-27",
                List.of(new SchemeVerdict("pre-matric", "eligible", List.of(), List.of()))));

        DashboardService service = new DashboardService(applications, disbursements,
            deficiencies, eligibility, new SlaCalculator());
        DashboardResponse response = service.dashboard(usid);

        assertThat(response.schemes()).hasSize(1);
        assertThat(response.schemes().get(0).eligibility()).isEqualTo("eligible");
    }
}
