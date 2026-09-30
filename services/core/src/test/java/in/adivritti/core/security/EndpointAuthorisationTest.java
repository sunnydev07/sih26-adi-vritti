package in.adivritti.core.security;

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
import in.adivritti.core.application.ApplicationController;
import in.adivritti.core.application.ApplicationService;
import in.adivritti.core.common.exception.GlobalExceptionHandler;
import in.adivritti.core.config.SecurityConfig;
import in.adivritti.core.consent.ConsentController;
import in.adivritti.core.consent.ConsentService;
import in.adivritti.core.identity.IdentityController;
import in.adivritti.core.identity.IdentityService;
import in.adivritti.core.verification.VerificationController;
import in.adivritti.core.verification.VerificationOrchestrator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The four endpoints that took no ownership or role check must now refuse.
 *
 * <p>Each one carried a personal-data write or read that only the route bound: the
 * verification body's USID (claim/deficiency write), an application UUID (timeline),
 * a consent UUID (revocation) and identity resolution (which mints USIDs and reports
 * whether a person is already enrolled). A scholar token could reach all four.
 *
 * <p>Real {@link SecurityConfig} chain, real {@link ScholarAccessGuard}, real
 * controllers and {@link GlobalExceptionHandler}; only the services are mocked, so this
 * is an authorization test rather than a wiring test. Runs with
 * {@code allow-insecure-dev=false}, the profile where the gates must bite.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, EndpointAuthorisationTest.StubApi.class})
@WebAppConfiguration
@TestPropertySource(properties = {
    "app.security.allow-insecure-dev=false",
    "app.security.jwt.hmac-secret=" + EndpointAuthorisationTest.DEV_HMAC_SECRET,
})
class EndpointAuthorisationTest {

    static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID APPLICATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONSENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({VerificationController.class, ApplicationController.class, ConsentController.class,
        IdentityController.class, GlobalExceptionHandler.class})
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

        /**
         * Spring Boot's Jackson auto-configuration is absent in this minimal context, so
         * the contract's snake_case wire format has to be installed explicitly here.
         * The production setting itself is pinned by {@code WireFormatContractTest}
         * against {@code application.yml}; without this the test would send camelCase
         * bodies and prove the opposite of what the contract says.
         */
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

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(applications.usidOf(APPLICATION_ID)).thenReturn(OWNER);
        when(consents.usidOf(CONSENT_ID)).thenReturn(OWNER);
    }

    private static RequestPostProcessor scholar(UUID usid) {
        return jwt()
            .jwt(builder -> builder.claim(ScholarAccessGuard.USID_CLAIM, usid.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_SCHOLAR"));
    }

    private static RequestPostProcessor officer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"));
    }

    @Test
    @DisplayName("POST /v1/verify refuses a write against another scholar's USID")
    void verifyRefusesAnotherScholarsUsid() throws Exception {
        mvc.perform(post("/v1/verify")
                .with(scholar(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usid\":\"" + OTHER + "\",\"claim_type\":\"income\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/verify still works for the caller's own USID")
    void verifyAllowsTheOwnersUsid() throws Exception {
        mvc.perform(post("/v1/verify")
                .with(scholar(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usid\":\"" + OWNER + "\",\"claim_type\":\"income\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/applications/{id}/timeline is not readable by UUID alone")
    void timelineRefusesAnotherScholarsApplication() throws Exception {
        mvc.perform(get("/v1/applications/{id}/timeline", APPLICATION_ID).with(scholar(OTHER)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/applications/{id}/timeline works for the owner")
    void timelineAllowsTheOwner() throws Exception {
        mvc.perform(get("/v1/applications/{id}/timeline", APPLICATION_ID).with(scholar(OWNER)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /v1/consent/{id} cannot revoke another scholar's consent")
    void consentRevocationRefusesAnotherScholarsConsent() throws Exception {
        mvc.perform(delete("/v1/consent/{id}", CONSENT_ID).with(scholar(OTHER)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /v1/consent/{id} works for the owner")
    void consentRevocationAllowsTheOwner() throws Exception {
        mvc.perform(delete("/v1/consent/{id}", CONSENT_ID).with(scholar(OWNER)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/identity/resolve is closed to scholar tokens")
    void identityResolutionRefusesAScholar() throws Exception {
        mvc.perform(post("/v1/identity/resolve")
                .with(scholar(OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(resolveBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/identity/resolve is open to officers")
    void identityResolutionAllowsAnOfficer() throws Exception {
        mvc.perform(post("/v1/identity/resolve")
                .with(officer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resolveBody()))
            .andExpect(status().isOk());
    }

    private static String resolveBody() {
        return "{\"records\":[{\"system_name\":\"NSP\",\"external_id\":\"NSP-1\","
            + "\"full_name\":\"Sunita Meena\"}]}";
    }
}
