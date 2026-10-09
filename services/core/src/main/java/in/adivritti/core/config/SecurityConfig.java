package in.adivritti.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API security.
 *
 * <p>The previous configuration disabled CSRF and allowed every request through:
 * <pre>authorizeHttpRequests(auth -&gt; auth.anyRequest().permitAll())</pre>
 * On an API that returns Aadhaar reference keys, income figures, bank identifiers,
 * and DPDP access-audit trails, that is a total data exposure. Every {@code /v1}
 * route now requires a verified JWT, and officer-only routes additionally require a
 * role.
 *
 * <p>{@code app.security.allow-insecure-dev} re-opens the API for local demos, but
 * it is opt-in and the prod profile refuses to start with it enabled.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String OFFICER_ROLE = "OFFICER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MIN_HMAC_KEY_BYTES = 32;

    private final boolean allowInsecureDev;
    private final boolean devProfile;
    private final boolean devOnlyProfile;

    public SecurityConfig(
        @Value("${app.security.allow-insecure-dev:false}") boolean allowInsecureDev,
        @Value("${spring.profiles.active:default}") String activeProfile) {
        this.allowInsecureDev = allowInsecureDev;
        // Comma-separated when several profiles are active ("dev,local").
        java.util.List<String> profiles = java.util.Arrays.stream(activeProfile.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        this.devProfile = profiles.contains("dev");
        // The unauthenticated demo mode is only meaningful as the whole posture:
        // exactly the dev profile and nothing else. "prod,dev" still loads the
        // committed dev constants, so the flag must not open the API there either.
        this.devOnlyProfile = profiles.size() == 1 && profiles.contains("dev");
    }

    /**
     * Local serializer for pre-controller rejections. Deliberately not an injected
     * bean: the filter chain must stay constructible in minimal test slices without
     * Jackson auto-configuration, and every value written is already a
     * string/number/map so no modules are needed.
     */
    private static final ObjectMapper ENVELOPE_MAPPER = new ObjectMapper();

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: there is no session or form login for
            // CSRF tokens to protect, and the endpoints are not cookie-authenticated.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                // Pre-controller rejections must use the same error envelope as
                // GlobalExceptionHandler (error_code/message/details/at/path per
                // docs/openapi/core.yaml), not Spring's default HTML/empty body.
                .authenticationEntryPoint((request, response, ex) ->
                    writeEnvelope(request, response, ENVELOPE_MAPPER, HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED", "Authentication required"))
                .accessDeniedHandler((request, response, ex) ->
                    writeEnvelope(request, response, ENVELOPE_MAPPER, HttpStatus.FORBIDDEN,
                        "FORBIDDEN", "Access denied")))
            // One authorizeHttpRequests block, one anyRequest() call. Both matter:
            // Spring Security's registry rejects a second anyRequest() with "Can't
            // configure anyRequest after itself", and a second authorizeHttpRequests
            // call applies to the same registry. The dev escape hatch therefore has
            // to be selected *inside* this lambda rather than by re-configuring
            // HttpSecurity after the fact -- which is what used to make the whole
            // context fail to refresh in the dev profile.
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                // The generated OpenAPI document is a complete route map for a
                // service whose payloads carry Aadhaar reference keys, income
                // figures and DPDP access-audit rows. Serving it to an anonymous
                // scanner hands over that inventory for free, so it is NOT public
                // in a locked-down deployment: an OFFICER/ADMIN token is required,
                // and the dev profile keeps it open because the CI contract-drift
                // job fetches it unauthenticated.
                if (devProfile) {
                    auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll();
                } else {
                    auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**")
                        .hasAnyRole(OFFICER_ROLE, ADMIN_ROLE);
                }
                if (allowInsecureDev && devOnlyProfile) {
                    log.warn("SECURITY: app.security.allow-insecure-dev=true — every /v1 endpoint is "
                        + "UNAUTHENTICATED. This is for local demos only.");
                    auth.anyRequest().permitAll();
                } else {
                    if (allowInsecureDev) {
                        log.warn("SECURITY: app.security.allow-insecure-dev=true is IGNORED "
                            + "because the active profiles are not exactly {dev} — every /v1 "
                            + "endpoint stays authenticated. The demo escape hatch only opens "
                            + "under the dev profile.");
                    }
                    auth.requestMatchers("/v1/admin/**").hasAnyRole(OFFICER_ROLE, ADMIN_ROLE)
                        .anyRequest().authenticated();
                }
            });
        return http.build();
    }

    /**
     * Serialises a filter-chain rejection in the contract's error envelope.
     *
     * <p>Keys are written snake_case literally (rather than via the
     * {@code ErrorBody} record) so the shape holds even if the record or the
     * mapper's naming strategy changes; {@code SecurityContractTest} pins the
     * record side, this pins the pre-controller side.
     */
    private static void writeEnvelope(HttpServletRequest request, HttpServletResponse response,
        ObjectMapper mapper, HttpStatus status, String code, String message)
        throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", status.value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", code);
        body.put("message", message);
        body.put("details", details);
        body.put("at", ZonedDateTime.now(ZoneOffset.UTC).toString());
        body.put("path", request == null ? null : request.getRequestURI());
        mapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * Maps JWT claims onto Spring authorities. Accepts a {@code roles} claim as a
     * space- or comma-separated list (the common IdP shape) in addition to the
     * standard {@code scope}/{@code scp} claim.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new java.util.ArrayList<>();
            StreamSupport.stream(scopeConverter.convert(jwt).spliterator(), false)
                .forEach(authorities::add);
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof Collection<?> list) {
                list.forEach(r -> authorities.add(
                    new SimpleGrantedAuthority("ROLE_" + String.valueOf(r).toUpperCase())));
            } else if (roles instanceof String s && !s.isBlank()) {
                for (String role : s.split("[,\\s]+")) {
                    if (!role.isBlank()) {
                        authorities.add(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }

    /**
     * Dual-mode JWT verification: prefer a JWKS endpoint (asymmetric, key rotation
     * handled by the IdP); fall back to a shared HS256 secret. Missing configuration
     * is a startup failure, not a silent downgrade to "accept anything".
     *
     * <p>When {@code app.security.jwt.issuer} is set, tokens must also carry that
     * {@code iss} claim (in addition to the default expiry/audience checks).
     * Unset means "any issuer signed by the configured key", which is the local
     * demo posture — key rotation and issuer pinning arrive with the real IdP.
     */
    @Bean
    JwtDecoder jwtDecoder(
        @Value("${app.security.jwt.jwk-set-uri:}") String jwkSetUri,
        @Value("${app.security.jwt.hmac-secret:}") String hmacSecretBase64,
        @Value("${app.security.jwt.issuer:}") String issuer) {

        NimbusJwtDecoder decoder;
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else if (hmacSecretBase64 != null && !hmacSecretBase64.isBlank()) {
            byte[] key;
            try {
                key = java.util.Base64.getDecoder().decode(hmacSecretBase64.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.security.jwt.hmac-secret must be base64", e);
            }
            if (key.length < MIN_HMAC_KEY_BYTES) {
                throw new IllegalStateException(
                    "app.security.jwt.hmac-secret must decode to at least "
                        + MIN_HMAC_KEY_BYTES + " bytes; got " + key.length + ".");
            }
            decoder = NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        } else {
            throw new IllegalStateException(
                "No JWT verification key configured. Set app.security.jwt.jwk-set-uri or "
                    + "app.security.jwt.hmac-secret, or enable app.security.allow-insecure-dev "
                    + "for a local demo.");
        }
        if (issuer != null && !issuer.isBlank()) {
            OAuth2TokenValidator<Jwt> withIssuer =
                JwtValidators.createDefaultWithIssuer(issuer.trim());
            decoder.setJwtValidator(withIssuer);
        }
        return decoder;
    }
}
