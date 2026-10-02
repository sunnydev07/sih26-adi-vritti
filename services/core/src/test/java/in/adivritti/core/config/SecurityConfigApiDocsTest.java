package in.adivritti.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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
 * Task 0.3: {@code /v3/api-docs} is a full route map for a service whose payloads
 * carry Aadhaar reference keys, income figures and DPDP audit rows. It used to be
 * {@code permitAll()} in every profile, so an anonymous scanner got the inventory.
 *
 * <p>It is now open only under the {@code dev} profile — the CI contract-drift
 * job and the dev stack both fetch it unauthenticated — and OFFICER/ADMIN-gated
 * everywhere else.
 */
class SecurityConfigApiDocsTest {

    private static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";

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
        @GetMapping("/v3/api-docs")
        String docs() {
            return "{}";
        }
    }

    @Nested
    @SpringJUnitConfig(classes = {SecurityConfig.class, StubApi.class})
    @WebAppConfiguration
    @TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.security.allow-insecure-dev=true",
        "app.security.jwt.hmac-secret=" + SecurityConfigApiDocsTest.DEV_HMAC_SECRET,
    })
    class DevProfile {

        @Autowired
        private WebApplicationContext context;

        @Test
        @DisplayName("dev profile: /v3/api-docs is public, so the CI drift check keeps working")
        void apiDocsArePublicInDev() throws Exception {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringJUnitConfig(classes = {SecurityConfig.class, StubApi.class})
    @WebAppConfiguration
    @TestPropertySource(properties = {
        "spring.profiles.active=prod",
        "app.security.allow-insecure-dev=false",
        "app.security.jwt.hmac-secret=" + SecurityConfigApiDocsTest.DEV_HMAC_SECRET,
    })
    class LockedDownProfile {

        @Autowired
        private WebApplicationContext context;

        private MockMvc mvc() {
            return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }

        @Test
        @DisplayName("prod profile: an anonymous scanner gets 401, not the route map")
        void apiDocsRequireATokenInProd() throws Exception {
            mvc().perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        }
    }
}

/**
 * Task 0.2: the dev profile commits its secrets on purpose, and every one of them
 * is public. This proves the prod profile refuses to boot while any of them is
 * still in place, and that a real value boots clean.
 */
class ProductionSecretGuardTest {

    private static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";
    private static final String DEV_VAULT_KEY = "ZGV2LW9ubHktYWVzMjU2LWtleS0zMi1ieXRlcy1sb24=";
    private static final String DEV_VAULT_HMAC = "ZGV2LW9ubHktaG1hYy1rZXktMzItYnl0ZXMtbG9uZyE=";
    private static final String REAL_SECRET = "c2VjdXJlLXByb2Qta2V5LTMyLWJ5dGVzLW5vdC1kZXY=";

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withUserConfiguration(ProductionSecretGuard.class)
            // The guard is @Profile("prod"); activate it explicitly so the test
            // does not need the whole application context.
            .withPropertyValues(
                "spring.profiles.active=prod",
                "app.aadhaar.vault-hmac-key=" + REAL_SECRET,
                "app.vault.encryption-key=" + REAL_SECRET,
                "app.security.jwt.hmac-secret=" + REAL_SECRET,
                "app.security.jwt.jwk-set-uri=",
                "app.ai-service.token=" + REAL_SECRET,
                "spring.datasource.password=" + REAL_SECRET,
                "app.security.allow-insecure-dev=false");
    }

    @Test
    @DisplayName("prod boots clean when every secret is a real value")
    void realSecretsStartTheProdProfile() {
        runner().run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("prod refuses to boot with the committed Aadhaar vault HMAC key")
    void rejectsDevAadhaarKey() {
        runner().withPropertyValues("app.aadhaar.vault-hmac-key=" + DEV_VAULT_HMAC)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("prod refuses to boot with the committed claim AES key")
    void rejectsDevClaimKey() {
        runner().withPropertyValues("app.vault.encryption-key=" + DEV_VAULT_KEY)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("prod refuses to boot with the committed JWT secret")
    void rejectsDevJwtSecret() {
        runner().withPropertyValues("app.security.jwt.hmac-secret=" + DEV_HMAC_SECRET)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("prod refuses to boot with the published AI service token")
    void rejectsDevAiServiceToken() {
        runner().withPropertyValues("app.ai-service.token=dev-only-ai-service-token")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("prod refuses to boot with the committed database password")
    void rejectsDevDatabasePassword() {
        runner().withPropertyValues("spring.datasource.password=adivritti_dev")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("prod refuses to boot with allow-insecure-dev on")
    void rejectsInsecureDevEscapeHatch() {
        runner().withPropertyValues("app.security.allow-insecure-dev=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a JWKS deployment is not blocked by the shared-secret check")
    void jwksDeploymentIsNotBlocked() {
        runner().withPropertyValues(
                "app.security.jwt.hmac-secret=" + DEV_HMAC_SECRET,
                "app.security.jwt.jwk-set-uri=https://idp.example/.well-known/jwks.json")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("the failure message names every offending property")
    void theMessageIsActionable() {
        var guard = new ProductionSecretGuard(
            DEV_VAULT_HMAC, DEV_VAULT_KEY, DEV_HMAC_SECRET, "",
            "dev-only-ai-service-token", "adivritti_dev", false);
        assertThatThrownBy(guard::check)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("REFUSING TO START")
            .hasMessageContaining("app.aadhaar.vault-hmac-key")
            .hasMessageContaining("app.vault.encryption-key")
            .hasMessageContaining("app.security.jwt.hmac-secret")
            .hasMessageContaining("app.ai-service.token")
            .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("a blank value is not treated as a dev constant")
    void blankValuesAreNotCollisions() {
        var guard = new ProductionSecretGuard("", "", "", "", "", "", false);
        assertThatCode(guard::check).doesNotThrowAnyException();
    }
}
