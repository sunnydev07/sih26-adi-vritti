package in.adivritti.core.common.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ClaimValueCipherTest {

    private static String key(int bytes) {
        byte[] raw = new byte[bytes];
        java.util.Arrays.fill(raw, (byte) 3);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static ClaimValueCipher cipher() {
        return new ClaimValueCipher(key(32));
    }

    @Test
    void sealedValueRoundTrips() {
        ClaimValueCipher c = cipher();
        String secret = "{\"income_paise\":2500000}";
        assertEquals(secret, c.open(c.seal(secret)));
    }

    @Test
    void ciphertextDoesNotContainThePlaintext() {
        ClaimValueCipher c = cipher();
        String secret = "account-holder-name";
        byte[] sealed = c.seal(secret);
        assertTrue(new String(sealed, java.nio.charset.StandardCharsets.ISO_8859_1)
            .contains(secret) == false, "ciphertext leaked the plaintext");
    }

    @Test
    void eachSealUsesAFreshIv() {
        ClaimValueCipher c = cipher();
        byte[] a = c.seal("same-input");
        byte[] b = c.seal("same-input");
        assertNotEquals(a.length, 0);
        // Identical plaintext must not produce identical ciphertext.
        assertTrue(!java.util.Arrays.equals(a, b), "IV reuse detected");
    }

    @Test
    void tamperingIsDetectedOnOpen() {
        ClaimValueCipher c = cipher();
        byte[] sealed = c.seal("2500000");
        sealed[sealed.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> c.open(sealed),
            "GCM must reject a modified ciphertext");
    }

    @Test
    void truncatedCiphertextIsRejected() {
        ClaimValueCipher c = cipher();
        assertThrows(IllegalStateException.class, () -> c.open(new byte[] {1, 2, 3}));
        assertThrows(IllegalStateException.class, () -> c.open(null));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        byte[] sealed = cipher().seal("secret-value");
        assertThrows(IllegalStateException.class, () -> new ClaimValueCipher(key(64)).open(sealed));
    }

    @Test
    void missingOrWeakKeyAbortsConstruction() {
        assertThrows(IllegalStateException.class, () -> new ClaimValueCipher(null));
        assertThrows(IllegalStateException.class, () -> new ClaimValueCipher(""));
        assertThrows(IllegalStateException.class, () -> new ClaimValueCipher(key(16)));
        assertThrows(IllegalStateException.class, () -> new ClaimValueCipher("not-base64!!"));
    }

    @Test
    void everyAcceptedKeyLengthRoundTrips() {
        // Regression: the cipher accepts >= 32 bytes but AES only accepts 128/192/256-bit
        // keys. Handing the raw secret to SecretKeySpec meant a 35-byte key (the length
        // the previous development default happened to be) threw InvalidKeyException on
        // the FIRST seal rather than at construction -- so the service booted healthy and
        // then failed the first time a claim was verified. Key derivation makes the
        // documented ">= 32 bytes" contract true for every accepted input.
        for (int len : new int[] {32, 33, 35, 48, 64, 100}) {
            ClaimValueCipher c = new ClaimValueCipher(key(len));
            String secret = "{\"income_paise\":" + len + "}";
            assertEquals(secret, c.open(c.seal(secret)),
                "key of " + len + " bytes must round-trip");
        }
    }

    @Test
    void theShippedDevelopmentDefaultKeyWorks() {
        // The exact value in application-dev.yml and infra/docker-compose.yml. If this
        // breaks, `make dev` encrypts nothing successfully.
        String devKey = "ZGV2LW9ubHktYWVzMjU2LWtleS0zMi1ieXRlcy1sb24=";
        assertEquals(32, Base64.getDecoder().decode(devKey).length,
            "dev AES key must decode to exactly 32 bytes");
        ClaimValueCipher c = new ClaimValueCipher(devKey);
        assertEquals("2500000", c.open(c.seal("2500000")));
    }

    @Test
    void theShippedDevelopmentDefaultHmacAndJwtKeysAreThirtyTwoBytes() {
        // AES derived a key, but HMAC (AadhaarVault) and the JWT HS256 secret are used
        // as raw key material, so their length is not silently normalised.
        assertEquals(32, Base64.getDecoder().decode(
            "ZGV2LW9ubHktaG1hYy1rZXktMzItYnl0ZXMtbG9uZyE=").length, "AADHAAR dev key");
        assertEquals(32, Base64.getDecoder().decode(
            "ZGV2LW9ubHktand0LXNlY3JldC0zMi1ieXRlcy1sb24=").length, "JWT dev key");
    }
}
