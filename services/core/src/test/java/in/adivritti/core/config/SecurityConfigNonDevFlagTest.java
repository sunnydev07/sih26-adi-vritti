package in.adivritti.core.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * The insecure-dev escape hatch must not open the API outside the dev profile.
 *
 * <p>A deployment that sets {@code allow-insecure-dev=true} (copied from a local
 * setup, or a staging slot reusing dev-shaped config) while running any other
 * profile used to get {@code anyRequest().permitAll()} — the whole API,
 * unauthenticated, on the committed dev secrets, with no guard running. The
 * hatch now only opens when the active profiles are exactly {@code {dev}};
 * everywhere else the flag is ignored loudly and the chain stays locked.
 */
@SpringJUnitConfig(classes = {SecurityConfigNonDevFlagTest.StubApi.class, SecurityConfig.class})
@WebAppConfiguration
@TestPropertySource(properties = {
    "spring.profiles.active=staging",
    "app.security.allow-insecure-dev=true",
    "app.security.jwt.hmac-secret=" + SecurityConfigNonDevFlagTest.DEV_HMAC_SECRET,
})
class SecurityConfigNonDevFlagTest {

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
        @GetMapping({"/v1/scholars/ping", "/actuator/health/ping"})
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
    @DisplayName("the insecure flag is ignored outside the dev profile: /v1 stays locked")
    void insecureFlagDoesNotOpenTheApiOutsideDev() throws Exception {
        mvc.perform(get("/v1/scholars/ping"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error_code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("health stays public so probes do not need a credential")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health/ping")).andExpect(status().isOk());
    }
}
