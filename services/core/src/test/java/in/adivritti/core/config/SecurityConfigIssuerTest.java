package in.adivritti.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * {@code app.security.jwt.issuer} must actually constrain verification.
 *
 * <p>The setting existed in {@code application.yml} but no code read it, so a
 * token minted for any issuer was accepted as long as the key matched. When
 * the issuer is configured, a wrong {@code iss} is rejected; a right one
 * still decodes.
 */
@SpringJUnitConfig(classes = SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.allow-insecure-dev=false",
    "app.security.jwt.hmac-secret=" + SecurityConfigIssuerTest.DEV_HMAC_SECRET,
    "app.security.jwt.issuer=https://issuer.example.com",
})
class SecurityConfigIssuerTest {

    static final String DEV_HMAC_SECRET = "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=";
    private static final String ISSUER = "https://issuer.example.com";

    @Autowired
    JwtDecoder decoder;

    /** Hand-rolled HS256 JWT: no extra test dependencies for one token. */
    private static String hs256(String iss) throws Exception {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long now = Instant.now().getEpochSecond();
        String payload = url.encodeToString(
            ("{\"iss\":\"" + iss + "\",\"sub\":\"test\",\"iat\":" + now
                + ",\"exp\":" + (now + 300) + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(DEV_HMAC_SECRET), "HmacSHA256"));
        String sig = url.encodeToString(
            mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return header + "." + payload + "." + sig;
    }

    @Test
    @DisplayName("token from the configured issuer decodes")
    void configuredIssuerAccepted() throws Exception {
        Jwt jwt = decoder.decode(hs256(ISSUER));

        assertThat(jwt.getSubject()).isEqualTo("test");
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
    }

    @Test
    @DisplayName("token from any other issuer is rejected")
    void otherIssuerRejected() throws Exception {
        assertThatThrownBy(() -> decoder.decode(hs256("https://evil.example.com")))
            .isInstanceOf(JwtException.class);
    }
}
