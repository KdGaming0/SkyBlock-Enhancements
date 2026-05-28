/*
 * Copyright (C) 2022 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * NotEnoughUpdates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
 *
 * -----------------------------------------------------------------------------
 * ADAPTATION NOTICE:
 * This file is a derivative work based on NotEnoughUpdates'
 * "rei_search_bar_calculations.Calculator". It has been adapted for
 * SkyBlock Enhancements to add support for:
 *   - Configurable decimal separators (dot, comma, or both)
 *   - Scientific notation (e.g. 1.5e6)
 *   - Functions (sqrt, sin, cos, …) with 1 or 2 arguments
 *   - Named constants (pi, e, tau)
 *   - Implicit multiplication (e.g. 2(3+1), 2pi)
 *   - Stack suffix "st" (×64) in addition to k/m/b/t
 *   - Binary modulo operator %
 * The original lexer/shunting-yard/RPN architecture and BigDecimal
 * arithmetic are preserved from NEU.
 * -----------------------------------------------------------------------------
 */

package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * BigDecimal expression evaluator using the classic lexer → shunting-yard → RPN pipeline.
 *
 * <p>Derived from NotEnoughUpdates' calculator and extended with functions, constants,
 * implicit multiplication, scientific notation, and configurable decimal separators.
 */
final class ExpressionCalculator {

    /** Scale used for intermediate division results to avoid non-terminating decimals. */
    static final int PRECISION = 5;

    static final String BINOPS = "+-*/^x";
    static final String POSTOPS = "kmbt%";
    static final String DIGITS = "0123456789";
    static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyz_";

    public interface VariableProvider {
        Optional<BigDecimal> provideVariable(String name) throws CalculatorException;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    static BigDecimal calculate(String source, char decimalSep) throws CalculatorException {
        return calculate(source, (ignored) -> Optional.empty(), decimalSep);
    }

    static BigDecimal calculate(String source, VariableProvider variables, char decimalSep)
            throws CalculatorException {
        List<Token> tokens = lex(source, decimalSep);
        tokens = insertImplicitMultiply(tokens);
        List<Token> rpn = shuntingYard(tokens);
        return evaluate(variables, rpn, decimalSep);
    }

    // ── Token model ────────────────────────────────────────────────────────────

    enum TokenType {
        NUMBER, BINOP, LPAREN, RPAREN, PREOP, POSTOP, VARIABLE, FUNCTION, CONSTANT, COMMA
    }

    static final class Token {
        TokenType type;
        String operatorValue;
        String numberLiteral;
        int functionArity = -1;
        int tokenStart;
        int tokenLength;
    }

    static final class CalculatorException extends Exception {
        final int offset;
        final int length;

        CalculatorException(String message, int offset, int length) {
            super(message);
            this.offset = offset;
            this.length = length;
        }

        int getOffset() { return offset; }
        int getLength() { return length; }
    }

    // ── Lexer ──────────────────────────────────────────────────────────────────

    static List<Token> lex(String source, char decimalSep) throws CalculatorException {
        List<Token> tokens = new ArrayList<>();
        boolean doesNotHaveLValue = true;
        int len = source.length();

        for (int i = 0; i < len; ) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            Token token = new Token();
            token.tokenStart = i;

            if (doesNotHaveLValue && (c == '-' || c == '+')) {
                token.tokenLength = 1;
                token.type = TokenType.PREOP;
                token.operatorValue = String.valueOf(c);
            } else if (BINOPS.indexOf(c) != -1 || c == 'x') {
                token.tokenLength = 1;
                token.type = TokenType.BINOP;
                token.operatorValue = String.valueOf(c);
                if (c == '*' && i + 1 < len && source.charAt(i + 1) == '*') {
                    token.tokenLength++;
                    token.operatorValue = "^";
                }
            } else if (!doesNotHaveLValue && isPostopStart(source, i)) {
                token.type = TokenType.POSTOP;
                if (c == 's' && i + 1 < len && source.charAt(i + 1) == 't'
                        && (i + 2 >= len || !Character.isLetter(source.charAt(i + 2)))) {
                    token.tokenLength = 2;
                    token.operatorValue = "st";
                } else {
                    token.tokenLength = 1;
                    token.operatorValue = String.valueOf(c).toLowerCase(Locale.ROOT);
                }
            } else if (c == '%') {
                if (doesNotHaveLValue) {
                    throw new CalculatorException("Unexpected %", i, 1);
                }
                int peek = i + 1;
                while (peek < len && Character.isWhitespace(source.charAt(peek))) peek++;
                boolean followedByValue = peek < len && isValueStartChar(source.charAt(peek));
                token.tokenLength = 1;
                token.operatorValue = "%";
                token.type = followedByValue ? TokenType.BINOP : TokenType.POSTOP;
            } else if (c == ')') {
                token.tokenLength = 1;
                token.type = TokenType.RPAREN;
                token.operatorValue = ")";
            } else if (c == '(') {
                token.tokenLength = 1;
                token.type = TokenType.LPAREN;
                token.operatorValue = "(";
            } else if (isDecimalSeparator(c, decimalSep)) {
                token = readDecimalStart(source, i, decimalSep);
            } else if (c == ',') {
                token.tokenLength = 1;
                token.type = TokenType.COMMA;
                token.operatorValue = ",";
            } else if (c == '$') {
                token.tokenLength = 1;
                token.type = TokenType.VARIABLE;
                token.operatorValue = "";
                boolean inBrace = false;
                if (i + 1 < len && source.charAt(i + 1) == '{') {
                    token.tokenLength++;
                    inBrace = true;
                }
                for (int j = token.tokenStart + token.tokenLength; j < len; j++) {
                    char d = source.charAt(j);
                    if (inBrace) {
                        if (d == '}') {
                            token.tokenLength++;
                            inBrace = false;
                            break;
                        }
                    } else if (NAME_CHARS.indexOf(d) == -1) {
                        break;
                    }
                    token.operatorValue += d;
                    token.tokenLength++;
                }
                if (token.operatorValue.isEmpty() || inBrace) {
                    throw new CalculatorException(
                            "Unterminated variable literal", token.tokenStart, token.tokenLength);
                }
            } else if (DIGITS.indexOf(c) != -1) {
                token = readNumber(source, i, decimalSep);
            } else if (Character.isLetter(c)) {
                token = readIdentifier(source, i, len);
            } else {
                throw new CalculatorException("Unknown character: " + c, i, 1);
            }

            doesNotHaveLValue =
                    token.type == TokenType.LPAREN
                            || token.type == TokenType.PREOP
                            || token.type == TokenType.BINOP
                            || token.type == TokenType.COMMA;

            tokens.add(token);
            i += token.tokenLength;
        }
        return tokens;
    }

    // ── Lexer helpers ──────────────────────────────────────────────────────────

    private static boolean isPostopStart(String source, int i) {
        char c = source.charAt(i);
        if (c == 's' && i + 1 < source.length() && source.charAt(i + 1) == 't'
                && (i + 2 >= source.length() || !Character.isLetter(source.charAt(i + 2)))) {
            return true;
        }
        return POSTOPS.indexOf(c) != -1;
    }

    private static boolean isValueStartChar(char c) {
        return DIGITS.indexOf(c) != -1
                || c == '('
                || c == '$'
                || c == '+'
                || c == '-'
                || Character.isLetter(c);
    }

    private static boolean isDecimalSeparator(char c, char decimalSep) {
        if (decimalSep == '\0') return c == '.' || c == ',';
        return c == decimalSep;
    }

    private static Token readDecimalStart(String source, int start, char decimalSep)
            throws CalculatorException {
        Token token = new Token();
        token.tokenStart = start;
        // Must be followed by a digit to be a number literal
        if (start + 1 < source.length() && DIGITS.indexOf(source.charAt(start + 1)) != -1) {
            token.type = TokenType.NUMBER;
            int p = start;
            boolean hasComma = false;
            boolean hasDot = false;

            // consume decimal separator
            if (decimalSep == '\0') {
                if (source.charAt(p) == '.') hasDot = true;
                else hasComma = true;
            }
            p++;

            // fractional digits
            while (p < source.length() && DIGITS.indexOf(source.charAt(p)) != -1) p++;

            // exponent
            int expConsumed = tryReadExponent(source, p);
            if (expConsumed > 0) p += expConsumed;

            token.tokenLength = p - start;
            token.numberLiteral = source.substring(start, p);
        } else if (source.charAt(start) == ',') {
            token.tokenLength = 1;
            token.type = TokenType.COMMA;
            token.operatorValue = ",";
        } else {
            throw new CalculatorException("Invalid number literal", start, 1);
        }
        return token;
    }

    private static Token readNumber(String source, int start, char decimalSep)
            throws CalculatorException {
        Token token = new Token();
        token.tokenStart = start;
        token.type = TokenType.NUMBER;
        int p = start;
        boolean hasDecimal = false;
        boolean hasExponent = false;
        boolean hasComma = false;
        boolean hasDot = false;

        // integer part
        while (p < source.length() && DIGITS.indexOf(source.charAt(p)) != -1) p++;

        // fractional part
        if (p < source.length() && isDecimalSeparator(source.charAt(p), decimalSep)
                && !hasDecimal && !hasExponent) {
            if (decimalSep == '\0') {
                if (source.charAt(p) == '.') hasDot = true;
                else hasComma = true;
            }
            hasDecimal = true;
            p++;
            while (p < source.length() && DIGITS.indexOf(source.charAt(p)) != -1) p++;
        }

        // exponent part
        int expConsumed = tryReadExponent(source, p);
        if (expConsumed > 0) {
            hasExponent = true;
            p += expConsumed;
        }

        if (decimalSep == '\0' && hasDot && hasComma) {
            throw new CalculatorException(
                    "Ambiguous number with both dot and comma", start, p - start);
        }

        token.tokenLength = p - start;
        token.numberLiteral = source.substring(start, p);
        return token;
    }

    /** Returns number of characters consumed for the exponent, or 0 if no exponent. */
    private static int tryReadExponent(String source, int pos) {
        if (pos >= source.length()) return 0;
        char c = source.charAt(pos);
        if (c != 'e' && c != 'E') return 0;
        int peek = pos + 1;
        if (peek >= source.length()) return 0;
        char next = source.charAt(peek);
        if (next != '+' && next != '-' && DIGITS.indexOf(next) == -1) return 0;

        int p = pos + 1; // consume 'e'
        if (source.charAt(p) == '+' || source.charAt(p) == '-') p++;
        int digitsStart = p;
        while (p < source.length() && DIGITS.indexOf(source.charAt(p)) != -1) p++;
        if (p == digitsStart) return 0; // no exponent digits
        return p - pos;
    }

    private static Token readIdentifier(String source, int start, int len)
            throws CalculatorException {
        Token token = new Token();
        token.tokenStart = start;
        token.type = TokenType.CONSTANT;
        StringBuilder name = new StringBuilder();
        int p = start;
        while (p < len && Character.isLetter(source.charAt(p))) {
            name.append(source.charAt(p));
            p++;
        }
        token.operatorValue = name.toString().toLowerCase(Locale.ROOT);
        token.tokenLength = p - start;

        // Peek ahead past whitespace for '('
        int after = p;
        while (after < len && Character.isWhitespace(source.charAt(after))) after++;
        if (after < len && source.charAt(after) == '(') {
            token.type = TokenType.FUNCTION;
            token.functionArity = countArguments(source, after);
        }
        return token;
    }

    /** Counts top-level commas between the '(' at {@code openPos} and its matching ')'. */
    private static int countArguments(String source, int openPos) {
        int depth = 0;
        int commas = 0;
        int p = openPos + 1;
        while (p < source.length()) {
            char c = source.charAt(p);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    p++;
                    break;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                commas++;
            }
            p++;
        }
        return commas + 1;
    }

    // ── Implicit multiplication ────────────────────────────────────────────────

    private static List<Token> insertImplicitMultiply(List<Token> tokens) {
        if (tokens.isEmpty()) return tokens;
        List<Token> out = new ArrayList<>();
        out.add(tokens.get(0));
        for (int i = 1; i < tokens.size(); i++) {
            Token prev = out.get(out.size() - 1);
            Token curr = tokens.get(i);
            if (canEndValue(prev) && canStartValue(curr)) {
                Token mul = new Token();
                mul.type = TokenType.BINOP;
                mul.operatorValue = "*";
                mul.tokenStart = curr.tokenStart;
                mul.tokenLength = 0;
                out.add(mul);
            }
            out.add(curr);
        }
        return out;
    }

    private static boolean canEndValue(Token t) {
        return t.type == TokenType.NUMBER
                || t.type == TokenType.RPAREN
                || t.type == TokenType.POSTOP
                || t.type == TokenType.VARIABLE
                || t.type == TokenType.CONSTANT;
    }

    private static boolean canStartValue(Token t) {
        return t.type == TokenType.NUMBER
                || t.type == TokenType.LPAREN
                || t.type == TokenType.VARIABLE
                || t.type == TokenType.FUNCTION
                || t.type == TokenType.CONSTANT
                || t.type == TokenType.PREOP;
    }

    // ── Shunting-yard ──────────────────────────────────────────────────────────

    static List<Token> shuntingYard(List<Token> toShunt) throws CalculatorException {
        Deque<Token> op = new ArrayDeque<>();
        List<Token> out = new ArrayList<>();

        for (Token t : toShunt) {
            switch (t.type) {
                case NUMBER:
                case VARIABLE:
                case CONSTANT:
                    out.add(t);
                    break;
                case FUNCTION:
                    op.push(t);
                    break;
                case PREOP:
                    op.push(t);
                    break;
                case BINOP:
                    int p = getPrecedence(t);
                    while (!op.isEmpty()) {
                        Token top = op.peek();
                        if (top.type == TokenType.LPAREN) break;
                        int pt = getPrecedence(top);
                        if (pt >= p) {
                            out.add(op.pop());
                        } else {
                            break;
                        }
                    }
                    op.push(t);
                    break;
                case LPAREN:
                    op.push(t);
                    break;
                case RPAREN:
                    while (true) {
                        if (op.isEmpty())
                            throw new CalculatorException(
                                    "Unbalanced right parenthesis", t.tokenStart, t.tokenLength);
                        Token top = op.pop();
                        if (top.type == TokenType.LPAREN) break;
                        out.add(top);
                    }
                    if (!op.isEmpty() && op.peek().type == TokenType.FUNCTION) {
                        out.add(op.pop());
                    }
                    break;
                case COMMA:
                    while (!op.isEmpty() && op.peek().type != TokenType.LPAREN) {
                        out.add(op.pop());
                    }
                    if (op.isEmpty()) {
                        throw new CalculatorException(
                                "Misplaced comma or mismatched parenthesis", t.tokenStart, t.tokenLength);
                    }
                    break;
                case POSTOP:
                    out.add(t);
                    break;
            }
        }

        while (!op.isEmpty()) {
            Token top = op.pop();
            if (top.type == TokenType.LPAREN)
                throw new CalculatorException(
                        "Unbalanced left parenthesis", top.tokenStart, top.tokenLength);
            out.add(top);
        }
        return out;
    }

    private static int getPrecedence(Token token) throws CalculatorException {
        switch (token.operatorValue) {
            case "+":
            case "-":
                return 0;
            case "*":
            case "/":
            case "x":
            case "%":
                return 1;
            case "^":
                return 2;
            default:
                throw new CalculatorException(
                        "Unknown operator " + token.operatorValue, token.tokenStart, token.tokenLength);
        }
    }

    // ── RPN evaluation ─────────────────────────────────────────────────────────

    static BigDecimal evaluate(VariableProvider provider, List<Token> rpn, char decimalSep)
            throws CalculatorException {
        Deque<BigDecimal> values = new ArrayDeque<>();
        try {
            for (Token cmd : rpn) {
                switch (cmd.type) {
                    case VARIABLE:
                        values.push(provider.provideVariable(cmd.operatorValue)
                                .orElseThrow(() -> new CalculatorException(
                                        "Unknown variable " + cmd.operatorValue,
                                        cmd.tokenStart, cmd.tokenLength)));
                        break;
                    case PREOP:
                        if (values.isEmpty()) throw underflow(cmd);
                        values.push(values.pop().negate());
                        break;
                    case NUMBER:
                        String lit = cmd.numberLiteral;
                        if (decimalSep == ',' || (decimalSep == '\0' && lit.indexOf(',') >= 0)) {
                            lit = lit.replace(',', '.');
                        }
                        values.push(new BigDecimal(lit));
                        break;
                    case CONSTANT:
                        values.push(resolveConstant(cmd));
                        break;
                    case BINOP:
                        if (values.size() < 2) throw underflow(cmd);
                        BigDecimal right = values.pop().setScale(PRECISION, RoundingMode.HALF_UP);
                        BigDecimal left = values.pop().setScale(PRECISION, RoundingMode.HALF_UP);
                        values.push(applyBinop(cmd, left, right));
                        break;
                    case POSTOP:
                        if (values.isEmpty()) throw underflow(cmd);
                        BigDecimal base = values.pop();
                        values.push(applyPostop(cmd, base));
                        break;
                    case FUNCTION:
                        if (cmd.functionArity < 0 || values.size() < cmd.functionArity)
                            throw underflow(cmd);
                        BigDecimal[] args = new BigDecimal[cmd.functionArity];
                        for (int i = cmd.functionArity - 1; i >= 0; i--) {
                            args[i] = values.pop();
                        }
                        values.push(applyFunction(cmd, args));
                        break;
                    case LPAREN:
                    case RPAREN:
                        throw new CalculatorException(
                                "Unexpected parenthesis in RPN", cmd.tokenStart, cmd.tokenLength);
                    case COMMA:
                        throw new CalculatorException(
                                "Unexpected comma in RPN", cmd.tokenStart, cmd.tokenLength);
                }
            }
            if (values.isEmpty()) {
                throw new CalculatorException("Empty expression", 0, 0);
            }
            return values.pop().stripTrailingZeros();
        } catch (NoSuchElementException e) {
            throw new CalculatorException("Unfinished expression", 0, 0);
        }
    }

    private static CalculatorException underflow(Token cmd) {
        return new CalculatorException(
                "Not enough values for " + cmd.operatorValue, cmd.tokenStart, cmd.tokenLength);
    }

    private static BigDecimal resolveConstant(Token cmd) throws CalculatorException {
        return switch (cmd.operatorValue) {
            case "pi" -> BigDecimal.valueOf(Math.PI);
            case "tau" -> BigDecimal.valueOf(2 * Math.PI);
            case "e" -> BigDecimal.valueOf(Math.E);
            default -> throw new CalculatorException(
                    "Unknown constant " + cmd.operatorValue, cmd.tokenStart, cmd.tokenLength);
        };
    }

    private static BigDecimal applyBinop(Token cmd, BigDecimal left, BigDecimal right)
            throws CalculatorException {
        return switch (cmd.operatorValue) {
            case "^" -> {
                if (right.compareTo(BigDecimal.valueOf(1000)) >= 0) {
                    throw new CalculatorException(
                            right + " is too large, pick a power less than 1000",
                            cmd.tokenStart, cmd.tokenLength);
                }
                if (right.stripTrailingZeros().scale() > 0) {
                    throw new CalculatorException(
                            right + " has a decimal, pick a power that is non-decimal",
                            cmd.tokenStart, cmd.tokenLength);
                }
                if (right.signum() < 0) {
                    throw new CalculatorException(
                            right + " is negative, pick a power that is positive",
                            cmd.tokenStart, cmd.tokenLength);
                }
                yield left.pow(right.intValueExact()).setScale(PRECISION, RoundingMode.HALF_UP);
            }
            case "x", "*" -> left.multiply(right).setScale(PRECISION, RoundingMode.HALF_UP);
            case "/" -> {
                try {
                    yield left.divide(right, RoundingMode.HALF_UP)
                            .setScale(PRECISION, RoundingMode.HALF_UP);
                } catch (ArithmeticException e) {
                    throw new CalculatorException(
                            "Division by zero", cmd.tokenStart, cmd.tokenLength);
                }
            }
            case "+" -> left.add(right).setScale(PRECISION, RoundingMode.HALF_UP);
            case "-" -> left.subtract(right).setScale(PRECISION, RoundingMode.HALF_UP);
            case "%" -> {
                try {
                    yield left.remainder(right).setScale(PRECISION, RoundingMode.HALF_UP);
                } catch (ArithmeticException e) {
                    throw new CalculatorException(
                            "Modulo by zero", cmd.tokenStart, cmd.tokenLength);
                }
            }
            default -> throw new CalculatorException(
                    "Unknown operator " + cmd.operatorValue, cmd.tokenStart, cmd.tokenLength);
        };
    }

    private static BigDecimal applyPostop(Token cmd, BigDecimal v) throws CalculatorException {
        return switch (cmd.operatorValue) {
            case "st" -> v.multiply(BigDecimal.valueOf(64)).setScale(PRECISION, RoundingMode.HALF_UP);
            case "k" -> v.multiply(BigDecimal.valueOf(1_000)).setScale(PRECISION, RoundingMode.HALF_UP);
            case "m" -> v.multiply(BigDecimal.valueOf(1_000_000)).setScale(PRECISION, RoundingMode.HALF_UP);
            case "b" -> v.multiply(BigDecimal.valueOf(1_000_000_000L)).setScale(PRECISION, RoundingMode.HALF_UP);
            case "t" -> v.multiply(new BigDecimal("1000000000000")).setScale(PRECISION, RoundingMode.HALF_UP);
            case "%" -> v.movePointLeft(2).setScale(PRECISION, RoundingMode.HALF_UP);
            default -> throw new CalculatorException(
                    "Unknown suffix " + cmd.operatorValue, cmd.tokenStart, cmd.tokenLength);
        };
    }

    private static BigDecimal applyFunction(Token cmd, BigDecimal[] args)
            throws CalculatorException {
        return switch (cmd.operatorValue) {
            case "sqrt" -> bdMath(args[0], Math::sqrt);
            case "cbrt" -> bdMath(args[0], Math::cbrt);
            case "abs" -> args[0].abs();
            case "floor" -> args[0].setScale(0, RoundingMode.FLOOR);
            case "ceil" -> args[0].setScale(0, RoundingMode.CEILING);
            case "round" -> args[0].setScale(0, RoundingMode.HALF_UP);
            case "log" -> {
                if (args.length == 1) {
                    yield bdMath(args[0], Math::log10);
                }
                yield bdMath2(args[0], args[1], (a, b) -> Math.log(a) / Math.log(b));
            }
            case "ln" -> bdMath(args[0], Math::log);
            case "sin" -> bdMath(args[0], v -> Math.sin(Math.toRadians(v)));
            case "cos" -> bdMath(args[0], v -> Math.cos(Math.toRadians(v)));
            case "tan" -> bdMath(args[0], v -> Math.tan(Math.toRadians(v)));
            case "asin" -> bdMath(args[0], v -> Math.toDegrees(Math.asin(v)));
            case "acos" -> bdMath(args[0], v -> Math.toDegrees(Math.acos(v)));
            case "atan" -> bdMath(args[0], v -> Math.toDegrees(Math.atan(v)));
            case "deg" -> bdMath(args[0], Math::toDegrees);
            case "rad" -> bdMath(args[0], Math::toRadians);
            case "sign" -> BigDecimal.valueOf(args[0].signum());
            case "min" -> args[0].min(args[1]);
            case "max" -> args[0].max(args[1]);
            case "pow" -> bdPow(args[0], args[1]);
            case "atan2" -> bdMath2(args[0], args[1], (a, b) -> Math.toDegrees(Math.atan2(a, b)));
            default -> throw new CalculatorException(
                    "Unknown function " + cmd.operatorValue, cmd.tokenStart, cmd.tokenLength);
        };
    }

    private static BigDecimal bdMath(BigDecimal v, java.util.function.DoubleUnaryOperator op) {
        return BigDecimal.valueOf(op.applyAsDouble(v.doubleValue()));
    }

    private static BigDecimal bdMath2(BigDecimal a, BigDecimal b,
                                      java.util.function.DoubleBinaryOperator op) {
        return BigDecimal.valueOf(op.applyAsDouble(a.doubleValue(), b.doubleValue()));
    }

    private static BigDecimal bdPow(BigDecimal base, BigDecimal exponent)
            throws CalculatorException {
        if (exponent.stripTrailingZeros().scale() <= 0) {
            int exp = exponent.intValueExact();
            if (exp < 0) {
                throw new CalculatorException("Negative exponents not supported", 0, 0);
            }
            if (exp > 1000) {
                throw new CalculatorException("Exponent too large, max 1000", 0, 0);
            }
            return base.pow(exp);
        }
        return BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue()));
    }

    private ExpressionCalculator() {}
}
