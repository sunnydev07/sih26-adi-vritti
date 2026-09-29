package in.adivritti.core.config;

import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

    public SecurityConfig(@Value("${app.security.allow-insecure-dev:false}") boolean allowInsecureDev) {
        this.allowInsecureDev = allowInsecureDev;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: there is no session or form login for
            // CSRF tokens to protect, and the endpoints are not cookie-authenticated.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v1/admin/**").hasAnyRole(OFFICER_ROLE, ADMIN_ROLE)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        if (allowInsecureDev) {
            log.warn("SECURITY: app.security.allow-insecure-dev=true — every /v1 endpoint is "
                + "UNAUTHENTICATED. This is for local demos only.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        }
        return http.build();
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
     */
    @Bean
    JwtDecoder jwtDecoder(
        @Value("${app.security.jwt.jwk-set-uri:}") String jwkSetUri,
        @Value("${app.security.jwt.hmac-secret:}") String hmacSecretBase64) {

        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        if (hmacSecretBase64 != null && !hmacSecretBase64.isBlank()) {
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
            return NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        }
        throw new IllegalStateException(
            "No JWT verification key configured. Set app.security.jwt.jwk-set-uri or "
                + "app.security.jwt.hmac-secret, or enable app.security.allow-insecure-dev "
                + "for a local demo.");
    }
}
