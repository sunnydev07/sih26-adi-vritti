package in.adivritti.core.eligibility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Generic rule evaluator over the Claims Wallet.
 * Operators: in, lte, gte, eq, exists. Rules are DATA (packages/rules/*.json),
 * never code — a ceiling change is a config edit, not a release.
 *
 * <p>Rules arrive as untyped JSON, so every operator is defensive: a malformed rule
 * produces an explicit {@code IllegalArgumentException} naming the claim, rather
 * than an NPE or a {@code ClassCastException} surfacing as a 500.
 */
@Component
public class RuleEngine {

    public record RuleOutcome(boolean passed, String reason, String missingClaim) {}

    public List<RuleOutcome> evaluate(List<Map<String, Object>> rules, Map<String, Object> claims) {
        if (rules == null) return List.of();
        Map<String, Object> effectiveClaims = claims == null ? Map.of() : claims;
        List<RuleOutcome> outcomes = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            outcomes.add(evaluateOne(i, rules.get(i), effectiveClaims));
        }
        return outcomes;
    }

    private RuleOutcome evaluateOne(int index, Map<String, Object> rule,
        Map<String, Object> claims) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule at index " + index + " is null");
        }
        String claim = requireString(rule, "claim", index);
        String op = requireString(rule, "op", index);
        Object expected = rule.get("value");
        Object actual = claims.get(claim);

        boolean passed = switch (op) {
            case "exists" -> actual != null;
            case "eq" -> actual != null && valuesEqual(actual, expected);
            case "in" -> actual != null && expected instanceof Collection<?> allowed
                && containsValue(allowed, actual);
            case "lte" -> actual != null && compareNumbers(actual, expected) <= 0;
            case "gte" -> actual != null && compareNumbers(actual, expected) >= 0;
            case "not_null" -> actual != null;
            default -> throw new IllegalArgumentException(
                "Unknown rule op '" + op + "' at index " + index + " (claim " + claim + ")");
        };

        String onFail = rule.get("onFail") instanceof String s ? s
            : "Requirement '" + claim + "' was not met.";
        return new RuleOutcome(passed, passed ? null : onFail, passed ? null : claim);
    }

    private static String requireString(Map<String, Object> rule, String key, int index) {
        Object value = rule.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                "Rule at index " + index + " is missing a non-blank '" + key + "'");
        }
        return s;
    }

    /**
     * Numeric-aware equality. Jackson decodes small integers as Integer and large
     * ones as Long, so a plain {@code equals} compared {@code 10} against {@code 10L}
     * and rejected a valid class-level match.
     */
    static boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        if (isNumeric(actual) && isNumeric(expected)) {
            return toDecimal(actual).compareTo(toDecimal(expected)) == 0;
        }
        if (actual instanceof String a && expected instanceof String b) {
            return a.equalsIgnoreCase(b);
        }
        return actual.equals(expected);
    }

    private static boolean containsValue(Collection<?> allowed, Object actual) {
        for (Object candidate : allowed) {
            if (valuesEqual(actual, candidate)) return true;
        }
        return false;
    }

    private static int compareNumbers(Object actual, Object expected) {
        if (!isNumeric(actual)) {
            throw new IllegalArgumentException("Claim value is not numeric: " + actual);
        }
        if (!isNumeric(expected)) {
            throw new IllegalArgumentException("Rule threshold is not numeric: " + expected);
        }
        return toDecimal(actual).compareTo(toDecimal(expected));
    }

    private static boolean isNumeric(Object o) {
        return o instanceof Number || o instanceof BigDecimal;
    }

    private static BigDecimal toDecimal(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        throw new IllegalArgumentException("Not numeric: " + o);
    }
}
