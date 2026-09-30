package in.adivritti.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.adivritti.core.admin.AdminController;
import in.adivritti.core.admin.CoverageGapService;
import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.claims.ClaimsController;
import in.adivritti.core.claims.ClaimsService;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Query parameter names must bind from the contract, not from the Java parameter name.
 *
 * <p>{@code core.yaml} documents {@code pvtg_filter}, {@code page_size} and
 * {@code include_expired}, while the controllers declared {@code pvtgFilter},
 * {@code pageSize} and {@code includeExpired}. Spring binds a query parameter by the
 * declared name, so a contract-legal request was not rejected — it was <em>silently
 * ignored</em>, applying no filter and the default page size. That is invisible without
 * a test that asserts what the service actually received, which is what this does.
 *
 * <p>Standalone MockMvc, so method security and bean validation are inert here on
 * purpose: the subject is binding.
 */
class ContractQueryParamBindingTest {

    private static final UUID USID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final CoverageGapService gaps = mock(CoverageGapService.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ClaimsService claims = mock(ClaimsService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new AdminController(gaps, applications, mock(SlaCalculator.class),
                    new ScholarAccessGuard(false)),
                new ClaimsController(claims, new ScholarAccessGuard(false)))
            .build();
        // The officer routes are role-gated; the subject of this class is parameter
        // binding, so the caller is simply an officer.
        runAsOfficer();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("pvtg_filter reaches the coverage-gap service")
    void pvtgFilterBinds() throws Exception {
        when(gaps.gap(any(), any(), any(), any(), any()))
            .thenReturn(new CoverageGapResponse(0, 0, 0, List.of()));

        mvc.perform(get("/v1/admin/coverage-gap").param("pvtg_filter", "true"))
            .andExpect(status().isOk());

        verify(gaps).gap(null, null, null, null, Boolean.TRUE);
    }

    @Test
    @DisplayName("page_size and page reach the pageable, 1-indexed as documented")
    void pageSizeBinds() throws Exception {
        when(applications.findAll(any(Pageable.class))).thenReturn(Page.<Application>empty());

        mvc.perform(get("/v1/admin/exceptions")
                .param("page", "2")
                .param("page_size", "5"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(applications).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize())
            .as("the contract's page_size must reach PageRequest")
            .isEqualTo(5);
        assertThat(captor.getValue().getPageNumber())
            .as("the contract is 1-indexed; PageRequest is 0-indexed")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("sort=created_at and sort=sla_deadline reach the sort")
    void sortBinds() throws Exception {
        when(applications.findAll(any(Pageable.class))).thenReturn(Page.<Application>empty());

        mvc.perform(get("/v1/admin/exceptions").param("sort", "created_at"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(applications).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt"))
            .as("created_at is contract-legal and must order by createdAt")
            .isNotNull();

        mvc.perform(get("/v1/admin/exceptions").param("sort", "sla_deadline"))
            .andExpect(status().isOk());

        verify(applications, org.mockito.Mockito.times(2)).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("slaDeadline"))
            .as("sla_deadline orders by the deadline so breached files sort first")
            .isNotNull();
    }

    @Test
    @DisplayName("include_expired reaches the claims service")
    void includeExpiredBinds() throws Exception {
        runAsSelf();
        when(claims.list(any(), anyBoolean())).thenReturn(null);

        mvc.perform(get("/v1/scholars/{usid}/claims", USID).param("include_expired", "true"))
            .andExpect(status().isOk());

        verify(claims).list(USID, true);
    }

    private static void runAsOfficer() {
        Jwt jwt = Jwt.withTokenValue("officer-token")
            .header("alg", "none")
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(300))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null,
                List.of(new SimpleGrantedAuthority("ROLE_OFFICER"))));
    }

    private static void runAsSelf() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(300))
            .claim(ScholarAccessGuard.USID_CLAIM, USID.toString())
            .build();
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
