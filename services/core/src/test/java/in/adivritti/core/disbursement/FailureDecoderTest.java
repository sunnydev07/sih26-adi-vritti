package in.adivritti.core.disbursement;

import static org.junit.jupiter.api.Assertions.*;
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
        assertTrue(decoder.decode("E999_NOPE").cause().contains("Unmapped"));
    }
}
