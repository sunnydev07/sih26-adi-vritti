package in.adivritti.core.common.util;

/** Money is ALWAYS integer paise. Format only at the presentation edge. */
public final class Money {
    private Money() {}

    public static String formatPaise(long paise) {
        return String.format("₹%,.2f", paise / 100.0);
    }
}
