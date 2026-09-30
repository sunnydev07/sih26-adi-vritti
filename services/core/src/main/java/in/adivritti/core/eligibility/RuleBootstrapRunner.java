package in.adivritti.core.eligibility;

import com.fasterxml.jackson.databind.JsonNode;
import in.adivritti.core.eligibility.entity.SchemeRuleVersion;
import in.adivritti.core.eligibility.repository.SchemeRuleVersionRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-001 startup gate: validate every rule file against
 * {@code packages/rules/schema.json}, then mirror the validated documents into
 * {@code scheme_rule_version}.
 *
 * <p>Any violation throws, which fails application startup: the service refuses
 * to serve eligibility verdicts from rules nobody validated. The mirror is an
 * upsert keyed by (scheme, academic_year), so restarts pick up rule edits and
 * re-deployments never duplicate rows.
 */
@Component
public class RuleBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleBootstrapRunner.class);

    private final RuleValidator validator;
    private final SchemeRuleVersionRepository mirror;

    public RuleBootstrapRunner(RuleValidator validator, SchemeRuleVersionRepository mirror) {
        this.validator = validator;
        this.mirror = mirror;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, JsonNode> docs = validator.validateAll();
        int written = 0;
        for (JsonNode doc : docs.values()) {
            String scheme = doc.path("scheme").asText(null);
            String year = doc.path("academic_year").asText(null);
            if (scheme == null || scheme.isBlank() || year == null || year.isBlank()) {
                // Unreachable for schema-valid documents (both are required), but
                // a hand-rolled schema edit upstream must not NPE the boot path.
                throw new IllegalStateException(
                    "Validated rule document is missing scheme/academic_year");
            }
            SchemeRuleVersion row = mirror
                .findBySchemeIgnoreCaseAndAcademicYear(scheme, year)
                .orElseGet(SchemeRuleVersion::new);
            row.scheme = scheme;
            row.academicYear = year;
            row.rulesJson = doc.toString();
            mirror.save(row);
            written++;
        }
        log.info("Mirrored {} validated rule files into scheme_rule_version", written);
    }
}
