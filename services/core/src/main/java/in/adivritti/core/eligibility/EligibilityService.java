package in.adivritti.core.eligibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse.SchemeVerdict;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EligibilityService {

    private final RuleEngine engine;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String rulesPath;

    private static final List<String> SCHEMES =
        List.of("pre-matric", "post-matric", "top-class", "nfst", "nos");

    public EligibilityService(RuleEngine engine,
        @Value("${app.rules-path:../../packages/rules}") String rulesPath) {
        this.engine = engine;
        this.rulesPath = rulesPath;
    }

    public EligibilityResponse evaluate(EligibilityRequest req) {
        String year = req.academicYear() != null ? req.academicYear() : "2026-27";
        // Claims wallet snapshot for this USID (repository-backed in full wiring).
        Map<String, Object> claims = Map.of();
        List<SchemeVerdict> verdicts = new ArrayList<>();
        for (String scheme : SCHEMES) {
            verdicts.add(evaluateScheme(scheme, year, claims));
        }
        // Compatibility matrix enforcement (merit + welfare) happens here in full wiring.
        return new EligibilityResponse(req.usid(), year, verdicts);
    }

    private SchemeVerdict evaluateScheme(String scheme, String year, Map<String, Object> claims) {
        try {
            Path file = Path.of(rulesPath, year, scheme + ".json");
            if (!Files.exists(file)) {
                return new SchemeVerdict(scheme.toUpperCase(), "missing_items",
                    List.of("Rules not loaded for " + scheme), List.of());
            }
            Map<String, Object> doc = mapper.readValue(file.toFile(), new TypeReference<>() {});
            List<Map<String, Object>> rules = (List<Map<String, Object>>) doc.get("rules");
            var outcomes = engine.evaluate(rules, claims);
            List<String> reasons = outcomes.stream()
                .filter(o -> !o.passed()).map(RuleEngine.RuleOutcome::reason).toList();
            List<String> missing = outcomes.stream()
                .filter(o -> !o.passed()).map(RuleEngine.RuleOutcome::missingClaim).toList();
            if (reasons.isEmpty()) return new SchemeVerdict(scheme.toUpperCase(), "eligible", List.of(), List.of());
            if (missing.stream().anyMatch(m -> claims.get(m) == null)) {
                return new SchemeVerdict(scheme.toUpperCase(), "missing_items", reasons, missing);
            }
            return new SchemeVerdict(scheme.toUpperCase(), "not_eligible", reasons, missing);
        } catch (Exception e) {
            return new SchemeVerdict(scheme.toUpperCase(), "missing_items",
                List.of("Could not evaluate: " + e.getMessage()), List.of());
        }
    }
}
