package in.adivritti.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
 * The dev profile must actually boot.
 *
 * <p>{@code application-dev.yml} sets {@code app.security.allow-insecure-dev=true},
 * and that branch used to call {@code anyRequest()} a second time on an
 * already-configured registry. Spring Security rejects that with "Can't configure
 * anyRequest after itself", so the context refresh threw and {@code make dev} could
 * not start Core at all -- while a 105-test unit suite stayed green, because no test
 * built the filter chain. This test builds the real {@link SecurityConfig} chain and
 * proves both things the local demo depends on: the context refreshes, and an
 * unauthenticated {@code /v1} call is permitted in the dev profile.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigDevChainTest.StubApi.class})
@WebAppConfiguration
@TestPropertySource(properties = {
    // Exactly the dev profile and nothing else: the unauthenticated demo mode
    // only opens there. (The flag alone must not open the API, which is what
    // SecurityConfigNonDevFlagTest pins from the other side.)
    "spring.profiles.active=dev",
    "app.security.allow-insecure-dev=true",
    // The value committed in application-dev.yml; reaching the assertions at all
    // proves the decoder accepts it.
    "app.security.jwt.hmac-secret=" + SecurityConfigDevChainTest.DEV_HMAC_SECRET,
})
class SecurityConfigDevChainTest {

    static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class StubApi {
        @Bean
        PingEndpoint pingEndpoint() {
            return new PingEndpoint();
        }
    }

    /** Stand-in for the real routes: the assertions are about the filter chain. */
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
    @DisplayName("dev profile: the chain builds and unauthenticated /v1 calls are permitted")
    void unauthenticatedCallsArePermittedInDev() throws Exception {
        // The context refresh itself is the regression: before the fix this test
        // failed with IllegalStateException("Can't configure anyRequest after itself").
        mvc.perform(get("/v1/scholars/ping")).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/ping")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("dev profile: health stays reachable without a token")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health/ping")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the committed dev JWT secret satisfies the decoder's own key policy")
    void devSecretIsUsable() {
        assertThat(context.getBean(JwtDecoder.class)).isNotNull();
    }
}
