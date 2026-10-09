package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * The {@code scheme_rule_version} mirror is a real fallback reader: when the
 * disk file is gone, eligibility evaluates the startup-validated mirror row
 * instead of reporting the scheme's rules as unpublished.
 */
class EligibilityMirrorFallbackTest {

    @Test
    @DisplayName("missing disk file falls back to the mirror row")
    void mirrorFallback(@TempDir Path root) {
        RuleEngine engine = mock(RuleEngine.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        SchemeRuleVersionRepository mirror = mock(SchemeRuleVersionRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        SchemeRuleVersion row = new SchemeRuleVersion();
        row.scheme = "PRE_MATRIC";
        row.academicYear = "2026-27";
        row.rulesJson = Map.of("scheme", "PRE_MATRIC", "academic_year", "2026-27",
            "rules", List.of(Map.of("claim", "income", "op", "exists", "onFail", "no income")));
        when(mirror.findBySchemeIgnoreCaseAndAcademicYear("pre-matric", "2026-27"))
            .thenReturn(Optional.of(row));
        when(engine.evaluate(any(), anyMap()))
            .thenReturn(List.of(new RuleOutcome(true, "", "")));

        EligibilityService service = new EligibilityService(engine, claims,
            mock(ClaimValueCipher.class), mirror,
            root.resolve("empty-rules").toString());
        EligibilityResponse response = service.evaluate(
            new EligibilityRequest(UUID.randomUUID(), "2026-27"));

        assertThat(response.verdicts()).hasSize(5);
        assertThat(response.verdicts().get(0).verdict()).isEqualTo("eligible");
        assertThat(response.verdicts().subList(1, 5))
            .allMatch(v -> v.verdict().equals("missing_items"));
        // Only the mirrored scheme reaches the engine; the rest report missing.
        ArgumentCaptor<List<Map<String, Object>>> rules = ArgumentCaptor.forClass(List.class);
        verify(engine, times(1)).evaluate(rules.capture(), anyMap());
        assertThat(rules.getValue()).hasSize(1);
        assertThat(rules.getValue().get(0)).containsEntry("claim", "income");
    }

    @Test
    @DisplayName("no disk file and no mirror row still reports rules as unpublished")
    void noMirrorStillMissing(@TempDir Path root) {        RuleEngine engine = mock(RuleEngine.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        SchemeRuleVersionRepository mirror = mock(SchemeRuleVersionRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        when(mirror.findBySchemeIgnoreCaseAndAcademicYear(any(), any()))
            .thenReturn(Optional.empty());

        EligibilityService service = new EligibilityService(engine, claims,
            mock(ClaimValueCipher.class), mirror,
            root.resolve("empty-rules").toString());
        EligibilityResponse response = service.evaluate(
            new EligibilityRequest(UUID.randomUUID(), "2026-27"));

        assertThat(response.verdicts()).allMatch(v -> v.verdict().equals("missing_items"));
    }

    @Test
    @DisplayName("mirror row stored as PRE_MATRIC is found through the pre-matric request")
    void mirrorFallbackBridgesHyphenAndUnderscore(@TempDir Path root) {
        // Rule files author PRE_MATRIC while the request path uses pre-matric,
        // and IgnoreCase folds case but not separators: only the underscore
        // spelling is stubbed here, so an exact-match-only lookup would miss.
        RuleEngine engine = mock(RuleEngine.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        SchemeRuleVersionRepository mirror = mock(SchemeRuleVersionRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        SchemeRuleVersion row = new SchemeRuleVersion();
        row.scheme = "PRE_MATRIC";
        row.academicYear = "2026-27";
        row.rulesJson = Map.of("scheme", "PRE_MATRIC", "academic_year", "2026-27",
            "rules", List.of(Map.of("claim", "income", "op", "exists", "onFail", "no income")));
        when(mirror.findBySchemeIgnoreCaseAndAcademicYear("pre_matric", "2026-27"))
            .thenReturn(Optional.of(row));
        when(engine.evaluate(any(), anyMap()))
            .thenReturn(List.of(new RuleOutcome(true, "", "")));

        EligibilityService service = new EligibilityService(engine, claims,
            mock(ClaimValueCipher.class), mirror,
            root.resolve("empty-rules").toString());
        EligibilityResponse response = service.evaluate(
            new EligibilityRequest(UUID.randomUUID(), "2026-27"));

        assertThat(response.verdicts().get(0).verdict()).isEqualTo("eligible");
    }
}
