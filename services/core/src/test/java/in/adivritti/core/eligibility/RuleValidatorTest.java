package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-001 startup validation: rule files must satisfy
 * {@code packages/rules/schema.json}, and violations must name the file.
 */
class RuleValidatorTest {

    /** Resolves from the services/core working directory, like the default config. */
    private static final String REAL_RULES = "../../packages/rules";

    @Test
    @DisplayName("the shipped rule files validate against the shipped schema")
    void shippedRulesValidate() {
        Map<String, JsonNode> docs = new RuleValidator(REAL_RULES).validateAll();

        assertThat(docs).containsKeys("2026-27/pre-matric", "2026-27/post-matric",
            "2026-27/top-class", "2026-27/nfst", "2026-27/nos");
    }

    @Test
    @DisplayName("a rule file missing a required key fails with the file named")
    void brokenRuleFailsNamed(@TempDir Path root) throws Exception {
        Path real = Path.of(REAL_RULES).toAbsolutePath().normalize();
        Files.copy(real.resolve("schema.json"), root.resolve("schema.json"));
        Path year = root.resolve("2026-27");
        Files.createDirectory(year);
        Files.writeString(year.resolve("pre-matric.json"),
            """
            {"scheme": "PRE_MATRIC", "academic_year": "2026-27",
             "category": "welfare", "ministry": "MoTA", "portal": "NSP",
             "award": {}},
            """,
            StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new RuleValidator(root.toString()).validateAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("2026-27/pre-matric");
    }

    @Test
    @DisplayName("an unknown rule op fails validation")
    void unknownOpFails(@TempDir Path root) throws Exception {
        Path real = Path.of(REAL_RULES).toAbsolutePath().normalize();
        Files.copy(real.resolve("schema.json"), root.resolve("schema.json"));
        Path year = root.resolve("2026-27");
        Files.createDirectory(year);
        Files.writeString(year.resolve("nos.json"),
            """
            {"scheme": "NOS", "scheme_name": "N", "academic_year": "2026-27",
             "category": "welfare", "ministry": "MoTA", "portal": "NOS",
             "rules": [{"claim": "x", "op": "teleport", "onFail": "no"}],
             "award": {}},
            """,
            StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new RuleValidator(root.toString()).validateAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("2026-27/nos");
    }

    @Test
    @DisplayName("a missing schema is a startup failure, not a silent skip")
    void missingSchemaFailsFast(@TempDir Path root) {
        assertThatThrownBy(() -> new RuleValidator(root.toString()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("schema");
    }
}
