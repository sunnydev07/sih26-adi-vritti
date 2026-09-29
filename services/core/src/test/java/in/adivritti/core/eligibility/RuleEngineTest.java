package in.adivritti.core.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void allOperatorsPass() {
        var rules = List.of(
            Map.<String, Object>of("claim", "a", "op", "in", "value", List.of("ST", "PVTG"), "onFail", "no"),
            Map.<String, Object>of("claim", "b", "op", "lte", "value", 25000000, "onFail", "no"),
            Map.<String, Object>of("claim", "c", "op", "gte", "value", 9, "onFail", "no"),
            Map.<String, Object>of("claim", "d", "op", "eq", "value", "enrolled", "onFail", "no"),
            Map.<String, Object>of("claim", "e", "op", "exists", "onFail", "no"));
        var claims = Map.<String, Object>of("a", "ST", "b", 20000000, "c", 10, "d", "enrolled", "e", true);
        assertTrue(engine.evaluate(rules, claims).stream().allMatch(RuleEngine.RuleOutcome::passed));
    }

    @Test
    void failureCarriesReasonAndMissingClaim() {
        var rules = List.of(Map.<String, Object>of("claim", "class_level", "op", "in",
            "value", List.of(9, 10), "onFail", "Pre-Matric covers Class IX and X only."));
        var outcomes = engine.evaluate(rules, Map.of("class_level", 12));
        assertFalse(outcomes.get(0).passed());
        assertEquals("Pre-Matric covers Class IX and X only.", outcomes.get(0).reason());
        assertEquals("class_level", outcomes.get(0).missingClaim());
    }

    @Test
    void numericEqualityIsTypeAgnostic() {
        // Jackson decodes 10 as Integer and 25000000 as Integer, but a large claim
        // value arrives as Long. Plain equals() rejected Integer 10 vs Long 10.
        assertTrue(RuleEngine.valuesEqual(10, 10L));
        assertTrue(RuleEngine.valuesEqual(25000000L, 25000000));
        assertFalse(RuleEngine.valuesEqual(10, 11L));
    }

    @Test
    void inOperatorMatchesAcrossNumericTypes() {
        var rules = List.of(Map.<String, Object>of("claim", "class_level", "op", "in",
            "value", List.of(9, 10), "onFail", "no"));
        assertTrue(engine.evaluate(rules, Map.of("class_level", 10L)).get(0).passed());
    }

    @Test
    void inOperatorRejectsNonListThresholdInsteadOfClassCastException() {
        var rules = List.of(Map.<String, Object>of("claim", "a", "op", "in",
            "value", "ST", "onFail", "no"));
        var outcome = engine.evaluate(rules, Map.of("a", "ST")).get(0);
        assertFalse(outcome.passed(), "a non-list threshold must not match");
    }

    @Test
    void comparisonRejectsNonNumericClaimWithAClearMessage() {
        var rules = List.of(Map.<String, Object>of("claim", "a", "op", "lte",
            "value", 100, "onFail", "no"));
        var e = assertThrows(IllegalArgumentException.class,
            () -> engine.evaluate(rules, Map.of("a", "not-a-number")));
        assertTrue(e.getMessage().contains("not numeric"), e.getMessage());
    }

    @Test
    void unknownOperatorIsRejectedWithRuleContext() {
        var rules = List.of(Map.<String, Object>of("claim", "a", "op", "matches_regex",
            "value", "x", "onFail", "no"));
        var e = assertThrows(IllegalArgumentException.class,
            () -> engine.evaluate(rules, Map.of("a", "x")));
        assertTrue(e.getMessage().contains("matches_regex"), e.getMessage());
        assertTrue(e.getMessage().contains("claim a"), e.getMessage());
    }

    @Test
    void missingClaimOrOpIsRejectedWithIndex() {
        var noOp = List.of(Map.<String, Object>of("claim", "a", "onFail", "no"));
        var e1 = assertThrows(IllegalArgumentException.class,
            () -> engine.evaluate(noOp, Map.of("a", "x")));
        assertTrue(e1.getMessage().contains("index 0"), e1.getMessage());

        var noClaim = List.of(Map.<String, Object>of("op", "eq", "value", 1, "onFail", "no"));
        var e2 = assertThrows(IllegalArgumentException.class,
            () -> engine.evaluate(noClaim, Map.of("a", "x")));
        assertTrue(e2.getMessage().contains("claim"), e2.getMessage());
    }

    @Test
    void nullRulesOrClaimsAreTolerated() {
        assertTrue(engine.evaluate(null, Map.of()).isEmpty());
        assertTrue(engine.evaluate(List.of(), null).isEmpty());
    }

    @Test
    void absentClaimFailsWithoutThrowing() {
        var rules = List.of(Map.<String, Object>of("claim", "income", "op", "lte",
            "value", 100, "onFail", "too high"));
        var outcome = engine.evaluate(rules, Map.of()).get(0);
        assertFalse(outcome.passed());
        assertEquals("too high", outcome.reason());
    }

    @Test
    void ruleWithoutOnFailGetsAReadableDefaultReason() {
        var rules = List.of(Map.<String, Object>of("claim", "income", "op", "exists"));
        var outcome = engine.evaluate(rules, Map.of()).get(0);
        assertFalse(outcome.passed());
        assertTrue(outcome.reason().contains("income"), outcome.reason());
    }
}
