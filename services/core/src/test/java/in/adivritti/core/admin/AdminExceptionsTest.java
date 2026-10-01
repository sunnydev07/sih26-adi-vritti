package in.adivritti.core.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.StpScoreCalculator;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.security.ScholarAccessGuard;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The exception queue emits computed STP scores (not 0.0) and its default
 * {@code breach_risk} sort actually orders by risk — previously both the
 * score and the ordering silently degraded to createdAt order, so the
 * console's >= 85 batch action could never fire.
 */
class AdminExceptionsTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final DeficiencyRepository deficiencies = mock(DeficiencyRepository.class);

    private MockMvc mvc;
    private Application overdue;
    private Application healthy;

    private static Application app(String stage, int daysAgo) {
        Application a = new Application();
        a.id = UUID.randomUUID();
        a.usid = UUID.randomUUID();
        a.scheme = "PMSS";
        a.academicYear = "2026-27";
        a.stage = stage;
        a.currentActor = "district";
        a.slaDeadline = ZonedDateTime.now().plusDays(3);
        a.createdAt = ZonedDateTime.now().minusDays(daysAgo);
        return a;
    }

    @BeforeEach
    void setUp() {
        SlaCalculator sla = new SlaCalculator();
        AdminController controller = new AdminController(mock(CoverageGapService.class),
            applications, sla, new StpScoreCalculator(sla), deficiencies,
            new ScholarAccessGuard(false));
        // Standalone MockMvc uses a default camelCase mapper; the runtime wire is
        // snake_case (P0-2), so install the same naming here and assert the true
        // wire names below.
        ObjectMapper wire = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    wire))
            .build();

        // submitted SLA is 2 days: 10 days elapsed clamps risk at 1.0.
        overdue = app("submitted", 10);
        // ministry SLA is 15 days: 1 day elapsed is ~0.07 risk.
        healthy = app("ministry", 1);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("officer", null,
            List.of(new SimpleGrantedAuthority("ROLE_OFFICER"))));
        SecurityContextHolder.setContext(context);

        when(deficiencies.findByApplicationIdOrderByCreatedAtAsc(any(UUID.class)))
            .thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("items carry computed STP scores, not 0.0")
    void itemsCarryComputedScores() throws Exception {
        when(applications.findAll()).thenReturn(List.of(overdue, healthy));

        mvc.perform(get("/v1/admin/exceptions"))
            .andExpect(status().isOk())
            // overdue first: risk 1.0 outranks 0.07 even though it is older.
            .andExpect(jsonPath("$.items[0].stage").value("submitted"))
            .andExpect(jsonPath("$.items[0].stp_score").value(15.0))
            .andExpect(jsonPath("$.items[0].breach_risk").value(1.0))
            .andExpect(jsonPath("$.items[1].stage").value("ministry"))
            .andExpect(jsonPath("$.items[1].stp_score").value(87.2))
            .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @DisplayName("default sort is risk-descending, not newest-first")
    void defaultSortIsRiskDescending() throws Exception {
        when(applications.findAll()).thenReturn(List.of(healthy, overdue));

        mvc.perform(get("/v1/admin/exceptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].stage").value("submitted"))
            .andExpect(jsonPath("$.items[1].stage").value("ministry"));
    }

    @Test
    @DisplayName("created_at sort stays newest-first")
    void createdAtSortIsNewestFirst() throws Exception {
        when(applications.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(healthy, overdue)));

        mvc.perform(get("/v1/admin/exceptions").param("sort", "created_at"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].stage").value("ministry"))
            .andExpect(jsonPath("$.items[1].stage").value("submitted"));
    }

    @Test
    @DisplayName("risk sort pages the ordered list and reports the full total")
    void riskSortPaginates() throws Exception {
        when(applications.findAll()).thenReturn(List.of(overdue, healthy));

        mvc.perform(get("/v1/admin/exceptions").param("page", "2").param("page_size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].stage").value("ministry"))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(2));
    }
}
