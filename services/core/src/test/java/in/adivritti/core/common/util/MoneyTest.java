package in.adivritti.core.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void wholeRupeesHaveNoFractionNoise() {
        assertEquals("₹0.00", Money.formatPaise(0));
        assertEquals("₹1.00", Money.formatPaise(100));
        assertEquals("₹2,50,000.00", Money.formatPaise(25_000_000));
    }

    @Test
    void paiseAreRenderedToTwoDigits() {
        assertEquals("₹1.05", Money.formatPaise(105));
        assertEquals("₹0.09", Money.formatPaise(9));
        assertEquals("₹0.01", Money.formatPaise(1));
    }

    @Test
    void groupingIsLocaleIndependent() {
        // String.format("%,.2f") without a locale grouped differently per machine.
        assertEquals("₹1,23,456.78", Money.formatPaise(12_345_678));
    }

    @Test
    void negativesRoundAwayFromZeroWithoutSignErrors() {
        assertEquals("-₹12.50", Money.formatPaise(-1250));
        assertEquals("-₹1.00", Money.formatPaise(-100));
    }

    @Test
    void largeValuesDoNotLosePrecision() {
        // paise / 100.0 loses precision above 2^53 and produced garbage digits.
        long huge = 9_007_199_254_740_993L;
        assertEquals("₹9,00,71,99,25,47,409.93", Money.formatPaise(huge));
        assertEquals("90071992547409.93", Money.toRupees(huge));
    }

    @Test
    void indianGroupingUsesLastThreeThenPairs() {
        assertEquals("100", Money.groupIndian(100));
        assertEquals("1,000", Money.groupIndian(1_000));
        assertEquals("2,50,000", Money.groupIndian(250_000));
        assertEquals("12,34,567", Money.groupIndian(1_234_567));
        assertEquals("1,23,45,678", Money.groupIndian(12_345_678));
        assertEquals("-1,000", Money.groupIndian(-1_000));
    }

    @Test
    void longMinValueDoesNotOverflowOnAbs() {
        // Math.abs(Long.MIN_VALUE) is still negative; the grouping must stay sane.
        String s = Money.groupIndian(Long.MIN_VALUE);
        assertTrue(s.startsWith("-"), s);
        assertTrue(s.contains(","), s);
    }

    @Test
    void toRupeesIsPlainAndUngrouped() {
        assertEquals("250000.00", Money.toRupees(25_000_000));
        assertEquals("0.05", Money.toRupees(5));
    }
}
