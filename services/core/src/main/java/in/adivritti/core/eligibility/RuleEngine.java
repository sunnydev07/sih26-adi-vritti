package in.adivritti.core.eligibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Generic rule evaluator over the Claims Wallet.
 * Operators: in, lte, gte, eq, exists. Rules are DATA (packages/rules/*.json),
 * never code — a ceiling change is a config edit, not a release.
 */
@Component
public class RuleEngine {

    public record RuleOutcome(boolean passed, String reason, String missingClaim) {}

    @SuppressWarnings("unchecked")
    public List<RuleOutcome> evaluate(List<Map<String, Object>> rules, Map<String, Object> claims) {
        List<RuleOutcome> outcomes = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            String claim = (String) rule.get("claim");
            String op = (String) rule.get("op");
            Object expected = rule.get("value");
            String onFail = (String) rule.get("onFail");
            Object actual = claims.get(claim);
            boolean passed = switch (op) {
                case "exists" -> actual != null;
                case "eq" -> actual != null && actual.equals(expected);
                case "in" -> actual != null && ((List<Object>) expected).contains(actual);
                case "lte" -> actual != null && compare(actual, expected) <= 0;
                case "gte" -> actual != null && compare(actual, expected) >= 0;
                default -> throw new IllegalArgumentException("Unknown rule op: " + op);
            };
            outcomes.add(new RuleOutcome(passed, passed ? null : onFail, passed ? null : claim));
        }
        return outcomes;
    }

    private static int compare(Object actual, Object expected) {
        return Long.compare(((Number) actual).longValue(), ((Number) expected).longValue());
    }
}
