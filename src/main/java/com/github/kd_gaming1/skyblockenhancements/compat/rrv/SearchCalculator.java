package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public final class SearchCalculator {

    // Expanded to allow letters (for functions/suffixes) and % (modulo)
    private static final Pattern VALID_CHARS = Pattern.compile("^[\\d.+\\-*/^()%a-zA-Z\\s]+$");

    private static final DecimalFormat GUI_FORMAT = new DecimalFormat("###,###.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final String input;
    private int pos = 0;

    private SearchCalculator(String input) {
        this.input = input;
    }

    @Nullable
    public static String tryEvaluate(@Nullable String query) {
        if (query == null || query.isBlank() || !VALID_CHARS.matcher(query).matches()) {
            return null;
        }

        try {
            SearchCalculator calc = new SearchCalculator(query);
            double result = calc.parseExpr();

            calc.skipWhitespace();
            // If the parser didn't consume the whole string (e.g., they typed "10 apples"), it's not pure math.
            if (calc.pos != calc.input.length() || !Double.isFinite(result)) {
                return null;
            }

            return " = " + format(result);
        } catch (Exception ignored) {
            // Any invalid syntax or unrecognized function fails silently, preserving normal search
            return null;
        }
    }

    // ── Parser Logic ──────────────────────────────────────────────────────────

    private double parseExpr() {
        double v = parseTerm();
        while (true) {
            skipWhitespace();
            if (consume('+')) v += parseTerm();
            else if (consume('-')) v -= parseTerm();
            else return v;
        }
    }

    private double parseTerm() {
        double v = parseFactor();
        while (true) {
            skipWhitespace();
            if (consume('*')) v *= parseFactor();
            else if (consume('/')) {
                double divisor = parseFactor();
                if (divisor == 0) throw new ArithmeticException();
                v /= divisor;
            } else if (consume('%')) {
                double divisor = parseFactor();
                if (divisor == 0) throw new ArithmeticException();
                v %= divisor;
            } else return v;
        }
    }

    private double parseFactor() {
        double v = parseUnary();
        skipWhitespace();
        if (consume('^')) {
            return Math.pow(v, parseFactor());
        }
        return v;
    }

    private double parseUnary() {
        skipWhitespace();
        if (consume('-')) return -parseUnary();
        if (consume('+')) return parseUnary();
        return parsePrimary();
    }

    private double parsePrimary() {
        skipWhitespace();
        if (consume('(')) {
            double v = parseExpr();
            if (!consume(')')) throw new IllegalStateException();
            return v;
        }

        // Parse functions (e.g., "sqrt", "floor") and constants ("pi", "e")
        if (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                pos++;
            }
            String text = input.substring(start, pos).toLowerCase(Locale.ROOT);
            skipWhitespace();

            if (consume('(')) {
                double v = parseExpr();
                if (!consume(')')) throw new IllegalStateException();
                return switch (text) {
                    case "sqrt" -> Math.sqrt(v);
                    case "cbrt" -> Math.cbrt(v);
                    case "abs" -> Math.abs(v);
                    case "floor" -> Math.floor(v);
                    case "ceil" -> Math.ceil(v);
                    case "round" -> Math.round(v);
                    default -> throw new IllegalStateException();
                };
            } else {
                // If no parenthesis, check for constants
                return switch (text) {
                    case "pi" -> Math.PI;
                    case "e" -> Math.E;
                    default -> throw new IllegalStateException();
                };
            }
        }

        return parseNumber();
    }

    private double parseNumber() {
        int start = pos;
        boolean hasDecimal = false;
        boolean hasExponent = false;

        // Expanded to support scientific notation natively (e.g., "1.5e6")
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) pos++;
            else if (c == '.' && !hasDecimal) {
                hasDecimal = true;
                pos++;
            } else if ((c == 'e' || c == 'E') && !hasExponent) {
                hasExponent = true;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            } else break;
        }

        if (start == pos) throw new IllegalStateException();

        double v = Double.parseDouble(input.substring(start, pos));

        // Handle Multiple Suffixes (e.g., "1st k" -> 64,000)
        skipWhitespace();
        while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            char c1 = Character.toLowerCase(input.charAt(pos));
            char c2 = (pos + 1 < input.length()) ? Character.toLowerCase(input.charAt(pos + 1)) : '\0';

            if (c1 == 's' && c2 == 't') { v *= 64L; pos += 2; } // Stack
            else if (c1 == 'k') { v *= 1_000L; pos++; }
            else if (c1 == 'm') { v *= 1_000_000L; pos++; }
            else if (c1 == 'b') { v *= 1_000_000_000L; pos++; }
            else if (c1 == 't') { v *= 1_000_000_000_000L; pos++; }
            else break;
        }
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean consume(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private static String format(double v) {
        double abs = Math.abs(v);
        String suffix = "";
        double val = abs;

        if (abs >= 1e12) { val /= 1e12; suffix = "T"; }
        else if (abs >= 1e9) { val /= 1e9; suffix = "B"; }
        else if (abs >= 1e6) { val /= 1e6; suffix = "M"; }
        else if (abs >= 1e3) { val /= 1e3; suffix = "K"; }

        String formatted = GUI_FORMAT.format(val) + suffix;
        return v < 0 ? "-" + formatted : formatted;
    }
}