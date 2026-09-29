package in.adivritti.core.disbursement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class FailureDecoderTest {

    private final FailureDecoder decoder = new FailureDecoder();

    @Test
    void allTaxonomyCodesDecode() {
        for (String code : List.of("E001_AADHAAR_NOT_SEEDED", "E002_ACCOUNT_DORMANT",
            "E003_IFSC_CHANGED", "E004_NAME_MISMATCH", "E005_FUNDS_NOT_RELEASED", "E006_OTHER")) {
            var d = decoder.decode(code);
            assertNotNull(d.cause(), code);
            assertNotNull(d.fix(), code);
        }
    }

    @Test
    void unknownCodeFallsBack() {
        assertEquals(true, decoder.decode("E999_NOPE").cause().contains("Unmapped"));
    }

    @Test
    void nullCodeFallsBackToOtherInsteadOfNulls() {
        // decode(null) used to return Decoded(null, null), which NPE'd every
        // downstream consumer that renders the cause and the fix.
        var d = decoder.decode(null);
        assertNotNull(d.cause());
        assertNotNull(d.fix());
        assertEquals("Other / technical failure", d.cause());
    }

    @Test
    void blankCodeFallsBackToOther() {
        assertNotNull(decoder.decode("").cause());
        assertNotNull(decoder.decode("   ").cause());
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertEquals("Aadhaar not seeded with bank account",
            decoder.decode("  E001_AADHAAR_NOT_SEEDED  ").cause());
    }
}
