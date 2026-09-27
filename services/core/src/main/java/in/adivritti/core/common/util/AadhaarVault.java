package in.adivritti.core.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aadhaar Data Vault pattern: the Aadhaar number itself is NEVER stored.
 * Only an HMAC-style reference key derived with a server-side secret is persisted.
 */
@Component
public class AadhaarVault {

    private final String pepper;

    public AadhaarVault(@Value("${app.aadhaar.vault-encryption-key:dev-key-replace-in-prod}") String pepper) {
        this.pepper = pepper;
    }

    public String referenceKey(String aadhaarNumber) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((pepper + ":" + aadhaarNumber).getBytes(StandardCharsets.UTF_8));
            return "AVR-" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Aadhaar vault failure", e);
        }
    }
}
