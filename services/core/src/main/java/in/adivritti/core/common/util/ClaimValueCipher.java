package in.adivritti.core.common.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authenticated encryption for Verified Claims Wallet values.
 *
 * <p>Claims carry income figures, disability status, and bank identifiers. They are
 * sealed with AES-256-GCM before touching the database, so {@code claim.value_encrypted}
 * is a real ciphertext rather than a placeholder column.
 *
 * <p>Layout: {@code [12-byte IV | ciphertext | 16-byte GCM tag]}, which is what the
 * {@code bytea} column stores. GCM is used (not CBC) because it authenticates as well
 * as encrypts — tampering with a stored claim is detected on read.
 */
@Component
public class ClaimValueCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ClaimValueCipher(@Value("${app.vault.encryption-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                "app.vault.encryption-key is required. Set CLAIM_VAULT_KEY to a base64-encoded "
                    + "secret of at least 32 random bytes.");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.vault.encryption-key must be valid base64", e);
        }
        if (raw.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                "app.vault.encryption-key must decode to at least " + MIN_KEY_BYTES
                    + " bytes; got " + raw.length + ".");
        }
        // AES only accepts 128/192/256-bit keys. Passing the configured secret
        // straight through means a 35-byte secret fails with InvalidKeyException
        // the first time a claim is sealed -- a runtime crash on the first
        // verification, not a startup error. Deriving a fixed 32-byte key with
        // SHA-256 makes the "at least 32 bytes" contract above actually hold.
        this.key = new SecretKeySpec(sha256(raw), ALGORITHM);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Seal a claim value. Each call uses a fresh random IV — never reuse one. */
    public byte[] seal(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + sealed.length)
                .put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Claim value encryption failed", e);
        }
    }

    /** Open a sealed claim value. Throws if the ciphertext was tampered with. */
    public String open(byte[] sealed) {
        if (sealed == null || sealed.length <= IV_BYTES) {
            throw new IllegalStateException("Sealed claim value is truncated or missing");
        }
        ByteBuffer buf = ByteBuffer.wrap(sealed);
        byte[] iv = new byte[IV_BYTES];
        buf.get(iv);
        byte[] body = new byte[buf.remaining()];
        buf.get(body);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Claim value failed authentication", e);
        }
    }
}
