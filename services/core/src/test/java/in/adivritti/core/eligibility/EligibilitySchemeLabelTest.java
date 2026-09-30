package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.RuleEngine.RuleOutcome;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.entity.SchemeRuleVersion;
import in.adivritti.core.eligibility.repository.SchemeRuleVersionRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The verdict label keeps the scheme key as requested (lowercase, matching the
 * rules filenames and {@code application.scheme}). It was previously uppercased,
 * so the dashboard keyed its verdict map by {@code PRE-MATRIC} while looking it
 * up with {@code pre-matric} — every scheme rendered eligibility
 * {@code unknown}.
 */
class EligibilitySchemeLabelTest {

    @Test
    @DisplayName("verdict scheme matches the lowercase scheme key")
    void verdictLabelIsLowercase(@TempDir Path root) {
        RuleEngine engine = mock(RuleEngine.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        SchemeRuleVersionRepository mirror = mock(SchemeRuleVersionRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        SchemeRuleVersion row = new SchemeRuleVersion();
        row.scheme = "pre-matric";
        row.academicYear = "2026-27";
        row.rulesJson = """
            {"scheme": "pre-matric", "academic_year": "2026-27",
             "rules": [{"claim": "income", "op": "exists", "onFail": "no income"}]}
            """;
        when(mirror.findBySchemeIgnoreCaseAndAcademicYear("pre-matric", "2026-27"))
            .thenReturn(Optional.of(row));
        when(engine.evaluate(any(), anyMap()))
            .thenReturn(List.of(new RuleOutcome(true, "", "")));

        EligibilityService service = new EligibilityService(engine, claims,
            mock(ClaimValueCipher.class), mirror,
            root.resolve("empty-rules").toString());
        EligibilityResponse response = service.evaluate(
            new EligibilityRequest(UUID.randomUUID(), "2026-27"));

        assertThat(response.verdicts().get(0).scheme()).isEqualTo("pre-matric");
        assertThat(response.verdicts().get(0).verdict()).isEqualTo("eligible");
    }
}
