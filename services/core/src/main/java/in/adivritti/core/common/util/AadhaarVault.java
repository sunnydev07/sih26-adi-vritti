package in.adivritti.core.common.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aadhaar Data Vault.
 *
 * <p>The Aadhaar number itself is NEVER stored, logged, or returned. Only a keyed
 * reference key is persisted. Design notes:
 *
 * <ul>
 *   <li><b>Keyed HMAC, not bare SHA-256.</b> A bare digest of a 12-digit number is
 *       brute-forceable offline in seconds (the Aadhaar keyspace is only ~10^10).
 *       HMAC-SHA256 with a server-side secret makes precomputation and dictionary
 *       attacks infeasible.
 *   <li><b>Full 128-bit output.</b> The previous implementation truncated the digest
 *       to 16 hex chars (64 bits), which is a birthday-bound collision waiting to
 *       happen at scale. The full 256-bit digest is used instead.
 *   <li><b>Key-id prefix for rotation.</b> Reference keys are
 *       {@code AVR1:<keyId>:<hex>} so a secret rotation can read old keys while
 *       writing new ones.
 *   <li><b>Fail fast on weak/missing keys.</b> A missing or short key aborts startup
 *       rather than silently falling back to a guessable value.
 * </ul>
 */
@Component
public class AadhaarVault {

    /** Reference-key format version. Bump if the derivation ever changes. */
    public static final String REF_KEY_VERSION = "AVR1";

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    /** The digest half of a reference key: 32 bytes, hex-encoded. */
    private static final Pattern REFERENCE_DIGEST = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** Verhoeff check-digit tables, used to reject obviously malformed Aadhaar input. */
    private static final int[][] D = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
        {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
        {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
        {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
        {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
        {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
        {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
        {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
        {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    private static final int[][] P = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
        {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
        {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
        {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
        {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
        {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
        {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    private final byte[] hmacKey;
    private final String keyId;
    private final boolean enforceVerhoeff;

    public AadhaarVault(
        @Value("${app.aadhaar.vault-hmac-key}") String hmacKeyBase64,
        @Value("${app.aadhaar.vault-key-id:v1}") String keyId,
        @Value("${app.aadhaar.enforce-verhoeff:true}") boolean enforceVerhoeff) {
        this.hmacKey = decodeKey(hmacKeyBase64);
        this.keyId = sanitizeKeyId(keyId);
        this.enforceVerhoeff = enforceVerhoeff;
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                "app.aadhaar.vault-hmac-key is required. Set AADHAAR_VAULT_HMAC_KEY to a "
                    + "base64-encoded secret of at least 32 random bytes.");
        }
        byte[] key;
        try {
            key = java.util.Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "app.aadhaar.vault-hmac-key must be valid base64", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                "app.aadhaar.vault-hmac-key must decode to at least " + MIN_KEY_BYTES
                    + " bytes; got " + key.length + ".");
        }
        return key;
    }

    private static String sanitizeKeyId(String keyId) {
        String v = (keyId == null || keyId.isBlank()) ? "v1" : keyId.trim();
        if (!v.matches("[A-Za-z0-9_-]{1,16}")) {
            throw new IllegalStateException(
                "app.aadhaar.vault-key-id must be 1-16 chars of [A-Za-z0-9_-]");
        }
        return v;
    }

    /**
     * Derive the persisted reference key for an Aadhaar number.
     *
     * @param aadhaarNumber raw Aadhaar, with or without spaces/dashes
     * @return {@code AVR1:<keyId>:<64 hex chars>}
     * @throws IllegalArgumentException if the number is not 12 digits (never echoes the value)
     */
    public String referenceKey(String aadhaarNumber) {
        String normalized = normalize(aadhaarNumber);
        if (!normalized.matches("[0-9]{12}")) {
            throw new IllegalArgumentException("Aadhaar reference requires exactly 12 digits");
        }
        if (enforceVerhoeff && !verhoeffValid(normalized)) {
            throw new IllegalArgumentException("Aadhaar reference failed checksum validation");
        }
        return REF_KEY_VERSION + ":" + keyId + ":" + HexFormat.of().formatHex(mac(normalized));
    }

    /**
     * Derive a persisted reference key for an already-vaulted key (idempotent passthrough).
     *
     * <p>The full shape is checked, not just the {@code AVR1:<keyId>:} prefix. The
     * previous {@code startsWith} accepted {@code AVR1:v1:} and
     * {@code AVR1:v1:00} — a truncated or hand-written string that satisfied the
     * test. It is not used as a security gate today, so nothing leaked while it
     * was wrong; it is a predicate whose entire job is to say "this is a reference
     * key", and saying yes to a string that is not one is the failure mode. The
     * digest half is 64 hex characters, uppercase on the way out of
     * {@link HexFormat}, and accepted either case on the way in.
     */
    public boolean isReferenceKey(String value) {
        if (value == null) return false;
        String prefix = REF_KEY_VERSION + ":" + keyId + ":";
        if (!value.startsWith(prefix)) return false;
        String digest = value.substring(prefix.length());
        return REFERENCE_DIGEST.matcher(digest).matches();
    }

    /** Strip the spaces and dashes people type into Aadhaar fields. */
    public static String normalize(String aadhaarNumber) {
        if (aadhaarNumber == null) {
            return "";
        }
        return aadhaarNumber.replaceAll("[\\s\\-]", "").trim();
    }

    /**
     * Standard Verhoeff check-digit validation.
     *
     * <p>Positions are weighted from the right-hand end, which is what makes the
     * scheme a check digit rather than a checksum.
     */
    public static boolean verhoeffValid(String aadhaarNumber) {
        String n = normalize(aadhaarNumber);
        if (!n.matches("[0-9]{12}")) {
            return false;
        }
        int c = 0;
        int length = n.length();
        for (int i = 0; i < length; i++) {
            c = D[c][P[(length - 1 - i) % 8][n.charAt(i) - '0']];
        }
        return c == 0;
    }

    /**
     * Last-4 display fragment. The last 4 digits are permitted for display under DPDP
     * masking rules; nothing else about the number is ever exposed.
     */
    public String last4(String aadhaarNumber) {
        String normalized = normalize(aadhaarNumber);
        if (!normalized.matches("[0-9]{12}")) {
            throw new IllegalArgumentException("Aadhaar last4 requires exactly 12 digits");
        }
        return normalized.substring(8);
    }

    /** Masked display form, e.g. {@code XXXX-XXXX-4821}. Safe to render in the UI. */
    public String masked(String aadhaarNumber) {
        return "XXXX-XXXX-" + last4(aadhaarNumber);
    }

    private byte[] mac(String normalizedAadhaar) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, ALGORITHM));
            return mac.doFinal(normalizedAadhaar.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Aadhaar vault HMAC failure", e);
        }
    }

    /** Never include the number itself in a log line or exception message. */
    @Override
    public String toString() {
        return "AadhaarVault[keyId=" + keyId.toLowerCase(Locale.ROOT) + "]";
    }
}
