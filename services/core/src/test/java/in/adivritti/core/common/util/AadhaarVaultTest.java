package in.adivritti.core.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AadhaarVaultTest {

    private static final String KEY_32 = key(32);
    private static final String KEY_SHORT = key(8);

    private static String key(int bytes) {
        byte[] raw = new byte[bytes];
        java.util.Arrays.fill(raw, (byte) 7);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static AadhaarVault vault(String base64Key) {
        return new AadhaarVault(base64Key, "v1", true);
    }

    @Test
    void referenceKeyIsStableForTheSameInput() {
        String a = vault(KEY_32).referenceKey("999999990019");
        String b = vault(KEY_32).referenceKey("999999990019");
        assertEquals(a, b);
    }

    @Test
    void referenceKeyIsSaltedByTheServerSecret() {
        // Two vaults with different secrets must not produce the same key, or the
        // reference key would be reversible by anyone who guesses an Aadhaar.
        byte[] other = new byte[32];
        java.util.Arrays.fill(other, (byte) 9);
        String a = vault(KEY_32).referenceKey("999999990019");
        String b = vault(Base64.getEncoder().encodeToString(other))
            .referenceKey("999999990019");
        assertNotEquals(a, b);
    }

    @Test
    void referenceKeyHasVersionedShapeAndFullDigest() {
        String ref = vault(KEY_32).referenceKey("999999990019");
        assertTrue(ref.startsWith("AVR1:v1:"), ref);
        String hex = ref.substring("AVR1:v1:".length());
        // The old implementation truncated to 16 hex chars (64 bits).
        assertEquals(64, hex.length(), "expected the full 256-bit digest, got " + hex.length());
        assertTrue(hex.matches("[0-9a-f]+"), hex);
    }

    @Test
    void referenceKeyNeverContainsTheAadhaarDigits() {
        String ref = vault(KEY_32).referenceKey("999999990019");
        assertFalse(ref.contains("999999990019"), "reference key leaked the Aadhaar");
        assertFalse(ref.toLowerCase().contains("9999"), "reference key leaked a fragment");
    }

    @Test
    void inputNormalisationStripsSpacesAndDashes() {
        AadhaarVault v = vault(KEY_32);
        assertEquals(v.referenceKey("9999 9999 0019"), v.referenceKey("999999990019"));
        assertEquals(v.referenceKey("9999-9999-0019"), v.referenceKey("999999990019"));
    }

    @Test
    void malformedInputIsRejectedWithoutEchoingTheValue() {
        AadhaarVault v = vault(KEY_32);
        assertThrows(IllegalArgumentException.class, () -> v.referenceKey("12345"));
        assertThrows(IllegalArgumentException.class, () -> v.referenceKey("not-a-number"));
        assertThrows(IllegalArgumentException.class, () -> v.referenceKey(null));
        assertThrows(IllegalArgumentException.class, () -> v.referenceKey("9999999900191234"));
    }

    @Test
    void badChecksumIsRejectedWhenEnforced() {
        AadhaarVault strict = new AadhaarVault(KEY_32, "v1", true);
        assertThrows(IllegalArgumentException.class,
            () -> strict.referenceKey("999999990018"),
            "a bad Verhoeff check digit must be rejected");
    }

    @Test
    void badChecksumIsToleratedWhenNotEnforced() {
        // Seeded synthetic data is not Verhoeff-valid, so dev can relax the gate.
        AadhaarVault lenient = new AadhaarVault(KEY_32, "v1", false);
        assertTrue(lenient.referenceKey("999999990018").startsWith("AVR1:"));
    }

    @Test
    void missingOrWeakKeyAbortsConstruction() {
        assertThrows(IllegalStateException.class, () -> new AadhaarVault(null, "v1", true));
        assertThrows(IllegalStateException.class, () -> new AadhaarVault("", "v1", true));
        assertThrows(IllegalStateException.class, () -> vault(KEY_SHORT));
        assertThrows(IllegalStateException.class, () -> vault("not-base64!!"));
    }

    @Test
    void keyIdIsValidatedSoItCannotBreakTheKeyFormat() {
        assertThrows(IllegalStateException.class, () -> new AadhaarVault(KEY_32, "bad id", true));
        assertThrows(IllegalStateException.class,
            () -> new AadhaarVault(KEY_32, "with:colon", true));
    }

    @Test
    void maskedDisplayExposesOnlyLastFour() {
        AadhaarVault v = vault(KEY_32);
        assertEquals("0019", v.last4("999999990019"));
        assertEquals("XXXX-XXXX-0019", v.masked("999999990019"));
    }

    @Test
    void toStringDoesNotLeakTheKey() {
        String s = vault(KEY_32).toString();
        assertFalse(s.contains(KEY_32), "toString leaked the secret");
        assertTrue(s.contains("v1"), s);
    }

    @Test
    void isReferenceKeyRecognisesItsOwnOutput() {
        AadhaarVault v = vault(KEY_32);
        String ref = v.referenceKey("999999990019");
        assertTrue(v.isReferenceKey(ref));
        assertFalse(v.isReferenceKey("999999990019"));
    }

    // ------------------------------------------------------------- Verhoeff

    @Test
    void verhoeffAcceptsAKnownValidNumber() {
        assertTrue(AadhaarVault.verhoeffValid("999999990019"));
    }

    @Test
    void verhoeffRejectsAWrongCheckDigit() {
        assertFalse(AadhaarVault.verhoeffValid("999999990018"));
        assertFalse(AadhaarVault.verhoeffValid("999999990010"));
    }

    @Test
    void verhoeffRejectsWrongLength() {
        assertFalse(AadhaarVault.verhoeffValid("12345"));
        assertFalse(AadhaarVault.verhoeffValid("9999999900199999"));
        assertFalse(AadhaarVault.verhoeffValid(""));
    }
}
