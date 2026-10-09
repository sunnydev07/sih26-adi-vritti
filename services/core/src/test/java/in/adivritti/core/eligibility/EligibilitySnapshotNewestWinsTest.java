package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.repository.SchemeRuleVersionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * When a scholar re-verifies a claim, the wallet holds several live rows of
 * the same type. The snapshot query orders newest-first, and the snapshot must
 * keep the first row it sees: keeping the last one evaluated a re-verified
 * income at its stale figure — newly eligible rendering as not eligible, or
 * newly over-income rendering as eligible.
 */
class EligibilitySnapshotNewestWinsTest {

    private static final String TYPE = "family_income_annual_paise";

    private static final String RULES = """
        {"scheme": "PRE_MATRIC", "academic_year": "2026-27",
         "rules": [{"claim": "family_income_annual_paise", "op": "lte",
                    "value": 25000000, "onFail": "income must not exceed 25000000 paise"}]}
        """;

    private Claim claim(byte marker) {
        Claim c = new Claim();
        c.valueEncrypted = new byte[] {marker};
        return c;
    }

    private EligibilityResponse evaluate(Path root, String newest, String oldest)
        throws Exception {
        Path dir = root.resolve("2026-27");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pre-matric.json"), RULES, StandardCharsets.UTF_8);

        ClaimRepository claims = mock(ClaimRepository.class);
        // Query order is verifiedAt DESC: newest row first.
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of(
            new Object[] {TYPE, claim((byte) 1)},
            new Object[] {TYPE, claim((byte) 2)}));

        ClaimValueCipher cipher = mock(ClaimValueCipher.class);
        when(cipher.open(argThat(b -> b != null && b.length == 1 && b[0] == 1)))
            .thenReturn(newest);
        when(cipher.open(argThat(b -> b != null && b.length == 1 && b[0] == 2)))
            .thenReturn(oldest);

        EligibilityService service = new EligibilityService(new RuleEngine(), claims,
            cipher, mock(SchemeRuleVersionRepository.class), root.toString());
        return service.evaluate(new EligibilityRequest(UUID.randomUUID(), "2026-27"));
    }

    @Test
    @DisplayName("a re-verified lower income evaluates as eligible, not at the stale figure")
    void newestValueWins(@TempDir Path root) throws Exception {
        EligibilityResponse response = evaluate(root, "20000000", "30000000");

        assertThat(response.verdicts().get(0).scheme()).isEqualTo("pre-matric");
        assertThat(response.verdicts().get(0).verdict()).isEqualTo("eligible");
    }

    @Test
    @DisplayName("a re-verified higher income evaluates as not eligible, not at the stale figure")
    void newestHighValueWins(@TempDir Path root) throws Exception {
        EligibilityResponse response = evaluate(root, "30000000", "20000000");

        assertThat(response.verdicts().get(0).verdict()).isEqualTo("not_eligible");
    }
}
