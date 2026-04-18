package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight recursive-descent calculator for the RRV search bar.
 *
 * <p>Called on every keystroke, so allocation and CPU cost are kept minimal:
 * <ul>
 *   <li>No regex — input is validated with a direct char scan (zero allocation).</li>
 *   <li>Input is lowercased once in the constructor; no per-token toLowerCase calls.</li>
 *   <li>A small last-result cache skips re-parsing identical consecutive queries
 *       (e.g. the user holds a key or the frame re-renders).</li>
 * </ul>
 *
 * <h3>Supported syntax</h3>
 * <ul>
 *   <li>Operators: {@code + - * / % ^ x} (x = multiply)</li>
 *   <li>Implicit multiply: {@code 2(3+1)}, {@code 2pi}, {@code 3 sqrt(4)}</li>
 *   <li>Unary minus/plus: {@code -3}, {@code --3}</li>
 *   <li>Grouping: {@code (...)}</li>
 *   <li>Number suffixes (chainable): {@code 1st} (stack×64), {@code k}, {@code m}, {@code b}, {@code t}</li>
 *   <li>Scientific notation: {@code 1.5e6}</li>
 *   <li>Constants: {@code pi}, {@code e}, {@code tau}</li>
 *   <li>1-arg functions: {@code sqrt cbrt abs floor ceil round log ln
 *       sin cos tan asin acos atan deg rad sign}</li>
 *   <li>2-arg functions: {@code min(a,b) max(a,b) pow(a,b) log(x,base) atan2(y,x)}</li>
 * </ul>
 */
public final class SearchCalculator {

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** Last query that was evaluated. */
    private static String cachedQuery = null;
    /** Result string for {@link #cachedQuery}, or {@code null} if it was not a math expression. */
    private static @Nullable String cachedResult = null;

    // ── Formatting ────────────────────────────────────────────────────────────

    private static final DecimalFormat GUI_FORMAT =
            new DecimalFormat("###,###.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    // ── Parser state ──────────────────────────────────────────────────────────

    /** Pre-lowercased input for zero-cost identifier comparisons. */
    private final String input;
    private int pos = 0;

    private SearchCalculator(String raw) {
        this.input = raw.toLowerCase(Locale.ROOT);
    }

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
            SearchCalculator calc = new SearchCalculator(query);
            double result = calc.parseExpr();

            calc.skipWhitespace();
            if (calc.pos != calc.input.length() || !Double.isFinite(result)) return null;

            return " = " + format(result);
        } catch (Exception ignored) {
            // Any syntax error or unrecognised token returns null, preserving normal search.
            return null;
        }
    }

    // ── Input validation ──────────────────────────────────────────────────────

    /**
     * Rejects queries that contain characters the parser can never consume, so we
     * avoid constructing a {@link SearchCalculator} at all for normal text searches.
     *
     * <p>Allowed: digits, {@code .}, operators ({@code +-*\/^%x}), parens, comma,
     * letters (functions/constants/suffixes), and whitespace.
     */
    private static boolean isValidInput(String s) {
        // Must start with something numeric-ish or a unary sign to be math
        boolean seenDigitOrParen = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) { seenDigitOrParen = true; continue; }
            if (Character.isLetter(c) || Character.isWhitespace(c)) { continue; }
            switch (c) {
                case '.', '+', '-', '*', '/', '^', '%', 'x', '(', ')', ',' -> {
                    if (c == '(' || c == ')') seenDigitOrParen = true;
                }
                default -> { return false; }
            }
        }
        return seenDigitOrParen;
    }

    // ── Parser logic ──────────────────────────────────────────────────────────

    /**
     * Additive tier: {@code expr → term (('+' | '-') term)*}
     */
    private double parseExpr() {
        double v = parseTerm();
        while (true) {
            skipWhitespace();
            if      (consume('+')) v += parseTerm();
            else if (consume('-')) v -= parseTerm();
            else return v;
        }
    }

    /**
     * Multiplicative tier: {@code term → factor (('*' | 'x' | '/' | '%') factor)*}
     *
     * <p>'x' is treated as multiply only when it appears as an operator between two
     * values (i.e. not at the start of an identifier like "xor").
     */
    private double parseTerm() {
        double v = parseFactor();
        while (true) {
            skipWhitespace();
            if (consume('*')) {
                v *= parseFactor();
            } else if (consumeMultiplyX()) {
                v *= parseFactor();
            } else if (consume('/')) {
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("Division by zero");
                v /= d;
            } else if (consume('%')) {
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("Modulo by zero");
                v %= d;
            } else {
                // Implicit multiplication: e.g. "2(3)" or "2 pi"
                double implicit = tryImplicitMultiply();
                if (Double.isNaN(implicit)) return v;
                v *= implicit;
            }
        }
    }

    /**
     * Consumes 'x' only when it is acting as a multiply operator (not part of a word).
     * Example: {@code 2x3} → yes; {@code xor} → no.
     */
    private boolean consumeMultiplyX() {
        if (pos >= input.length() || input.charAt(pos) != 'x') return false;
        // 'x' must NOT be followed by another letter (that would make it a word/function)
        int next = pos + 1;
        if (next < input.length() && Character.isLetter(input.charAt(next))) return false;
        pos++;
        return true;
    }

    /**
     * Attempts implicit multiplication with the next factor.
     * Returns the factor's value if implicit multiply applies, or {@link Double#NaN} otherwise.
     *
     * <p>Implicit multiply fires when the previous factor is followed by {@code '('} or a
     * letter that starts a known constant/function — NOT raw digits (that would break
     * suffix parsing like "1k").
     */
    private double tryImplicitMultiply() {
        int savedPos = pos;
        skipWhitespace();
        if (pos >= input.length()) return Double.NaN;

        char c = input.charAt(pos);
        if (c == '(' || Character.isLetter(c)) {
            try {
                return parseFactor();
            } catch (Exception e) {
                pos = savedPos;
                return Double.NaN;
            }
        }
        pos = savedPos;
        return Double.NaN;
    }

    /**
     * Exponentiation tier (right-associative): {@code factor → unary ('^' factor)?}
     */
    private double parseFactor() {
        double v = parseUnary();
        skipWhitespace();
        if (consume('^')) return Math.pow(v, parseFactor());
        return v;
    }

    /**
     * Unary tier: {@code unary → ('-' | '+') unary | primary}
     */
    private double parseUnary() {
        skipWhitespace();
        if (consume('-')) return -parseUnary();
        if (consume('+')) return  parseUnary();
        return parsePrimary();
    }

    /**
     * Primary tier: parenthesised expression, function call, constant, or number.
     */
    private double parsePrimary() {
        skipWhitespace();

        if (consume('(')) {
            double v = parseExpr();
            if (!consume(')')) throw new IllegalStateException("Expected ')'");
            return v;
        }

        if (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            return parseIdentifier();
        }

        return parseNumber();
    }

    /**
     * Parses a function call or named constant.
     * Input is already lowercased, so no conversion is needed here.
     */
    private double parseIdentifier() {
        int start = pos;
        while (pos < input.length() && Character.isLetter(input.charAt(pos))) pos++;
        String name = input.substring(start, pos);

        skipWhitespace();

        if (consume('(')) {
            // First argument
            double a = parseExpr();

            // Optional second argument (for binary functions)
            if (consume(',')) {
                double b = parseExpr();
                if (!consume(')')) throw new IllegalStateException("Expected ')'");
                return applyBinaryFunction(name, a, b);
            }

            if (!consume(')')) throw new IllegalStateException("Expected ')'");
            return applyUnaryFunction(name, a);
        }

        return resolveConstant(name);
    }

    private double applyUnaryFunction(String name, double v) {
        return switch (name) {
            case "sqrt"  -> Math.sqrt(v);
            case "cbrt"  -> Math.cbrt(v);
            case "abs"   -> Math.abs(v);
            case "floor" -> Math.floor(v);
            case "ceil"  -> Math.ceil(v);
            case "round" -> (double) Math.round(v);
            case "log"   -> Math.log10(v);
            case "ln"    -> Math.log(v);
            case "sin"   -> Math.sin(Math.toRadians(v));
            case "cos"   -> Math.cos(Math.toRadians(v));
            case "tan"   -> Math.tan(Math.toRadians(v));
            case "asin"  -> Math.toDegrees(Math.asin(v));
            case "acos"  -> Math.toDegrees(Math.acos(v));
            case "atan"  -> Math.toDegrees(Math.atan(v));
            case "deg"   -> Math.toDegrees(v);
            case "rad"   -> Math.toRadians(v);
            case "sign"  -> Math.signum(v);
            default      -> throw new IllegalStateException("Unknown function: " + name);
        };
    }

    private double applyBinaryFunction(String name, double a, double b) {
        return switch (name) {
            case "min"   -> Math.min(a, b);
            case "max"   -> Math.max(a, b);
            case "pow"   -> Math.pow(a, b);
            case "log"   -> Math.log(a) / Math.log(b);  // log(x, base)
            case "atan2" -> Math.toDegrees(Math.atan2(a, b));
            default      -> throw new IllegalStateException("Unknown function: " + name);
        };
    }

    private double resolveConstant(String name) {
        return switch (name) {
            case "pi"  -> Math.PI;
            case "tau" -> 2 * Math.PI;
            case "e"   -> Math.E;
            default    -> throw new IllegalStateException("Unknown constant: " + name);
        };
    }

    /**
     * Parses a numeric literal with optional scientific notation and value suffixes.
     *
     * <p>Suffixes (case-insensitive, chainable):
     * <ul>
     *   <li>{@code st} — one stack (×64)</li>
     *   <li>{@code k}  — thousand</li>
     *   <li>{@code m}  — million</li>
     *   <li>{@code b}  — billion</li>
     *   <li>{@code t}  — trillion</li>
     * </ul>
     */
    private double parseNumber() {
        int start = pos;
        boolean hasDecimal  = false;
        boolean hasExponent = false;

        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' && !hasDecimal && !hasExponent) {
                hasDecimal = true;
                pos++;
            } else if ((c == 'e') && !hasExponent && pos > start) {
                // Only treat 'e' as exponent marker if there are digits before it.
                // Check next char: must be digit, +, or - to be scientific notation.
                int peek = pos + 1;
                if (peek < input.length()) {
                    char next = input.charAt(peek);
                    if (Character.isDigit(next) || next == '+' || next == '-') {
                        hasExponent = true;
                        pos++; // consume 'e'
                        if (input.charAt(pos) == '+' || input.charAt(pos) == '-') pos++;
                        continue;
                    }
                }
                break; // 'e' here is the start of a suffix or function, stop
            } else {
                break;
            }
        }

        if (start == pos) throw new IllegalStateException("Expected number at pos " + pos);

        double v = Double.parseDouble(input.substring(start, pos));

        // Value suffixes — consume greedily, allow chaining (e.g. "1st k" = 64 000)
        skipWhitespace();
        v = consumeSuffixes(v);

        return v;
    }

    /**
     * Consumes zero or more value-scaling suffixes after a number literal.
     * Stops as soon as a non-suffix letter sequence is encountered.
     */
    private double consumeSuffixes(double v) {
        while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            char c1 = input.charAt(pos);
            char c2 = (pos + 1 < input.length()) ? input.charAt(pos + 1) : '\0';

            if      (c1 == 's' && c2 == 't') { v *=         64L; pos += 2; }
            else if (c1 == 'k')              { v *=      1_000L; pos++;    }
            else if (c1 == 'm')              { v *=  1_000_000L; pos++;    }
            else if (c1 == 'b')              { v *= 1_000_000_000L; pos++; }
            else if (c1 == 't')              { v *= 1_000_000_000_000L; pos++; }
            else break; // not a suffix — could be a function or constant
        }
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private boolean consume(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private static String format(double v) {
        boolean negative = v < 0;
        double abs = Math.abs(v);

        double scaled;
        String suffix;

        if      (abs >= 1e12) { scaled = abs / 1e12; suffix = "T"; }
        else if (abs >= 1e9)  { scaled = abs / 1e9;  suffix = "B"; }
        else if (abs >= 1e6)  { scaled = abs / 1e6;  suffix = "M"; }
        else if (abs >= 1e3)  { scaled = abs / 1e3;  suffix = "K"; }
        else                  { scaled = abs;         suffix = "";  }

        String formatted = GUI_FORMAT.format(scaled) + suffix;
        return negative ? "-" + formatted : formatted;
    }
}