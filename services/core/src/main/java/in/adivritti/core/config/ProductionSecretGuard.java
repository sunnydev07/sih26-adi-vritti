package in.adivritti.core.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot on any non-dev profile while a security-relevant secret is
 * still a value published in this repository.
 *
 * <p>The dev profile deliberately commits its secrets — that is what makes
 * {@code make dev} work with no setup step — and every one of them is a constant
 * in {@code application-dev.yml} and {@code infra/docker-compose.yml}. The failure
 * mode this guards against is a deployment that does NOT run the dev profile
 * (staging, a custom profile, or no profile at all) yet inherits those constants:
 * {@code allow-insecure-dev=true} would then turn the whole API into
 * {@code anyRequest().permitAll()}, and the Aadhaar vault HMAC key, the claim
 * AES key, the JWT secret and the AI service token would all be public values
 * that anyone who has read the repo can use to decrypt the wallet or forge a
 * token.
 *
 * <p>It runs from {@link #check()} during context refresh, so the process dies
 * with this message rather than coming up and serving traffic:
 *
 * <pre>
 *   *** REFUSING TO START: non-dev profile is using development secrets ***
 *     - app.aadhaar.vault-hmac-key still holds the committed development value
 *     ...
 * </pre>
 *
 * <p>Scoped to every profile EXCEPT {@code dev} ({@code @Profile("!dev")}). The
 * dev profile is the one deployment that is allowed to run on the committed
 * constants, and {@link SecurityConfig} additionally refuses to open its
 * unauthenticated demo mode unless the active profiles are exactly
 * {@code {dev}} — so a production box that accidentally boots the dev stack
 * file stays a loud, deliberate choice rather than a silent default.
 */
@Component
@Profile("!dev")
public class ProductionSecretGuard {

    /** base64("dev-only-hmac-key-32-bytes-long!") — application-dev.yml */
    static final String DEV_AADHAAR_HMAC_KEY =
        "ZGV2LW9ubHktaG1hYy1rZXktMzItYnl0ZXMtbG9uZyE=";
    /** base64("dev-only-aes256-key-32-bytes-lon") — application-dev.yml */
    static final String DEV_CLAIM_VAULT_KEY =
        "ZGV2LW9ubHktYWVzMjU2LWtleS0zMi1ieXRlcy1sb24=";
    /** base64("dev-only-jwt-secret-32-bytes-lo") — application-dev.yml */
    static final String DEV_JWT_HMAC_SECRET =
        "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";
    /** The published placeholder both compose and the AI service default to. */
    static final String DEV_AI_SERVICE_TOKEN = "dev-only-ai-service-token";
    static final String DEV_DB_PASSWORD = "adivritti_dev";

    private final Map<String, String> configured = new LinkedHashMap<>();
    private final boolean allowInsecureDev;

    public ProductionSecretGuard(
        @Value("${app.aadhaar.vault-hmac-key:}") String aadhaarHmacKey,
        @Value("${app.vault.encryption-key:}") String claimVaultKey,
        @Value("${app.security.jwt.hmac-secret:}") String jwtHmacSecret,
        @Value("${app.security.jwt.jwk-set-uri:}") String jwkSetUri,
        @Value("${app.ai-service.token:}") String aiServiceToken,
        @Value("${spring.datasource.password:}") String datasourcePassword,
        @Value("${app.security.allow-insecure-dev:false}") boolean allowInsecureDev) {

        this.allowInsecureDev = allowInsecureDev;
        configured.put("app.aadhaar.vault-hmac-key", aadhaarHmacKey);
        configured.put("app.vault.encryption-key", claimVaultKey);
        // Only a *shared secret* can collide with a published constant; a JWKS
        // URL is a public identifier, so pinning it to a dev value is meaningless.
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            configured.put("app.security.jwt.hmac-secret", jwtHmacSecret);
        }
        configured.put("app.ai-service.token", aiServiceToken);
        configured.put("spring.datasource.password", datasourcePassword);
    }

    @PostConstruct
    void check() {
        List<String> problems = new ArrayList<>();

        devConstant("app.aadhaar.vault-hmac-key", DEV_AADHAAR_HMAC_KEY)
            .ifPresent(problems::add);
        devConstant("app.vault.encryption-key", DEV_CLAIM_VAULT_KEY)
            .ifPresent(problems::add);
        devConstant("app.security.jwt.hmac-secret", DEV_JWT_HMAC_SECRET)
            .ifPresent(problems::add);
        devConstant("app.ai-service.token", DEV_AI_SERVICE_TOKEN)
            .ifPresent(problems::add);
        devConstant("spring.datasource.password", DEV_DB_PASSWORD)
            .ifPresent(problems::add);

        if (allowInsecureDev) {
            problems.add("app.security.allow-insecure-dev is true, which makes every "
                + "/v1 endpoint unauthenticated");
        }

        // GAP_HMAC_SALT is not read by this process (it belongs to the AI
        // service, which fails its own prod check), so nothing to assert here.
        if (problems.isEmpty()) return;

        throw new IllegalStateException(
            "*** REFUSING TO START: non-dev profile is using development secrets ***\n"
                + problems.stream().map(p -> "  - " + p).collect(java.util.stream.Collectors.joining("\n"))
                + "\nSet each value from the deployment's secret store, or run with the "
                + "dev profile for local demos. The committed values are in "
                + "services/core/src/main/resources/application-dev.yml and "
                + "infra/docker-compose.yml.");
    }

    private java.util.Optional<String> devConstant(String key, String devValue) {
        String value = configured.get(key);
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        if (!value.trim().equals(devValue)) return java.util.Optional.empty();
        return java.util.Optional.of(key + " still holds the committed development value");
    }
}
