package in.adivritti.core.eligibility;

import static org.junit.jupiter.api.Assertions.*;
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
}
