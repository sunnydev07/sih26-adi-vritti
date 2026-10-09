package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.entity.SchemeRuleVersion;
import in.adivritti.core.eligibility.repository.SchemeRuleVersionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An empty rule set must fail closed. Previously only a <em>missing</em> rule set
 * was rejected, so an empty-but-present {@code "rules": []} evaluated zero
 * outcomes and returned {@code eligible} for every student — a malformed or
 * truncated rules file granted every scholarship in the system.
 */
class EligibilityEmptyRulesFailClosedTest {

    @Test
    @DisplayName("empty rules file on disk reports missing and never reaches the engine")
    void emptyDiskFileFailsClosed(@TempDir Path root) throws Exception {
        Path rulesRoot = root.resolve("rules");
        Files.createDirectories(rulesRoot.resolve("2026-27"));
        Files.writeString(rulesRoot.resolve("2026-27/pre-matric.json"),
            """
                {"scheme": "pre-matric", "academic_year": "2026-27", "rules": []}
                """,
            StandardCharsets.UTF_8);

        RuleEngine engine = mock(RuleEngine.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        SchemeRuleVersionRepository mirror = mock(SchemeRuleVersionRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        when(mirror.findBySchemeIgnoreCaseAndAcademicYear(any(), any()))
            .thenReturn(Optional.empty());

        EligibilityService service = new EligibilityService(engine, claims,
            mock(ClaimValueCipher.class), mirror, rulesRoot.toString());
        EligibilityResponse response = service.evaluate(
            new EligibilityRequest(UUID.randomUUID(), "2026-27"));

        assertThat(response.verdicts()).allMatch(v -> v.verdict().equals("missing_items"));
        assertThat(response.verdicts().get(0).reasons())
            .containsExactly("Rules are not published for 2026-27");
        verify(engine, never()).evaluate(any(), anyMap());
    }

    @Test
    @DisplayName("empty rules in the mirror row reports missing and never reaches the engine")
    void emptyMirrorRowFailsClosed(@TempDir Path root) {
        RuleEngine engine = mock(RuleEngine.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        SchemeRuleVersionRepository mirror = mock(SchemeRuleVersionRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        SchemeRuleVersion row = new SchemeRuleVersion();
        row.scheme = "pre-matric";
        row.academicYear = "2026-27";
        row.rulesJson = Map.of("scheme", "pre-matric", "academic_year", "2026-27",
            "rules", List.of());
        when(mirror.findBySchemeIgnoreCaseAndAcademicYear("pre-matric", "2026-27"))
            .thenReturn(Optional.of(row));

        EligibilityService service = new EligibilityService(engine, claims,
            mock(ClaimValueCipher.class), mirror,
            root.resolve("empty-rules").toString());
        EligibilityResponse response = service.evaluate(
            new EligibilityRequest(UUID.randomUUID(), "2026-27"));

        assertThat(response.verdicts()).allMatch(v -> v.verdict().equals("missing_items"));
        verify(engine, never()).evaluate(any(), anyMap());
    }
}
