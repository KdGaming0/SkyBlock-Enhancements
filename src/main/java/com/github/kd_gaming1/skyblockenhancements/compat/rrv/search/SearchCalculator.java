package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight calculator for the RRV search bar.
 *
 * <p>Evaluates math expressions using a lexer + shunting-yard + BigDecimal RPN
 * evaluator derived from NotEnoughUpdates. Called on every keystroke, so a small
 * last-result cache skips re-parsing identical consecutive queries.
 *
 * <h3>Supported syntax</h3>
 * <ul>
 *   <li>Operators: {@code + - * / % ^ x} ({@code x} = multiply, {@code %} = modulo)</li>
 *   <li>Implicit multiply: {@code 2(3+1)}, {@code 2pi}, {@code 3 sqrt(4)}</li>
 *   <li>Unary minus/plus: {@code -3}, {@code --3}</li>
 *   <li>Grouping: {@code (...)}</li>
 *   <li>Number suffixes (chainable): {@code 1st} (stack×64), {@code k}, {@code m}, {@code b}, {@code t}</li>
 *   <li>Scientific notation: {@code 1.5e6}</li>
 *   <li>Constants: {@code pi}, {@code e}, {@code tau}</li>
 *   <li>Variables: {@code $VAR} or {@code ${VAR}}</li>
 *   <li>1-arg functions: {@code sqrt cbrt abs floor ceil round log ln
 *       sin cos tan asin acos atan deg rad sign}</li>
 *   <li>2-arg functions: {@code min(a,b) max(a,b) pow(a,b) log(x,base) atan2(y,x)}</li>
 * </ul>
 *
 * <h3>Precision and formatting</h3>
 * All arithmetic uses {@link BigDecimal} for exact decimal results. The final display
 * formatting is configurable via {@link SkyblockEnhancementsConfig}:
 * <ul>
 *   <li>Decimal separator: dot ({@code .}), comma ({@code ,}), or both.</li>
 *   <li>Rounding: enabled (half-up) or disabled (truncation).</li>
 *   <li>Maximum decimal places: 0–10.</li>
 * </ul>
 */
public final class SearchCalculator {

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** Last query that was evaluated. Volatile for safe multi-thread visibility. */
    private static volatile String cachedQuery = null;
    /** Result string for {@link #cachedQuery}. */
    private static volatile @Nullable String cachedResult = null;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluates {@code query} as a math expression and returns a formatted result string
     * (e.g. {@code " = 1.5K"}), or {@code null} if the query is not a valid expression.
     *
     * <p>Returns {@code null} silently so callers can use it as a pass-through without
     * any special-case logic.
     */
    @Nullable
    public static String tryEvaluate(@Nullable String query) {
        if (query == null || query.isBlank()) return null;

        // Fast path: identical to last query
        if (query.equals(cachedQuery)) return cachedResult;

        String result = computeResult(query);

        cachedQuery = query;
        cachedResult = result;
        return result;
    }

    @Nullable
    private static String computeResult(String query) {
        if (!isValidInput(query)) return null;

        try {
            char decimalSep = getActiveDecimalSeparator();
            BigDecimal result = ExpressionCalculator.calculate(query, decimalSep);
            return " = " + format(result);
        } catch (ExpressionCalculator.CalculatorException ignored) {
            // Any syntax error or unrecognised token returns null, preserving normal search.
            return null;
        }
    }

    // ── Input validation ──────────────────────────────────────────────────────

    /**
     * Rejects queries that contain characters the parser can never consume, so we
     * avoid constructing an {@link ExpressionCalculator} at all for normal text searches.
     *
     * <p>Allowed: digits, {@code .}, operators ({@code + - * / ^ % x}), parens, comma,
     * letters (functions/constants/suffixes), {@code $} (variables), underscore and whitespace.
     */
    private static boolean isValidInput(String s) {
        // Must start with something numeric-ish or a unary sign to be math
        boolean seenDigitOrParen = false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') { seenDigitOrParen = true; continue; }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || c == ' ' || c == '\t' || c == '$' || c == '_') {
                continue;
            }
            switch (c) {
                case '.', '+', '-', '*', '/', '^', '%', '(', ')', ',' -> {
                    if (c == '(' || c == ')') seenDigitOrParen = true;
                }
                case 'x' -> {
                    // Only treat 'x' as multiplication if preceded by a digit.
                    // Prevents item names like "Dr-x455" from being considered math.
                    if (i == 0) return false;
                    char prev = s.charAt(i - 1);
                    if (prev < '0' || prev > '9') return false;
                    // Also reject "2x4" which looks like an item identifier
                    if (i + 1 < len) {
                        char next = s.charAt(i + 1);
                        if (next >= '0' && next <= '9') return false;
                    }
                }
                default -> { return false; }
            }
        }
        return seenDigitOrParen;
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Formats a calculator result using the user's configured precision and separator.
     *
     * <p>Large values are scaled into T/B/M/K suffix bands (unless the "show full number"
     * option is enabled).  When suffixes are enabled, they are only applied to "clean" multiples
     * (e.g. 10000 → 10K, but 10001 → 10001) to preserve accuracy.
     * The fractional part is controlled by
     * {@link SkyblockEnhancementsConfig#rrvCalculatorRoundingEnabled}
     * and {@link SkyblockEnhancementsConfig#rrvCalculatorMaxDecimalPlaces}.
     */
    private static String format(BigDecimal v) {
        boolean negative = v.signum() < 0;
        BigDecimal abs = v.abs();

        BigDecimal scaled;
        String suffix;

        if (SkyblockEnhancementsConfig.rrvCalculatorShowFullNumber) {
            scaled = abs;
            suffix = "";
        } else {
            if      (abs.compareTo(new BigDecimal("1e12")) >= 0) { scaled = abs.movePointLeft(12); suffix = "T"; }
            else if (abs.compareTo(new BigDecimal("1e9"))  >= 0) { scaled = abs.movePointLeft(9);  suffix = "B"; }
            else if (abs.compareTo(new BigDecimal("1e6"))  >= 0) { scaled = abs.movePointLeft(6);  suffix = "M"; }
            else if (abs.compareTo(new BigDecimal("1e3"))  >= 0) { scaled = abs.movePointLeft(3);  suffix = "K"; }
            else                  { scaled = abs;         suffix = "";  }
        }

        char decimalSep = getActiveDecimalSeparator();
        if (decimalSep == '\0') decimalSep = '.'; // BOTH → dot in output

        // For suffix-scaled values show the exact number (no artificial max-decimal limit).
        // Full-number mode still respects the user's rounding / decimal-place config.
        String formatted = suffix.isEmpty()
                ? formatNumber(scaled, decimalSep)
                : formatExact(scaled, decimalSep);
        return (negative ? "-" : "") + formatted + suffix;
    }

    /**
     * Formats a BigDecimal exactly: no rounding, no max-decimal limit,
     * trailing zeros are stripped and thousands separators are added.
     */
    private static String formatExact(BigDecimal value, char decimalSep) {
        String plain = value.stripTrailingZeros().toPlainString();
        if (decimalSep != '.') {
            plain = plain.replace('.', decimalSep);
        }
        return addThousandsSeparators(plain, decimalSep);
    }

    /**
     * Fast number formatter that avoids {@link java.text.DecimalFormat} overhead.
     * Adds thousands separators and respects the user's rounding / max-decimal config.
     */
    private static String formatNumber(BigDecimal value, char decimalSep) {
        boolean roundingEnabled = SkyblockEnhancementsConfig.rrvCalculatorRoundingEnabled;
        int maxDecimals = SkyblockEnhancementsConfig.rrvCalculatorMaxDecimalPlaces;

        RoundingMode mode = roundingEnabled ? RoundingMode.HALF_UP : RoundingMode.DOWN;
        BigDecimal bd = value.setScale(Math.max(maxDecimals, 0), mode);

        String plain = bd.toPlainString();

        // Strip trailing zeros (and the dot if it becomes redundant)
        int dotIdx = plain.indexOf('.');
        if (dotIdx >= 0) {
            int end = plain.length();
            while (end > dotIdx + 1 && plain.charAt(end - 1) == '0') {
                end--;
            }
            if (end == dotIdx + 1) {
                end = dotIdx; // remove the dot too
            }
            plain = plain.substring(0, end);
        }

        // Replace decimal separator if needed
        if (decimalSep != '.') {
            plain = plain.replace('.', decimalSep);
        }

        // Add thousands separators
        return addThousandsSeparators(plain, decimalSep);
    }

    /**
     * Walks the integer part backwards and inserts a grouping separator every 3 digits.
     */
    private static String addThousandsSeparators(String plain, char decimalSep) {
        int sepIdx = plain.indexOf(decimalSep);
        String intPart = sepIdx >= 0 ? plain.substring(0, sepIdx) : plain;
        String fracPart = sepIdx >= 0 ? plain.substring(sepIdx) : "";

        boolean negative = intPart.startsWith("-");
        if (negative) {
            intPart = intPart.substring(1);
        }

        char groupSep = (decimalSep == ',') ? '.' : ',';
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            if (count == 3) {
                sb.append(groupSep);
                count = 0;
            }
            sb.append(intPart.charAt(i));
            count++;
        }
        String groupedInt = sb.reverse().toString();
        return (negative ? "-" : "") + groupedInt + fracPart;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the configured decimal separator, or {@code '\0'} when both are accepted. */
    private static char getActiveDecimalSeparator() {
        return switch (SkyblockEnhancementsConfig.rrvCalculatorDecimalSeparator) {
            case DOT -> '.';
            case COMMA -> ',';
            case BOTH -> '\0';
        };
    }
}
