package in.adivritti.core.common.util;

import java.math.BigDecimal;
import java.util.Locale;

/** Money is ALWAYS integer paise. Format only at the presentation edge. */
public final class Money {
    private Money() {}

    /**
     * Render paise as a rupee amount, e.g. {@code ₹2,50,000.00} or {@code -₹12.50}.
     *
     * <p>Uses {@link BigDecimal} rather than {@code paise / 100.0}: dividing a long by
     * a double loses precision above 2^53 paise and makes grouping locale-dependent.
     * Sign is handled before rounding so negatives never round away from zero.
     */
    public static String formatPaise(long paise) {
        BigDecimal rupees = BigDecimal.valueOf(paise, 2);
        BigDecimal abs = rupees.abs();
        long whole = abs.longValue();
        int paisePart = abs.subtract(BigDecimal.valueOf(whole)).movePointRight(2).intValue();
        return (rupees.signum() < 0 ? "-₹" : "₹")
            + groupIndian(whole)
            + String.format(Locale.ROOT, ".%02d", paisePart);
    }

    /** Paise to a plain decimal string without grouping or symbol (for API/CSV). */
    public static String toRupees(long paise) {
        return BigDecimal.valueOf(paise, 2).toPlainString();
    }

    /**
     * Indian digit grouping: the last three digits, then groups of two.
     * {@code 250000 -> 2,50,000}.
     *
     * <p>Implemented by hand rather than via {@code String.format("%,d", ...)} with
     * {@code en-IN}: the grouping pattern is locale-data dependent, and the JDK has
     * shipped both Indian and Western patterns for that tag across versions.
     */
    static String groupIndian(long value) {
        String digits = Long.toString(Math.abs(value));
        boolean negative = value < 0;
        int n = digits.length();
        String grouped;
        if (n <= 3) {
            grouped = digits;
        } else {
            String head = digits.substring(0, n - 3);
            String tail = digits.substring(n - 3);
            StringBuilder sb = new StringBuilder(n + (n / 2) + 1);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    sb.append(',');
                }
                sb.append(head.charAt(i));
            }
            grouped = sb.append(',').append(tail).toString();
        }
        return negative ? "-" + grouped : grouped;
    }
}
