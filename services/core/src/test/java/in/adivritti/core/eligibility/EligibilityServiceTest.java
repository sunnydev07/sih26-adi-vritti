package in.adivritti.core.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EligibilityServiceTest {

    @Test
    void numericClaimValuesAreCoercedToNumbers() {
        // Claim values arrive as text; a rule threshold of 25000000 (a JSON number)
        // must compare numerically, not fail on a String/Number mismatch.
        assertEquals(9, EligibilityService.coerce("9"));
        assertEquals(12, EligibilityService.coerce(" 12 "));
        assertInstanceOf(Integer.class, EligibilityService.coerce("10"));
    }

    @Test
    void largeValuesBecomeLongs() {
        // Beyond Integer.MAX_VALUE the value must widen to Long, which is what
        // keeps income ceilings in the rules JSON (written as large numbers)
        // comparing correctly against stored claim values.
        assertInstanceOf(Integer.class, EligibilityService.coerce("25000000"));
        assertInstanceOf(Long.class, EligibilityService.coerce("5000000000"));
        assertEquals(5_000_000_000L, EligibilityService.coerce("5000000000"));
    }

    @Test
    void booleansAreCoerced() {
        assertEquals(Boolean.TRUE, EligibilityService.coerce("true"));
        assertEquals(Boolean.FALSE, EligibilityService.coerce("false"));
    }

    @Test
    void nonNumericValuesStayAsStrings() {
        assertEquals("ST", EligibilityService.coerce("ST"));
        assertEquals("enrolled", EligibilityService.coerce(" enrolled "));
        // A value that is mostly digits but not a number must not be truncated.
        assertEquals("SCH-0001", EligibilityService.coerce("SCH-0001"));
    }

    @Test
    void oversizedNumbersAreLeftAsTextRatherThanOverflowing() {
        assertEquals("999999999999999999999", EligibilityService.coerce("999999999999999999999"));
    }

    @Test
    void validAcademicYearIsAccepted() {
        assertEquals("2026-27", EligibilityService.normalizeYear("2026-27"));
        assertEquals("2026-27", EligibilityService.normalizeYear("  2026-27  "));
    }

    @Test
    void missingAcademicYearFallsBackToTheDefault() {
        assertEquals("2026-27", EligibilityService.normalizeYear(null));
        assertEquals("2026-27", EligibilityService.normalizeYear(""));
        assertEquals("2026-27", EligibilityService.normalizeYear("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../../etc",
        "..\\..\\windows",
        "2026-27/../../..",
        "2026-27/passwd",
        "/etc/passwd",
        "202627",
        "2026_27",
        "2026-27;drop table scholar",
    })
    void academicYearRejectsPathTraversalAndMalformedInput(String evil) {
        // academicYear is concatenated into Path.of(rulesPath, year, scheme + ".json").
        assertThrows(IllegalArgumentException.class,
            () -> EligibilityService.normalizeYear(evil));
    }
}
