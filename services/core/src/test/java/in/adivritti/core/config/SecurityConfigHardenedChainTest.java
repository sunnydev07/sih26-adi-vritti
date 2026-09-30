package in.adivritti.core.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The hardened branch of {@link SecurityConfig}, which is the one the dev-profile fix
 * had to move without weakening: {@code allow-insecure-dev=false} must still require a
 * token on every {@code /v1} route and still keep the officer role gate on
 * {@code /v1/admin/**}.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigHardenedChainTest.StubApi.class})
@WebAppConfiguration
@TestPropertySource(properties = {
    "app.security.allow-insecure-dev=false",
    "app.security.jwt.hmac-secret=" + SecurityConfigHardenedChainTest.DEV_HMAC_SECRET,
})
class SecurityConfigHardenedChainTest {

    static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class StubApi {
        @Bean
        PingEndpoint pingEndpoint() {
            return new PingEndpoint();
        }
    }

    @RestController
    static class PingEndpoint {
        @GetMapping({"/v1/scholars/ping", "/v1/admin/ping", "/actuator/health/ping"})
        String ping() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("no token means no access to /v1")
    void unauthenticatedCallsAreRejected() throws Exception {
        mvc.perform(get("/v1/scholars/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a scholar token cannot reach the officer console routes")
    void scholarIsForbiddenFromTheAdminRoutes() throws Exception {
        mvc.perform(get("/v1/admin/ping")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SCHOLAR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an officer token reaches the officer console routes")
    void officerReachesTheAdminRoutes() throws Exception {
        mvc.perform(get("/v1/admin/ping")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("health stays public so probes do not need a credential")
    void healthIsPublicWithoutAToken() throws Exception {
        mvc.perform(get("/actuator/health/ping")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the regenerated API document stays public for the drift check")
    void apiDocsArePublicWithoutAToken() throws Exception {
        // No springdoc handler is registered in this slice, so a request the
        // chain permits falls through to 404 — which is exactly the assertion:
        // the security chain let it through instead of answering 401/403. The
        // document is public by design (the contract it is diffed against is
        // committed to the repo).
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
