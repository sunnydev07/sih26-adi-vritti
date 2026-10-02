package in.adivritti.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.adivritti.core.admin.AdminController;
import in.adivritti.core.admin.CoverageGapService;
import in.adivritti.core.application.ApplicationController;
import in.adivritti.core.application.ApplicationService;
import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.StpScoreCalculator;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.claims.ClaimsController;
import in.adivritti.core.claims.ClaimsService;
import in.adivritti.core.common.exception.GlobalExceptionHandler;
import in.adivritti.core.config.SecurityConfig;
import in.adivritti.core.consent.ConsentController;
import in.adivritti.core.consent.ConsentService;
import in.adivritti.core.disbursement.DisbursementController;
import in.adivritti.core.disbursement.DisbursementService;
import in.adivritti.core.eligibility.EligibilityController;
import in.adivritti.core.eligibility.EligibilityService;
import in.adivritti.core.identity.IdentityController;
import in.adivritti.core.identity.IdentityService;
import in.adivritti.core.jago.JagoController;
import in.adivritti.core.jago.JagoToolRouter;
import in.adivritti.core.scholars.DashboardService;
import in.adivritti.core.scholars.ScholarsController;
import in.adivritti.core.verification.VerificationController;
import in.adivritti.core.verification.VerificationOrchestrator;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Every path in the contract, checked for authorization, in one table (task 1.3).
 *
 * <p>{@link EndpointAuthorisationTest} covers the four routes that were found
 * unguarded and fixed. This one exists because "we checked the routes once" is not
 * a property that survives: it was true for all thirteen when this was written, and
 * nothing but four hand-written tests was holding it there. A new handler whose
 * author forgets {@code access.check(usid)} would ship with a green build and an
 * IDOR over the claims wallet.
 *
 * <p>Two things make it hold as the API grows:
 * <ul>
 *   <li>the table is the audit — every contract path appears in it, checked against
 *       three callers: a scholar who does not own the record, the owner, and an
 *       officer;
 *   <li>{@link #everyContractPathIsInTheTable()} reads {@code docs/openapi/core.yaml}
 *       and fails if the contract has grown a path the table does not cover. Adding
 *       an endpoint without deciding who may call it is a build failure, not a
 *       code-review question somebody has to remember to ask.
 * </ul>
 *
 * <p>Real {@link SecurityConfig} chain, real {@link ScholarAccessGuard}, real
 * controllers and {@link GlobalExceptionHandler}; only the services are mocked, so
 * this is an authorization test rather than a wiring test. Runs with
 * {@code allow-insecure-dev=false} — the profile where the gates have to bite.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, ContractRouteAuthorisationTest.StubApi.class})
@WebAppConfiguration
@TestPropertySource(properties = {
    "app.security.allow-insecure-dev=false",
    "app.security.jwt.hmac-secret=" + ContractRouteAuthorisationTest.DEV_HMAC_SECRET,
})
class ContractRouteAuthorisationTest {

    static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID APPLICATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONSENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String IDENTITY_RESOLVE_BODY = "{\"records\":[{\"system_name\":\"NSP\","
        + "\"external_id\":\"NSP-1\",\"full_name\":\"Sunita Meena\"}]}";

    /** One contract path and who may reach it. */
    record Route(String path, String method,
        int forAScholarWhoDoesNotOwnIt, int forTheOwner, int forAnOfficer) {

        /** True when the owner gets through at all (200, or 201 for a create). */
        boolean ownerAllowed() {
            return forTheOwner < 300;
        }

        /** The concrete URL for this caller's request, with the owner's id filled in. */
        String url() {
            return path
                .replace("{usid}", OWNER.toString())
                .replace("{id}", path.startsWith("/v1/applications") ? APPLICATION_ID.toString()
                    : CONSENT_ID.toString())
                .replace("{name}", "get_my_applications");
        }

        /** The body the contract requires, carrying the owner's USID. */
        String body() {
            return switch (path) {
                case "/v1/verify" ->
                    "{\"usid\":\"" + OWNER + "\",\"claim_type\":\"income\"}";
                case "/v1/eligibility/evaluate" -> "{\"usid\":\"" + OWNER + "\"}";
                case "/v1/jago/tool/{name}" -> "{\"usid\":\"" + OWNER + "\",\"parameters\":{}}";
                case "/v1/consent" -> "{\"usid\":\"" + OWNER
                    + "\",\"purpose\":\"claim_verification\",\"granted_by\":\"Sunita Meena\"}";
                case "/v1/identity/resolve" -> IDENTITY_RESOLVE_BODY;
                default -> null;
            };
        }
    }

    /**
     * The audit, as data. Every path in docs/openapi/core.yaml appears here.
     *
     * <p>Scholar-scoped paths follow one rule (own the USID, or hold a role), which
     * is why {@code scholarScoped} is not a per-row decision. Officer-scoped paths
     * ignore ownership entirely: a scholar token cannot read the exception queue or
     * the coverage map even for their own USID, because those are cross-scholar
     * aggregates.
     */
    static List<Route> routes() {
        return List.of(
            // --- officer-scoped: role only, ownership is irrelevant -------------
            new Route("/v1/identity/resolve", "POST", 403, 403, 200),
            new Route("/v1/admin/coverage-gap", "GET", 403, 403, 200),
            new Route("/v1/admin/exceptions", "GET", 403, 403, 200),

            // --- scholar-scoped: the owner or an officer -------------------------
            new Route("/v1/scholars/{usid}/dashboard", "GET", 403, 200, 200),
            new Route("/v1/scholars/{usid}/claims", "GET", 403, 200, 200),
            new Route("/v1/scholars/{usid}/audit", "GET", 403, 200, 200),
            new Route("/v1/disbursements/{usid}", "GET", 403, 200, 200),
            new Route("/v1/applications/{id}/timeline", "GET", 403, 200, 200),
            new Route("/v1/verify", "POST", 403, 200, 200),
            new Route("/v1/eligibility/evaluate", "POST", 403, 200, 200),
            new Route("/v1/jago/tool/{name}", "POST", 403, 200, 200),
            new Route("/v1/consent", "POST", 403, 201, 201),
            new Route("/v1/consent/{id}", "DELETE", 403, 200, 200));
    }

    static {
        // Cheap tripwire: the contract has 13 paths, so a table that loses rows is
        // a signal to look, not a failure on its own (everyContractPathIsInTheTable
        // is the real gate).
        if (routes().size() < 13) {
            throw new AssertionError("the audit table shrank below the contract's path count");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({
        VerificationController.class,
        ApplicationController.class,
        ConsentController.class,
        IdentityController.class,
        ClaimsController.class,
        ScholarsController.class,
        EligibilityController.class,
        DisbursementController.class,
        JagoController.class,
        AdminController.class,
        GlobalExceptionHandler.class,
    })
    static class StubApi {

        @Bean
        ScholarAccessGuard scholarAccessGuard() {
            return new ScholarAccessGuard(false);
        }

        @Bean
        VerificationOrchestrator verificationOrchestrator() {
            return mock(VerificationOrchestrator.class);
        }

        @Bean
        ApplicationService applicationService() {
            return mock(ApplicationService.class);
        }

        @Bean
        ConsentService consentService() {
            return mock(ConsentService.class);
        }

        @Bean
        IdentityService identityService() {
            return mock(IdentityService.class);
        }

        @Bean
        ClaimsService claimsService() {
            return mock(ClaimsService.class);
        }

        @Bean
        DashboardService dashboardService() {
            return mock(DashboardService.class);
        }

        @Bean
        EligibilityService eligibilityService() {
            return mock(EligibilityService.class);
        }

        @Bean
        DisbursementService disbursementService() {
            return mock(DisbursementService.class);
        }

        @Bean
        JagoToolRouter jagoToolRouter() {
            return mock(JagoToolRouter.class);
        }

        @Bean
        CoverageGapService coverageGapService() {
            return mock(CoverageGapService.class);
        }

        @Bean
        ApplicationRepository applicationRepository() {
            return mock(ApplicationRepository.class);
        }

        @Bean
        DeficiencyRepository deficiencyRepository() {
            return mock(DeficiencyRepository.class);
        }

        @Bean
        SlaCalculator slaCalculator() {
            return new SlaCalculator();
        }

        @Bean
        StpScoreCalculator stpScoreCalculator(SlaCalculator sla) {
            return new StpScoreCalculator(sla);
        }

        /** See EndpointAuthorisationTest: this minimal context has no Boot Jackson. */
        @Bean
        WebMvcConfigurer contractJson() {
            return new WebMvcConfigurer() {
                @Override
                public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
                    ObjectMapper mapper = new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                    converters.add(new StringHttpMessageConverter());
                }
            };
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicationService applications;

    @Autowired
    private ConsentService consents;

    @Autowired
    private ApplicationRepository applicationRepo;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(applications.usidOf(APPLICATION_ID)).thenReturn(OWNER);
        when(consents.usidOf(CONSENT_ID)).thenReturn(OWNER);
        // The exception queue orders by a risk computed in Java, so the controller
        // asks the repository for a bounded window. An unstubbed mock returns null
        // and the handler NPEs into a 500, which would be indistinguishable from
        // "the guard denied it" in the officer row above.
        when(applicationRepo.findAll(any(Pageable.class))).thenReturn(Page.empty());
    }

    @ParameterizedTest(name = "{0} {1}: a scholar who does not own it is refused")
    @MethodSource("routes")
    @DisplayName("no path lets a scholar reach another scholar's record")
    void aScholarWhoDoesNotOwnTheRecordIsRefused(Route route) throws Exception {
        mvc.perform(request(route).with(scholar(OTHER)))
            .andExpect(status().is(route.forAScholarWhoDoesNotOwnIt()));
    }

    @ParameterizedTest(name = "{0} {1}: the owner is allowed")
    @MethodSource("routes")
    @DisplayName("an owner is never locked out of their own record")
    void theOwnerIsAllowed(Route route) throws Exception {
        mvc.perform(request(route).with(scholar(OWNER)))
            .andExpect(status().is(route.forTheOwner()));
    }

    @ParameterizedTest(name = "{0} {1}: an officer is allowed")
    @MethodSource("routes")
    @DisplayName("an officer reaches every path, including the cross-scholar ones")
    void anOfficerIsAllowed(Route route) throws Exception {
        mvc.perform(request(route).with(officer()))
            .andExpect(status().is(route.forAnOfficer()));
    }

    @Test
    @DisplayName("a scholar without a usid claim cannot pass ownership off as anybody's")
    void aScholarTokenWithNoUsidClaimIsRefusedEverywhere() throws Exception {
        // A JWT signed by the right key but carrying no `usid` claim must not be
        // treated as "the owner of everything". callerUsid() returning null has to
        // deny, not allow.
        for (Route route : routes()) {
            if (!route.ownerAllowed()) continue; // officer-only routes: covered above
            mvc.perform(request(route).with(jwt()
                    .authorities(new SimpleGrantedAuthority("ROLE_SCHOLAR"))))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("every path in the contract is in the audit table")
    void everyContractPathIsInTheTable() throws IOException {
        // The point of the exercise. Someone adds an endpoint, the contract grows,
        // and this fails until somebody decides who may call it.
        List<String> contractPaths = contractPaths();
        assertThat(contractPaths)
            .as("docs/openapi/core.yaml declares %s paths", contractPaths.size())
            .isNotEmpty();

        List<String> audited = routes().stream().map(Route::path).distinct().toList();
        assertThat(contractPaths)
            .as("paths added to the contract with no authorization decision")
            .allSatisfy(p -> assertThat(audited)
                .as("unauthenticated-by-default route %s", p)
                .contains(p));
    }

    @Test
    @DisplayName("the audit table has no route the contract does not declare")
    void theTableDoesNotDriftFromTheContract() throws IOException {
        // The other direction: a table row for a path that no longer exists is a
        // test that quietly stopped testing anything.
        List<String> audited = routes().stream().map(Route::path).distinct().toList();
        assertThat(audited)
            .as("table rows for paths that are no longer in the contract")
            .allSatisfy(p -> assertThat(contractPaths()).contains(p));
    }

    @Test
    @DisplayName("a guard that passes changes nothing else about the response")
    void anAllowedRequestStillReachesItsHandler() throws Exception {
        // Guards that deny everything would satisfy the 403 rows above. This proves
        // the 200 rows are the handler running, not the guard short-circuiting.
        mvc.perform(get("/v1/scholars/{usid}/claims", OWNER).with(scholar(OWNER)))
            .andExpect(status().isOk());
        mvc.perform(get("/v1/scholars/{usid}/dashboard", OWNER).with(scholar(OWNER)))
            .andExpect(status().isOk());
        mvc.perform(get("/v1/disbursements/{usid}", OWNER).with(scholar(OWNER)))
            .andExpect(status().isOk());
    }

    // --- helpers ---------------------------------------------------------------

    private MockHttpServletRequestBuilder request(Route route) {
        MockHttpServletRequestBuilder builder = switch (route.method()) {
            case "GET" -> get(route.url());
            case "POST" -> post(route.url());
            case "DELETE" -> delete(route.url());
            default -> throw new IllegalArgumentException("unsupported " + route.method());
        };
        String body = route.body();
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    private static RequestPostProcessor scholar(UUID usid) {
        return jwt()
            .jwt(b -> b.claim(ScholarAccessGuard.USID_CLAIM, usid.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_SCHOLAR"));
    }

    private static RequestPostProcessor officer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"));
    }

    /** Top-level `paths:` keys from the contract, which is the source of truth. */
    private static List<String> contractPaths() throws IOException {
        String yaml = Files.readString(
            repoRoot().resolve("docs/openapi/core.yaml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("^  (/(?:v1)[^:]*):\\s*$", Pattern.MULTILINE).matcher(yaml);
        java.util.List<String> found = new java.util.ArrayList<>();
        while (m.find()) found.add(m.group(1).trim());
        return found;
    }

    /** Walks up from the test working directory so this works from Gradle or an IDE. */
    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("docs/openapi/core.yaml"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "could not locate docs/openapi/core.yaml above " + System.getProperty("user.dir"));
    }
}
