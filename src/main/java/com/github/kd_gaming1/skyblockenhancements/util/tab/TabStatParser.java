package com.github.kd_gaming1.skyblockenhancements.util.tab;

import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Extracts player stats from the Hypixel SkyBlock tab list.
 *
 * <p>Feed in a list of tab-line strings and get back a map of every stat
 * the parser could identify — keyed by canonical names such as
 * {@code mining_speed}, {@code health}, {@code defense}, and so on.
 *
 * <p>Typical usage (done automatically by {@link TabListMonitor}):
 * <pre>{@code
 *   List<String> raw = ...; // text from each tab-list entry
 *   ParseResult result = TabStatParser.parse(raw);
 *   int speed = result.getInt("mining_speed").orElse(0);
 * }</pre>
 *
 * <p>The parser handles colour codes, decorative icons, abbreviated numbers
 * ({@code 1.2k}), comma separators, multi-line stats, and unrecognised lines
 * via a generic key-value fallback. No Minecraft classes are required —
 * it works with plain strings so it can be driven from commands, tests, or
 * any data source.
 */
public final class TabStatParser {

    private TabStatParser() {}

    // ═══════════════════════════════════════════════════════════════════════════
    //  Normalisation pipeline — pre-compiled patterns for zero-allocation reuse
    // ═══════════════════════════════════════════════════════════════════════════

    /** Collapses runs of whitespace to a single space. */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /**
     * Characters we keep during decorative-Unicode stripping. Everything else
     * (emojis, stat icons, control chars) is removed.
     */
    private static final long KEEP_BITMASK = keepBitmask();

    private static long keepBitmask() {
        long mask = 0L;
        String keep = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 :,./-%'°∞";
        for (int i = 0; i < keep.length(); i++) {
            mask |= 1L << (keep.charAt(i) & 63);
        }
        return mask;
    }

    private static boolean shouldKeep(char ch) {
        return ch < 128 && ((KEEP_BITMASK >>> (ch & 63)) & 1L) != 0L;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Extraction patterns
    // ═══════════════════════════════════════════════════════════════════════════

    /** "Key: Value" with an optional colon. */
    private static final Pattern COLON_PAIR = Pattern.compile("^([^:]+)\\s*:\\s*(.+)$");
    /**
     * Fallback: a word-based key (≥3 chars) followed by whitespace and a value.
     * The value must start with a digit, sign, or known prefix.
     */
    private static final Pattern SPACE_PAIR = Pattern.compile("^([A-Za-z][A-Za-z\\s]{2,}?)\\s+([\\d\\-+~%£$].*)$");

    // ═══════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Runs the full normalisation + extraction pipeline over a list of raw tab lines.
     *
     * @param rawLines the display-name strings from each tab-list entry
     * @return a {@link ParseResult} containing every stat that could be identified
     */
    public static ParseResult parse(List<String> rawLines) {
        Map<String, String> stats = new HashMap<>();
        if (rawLines == null || rawLines.isEmpty()) {
            return new ParseResult(Collections.emptyMap());
        }

        // Pass 1 — normalise every line once.
        int n = rawLines.size();
        String[] normalized = new String[n];
        for (int i = 0; i < n; i++) {
            normalized[i] = normalize(rawLines.get(i));
        }

        // Pass 2 — try registered stat definitions (includes multi-line).
        for (int i = 0; i < n; i++) {
            String line = normalized[i];
            if (line.isEmpty() || StatDefinition.isBlockedKeyword(line)) continue;

            Optional<StatDefinition> matchedDef = matchDefinition(line);
            if (matchedDef.isPresent()) {
                StatDefinition def = matchedDef.get();
                String rawValue = def.extractValue(line);

                // Multi-line: value empty but next line is a number → use it.
                if (rawValue.isEmpty() && i + 1 < n) {
                    String next = normalized[i + 1].trim();
                    if (!next.isEmpty() && isValueLike(next)) {
                        rawValue = next;
                        i++; // consume the value line
                    }
                }

                if (!rawValue.isEmpty()) {
                    stats.merge(def.primaryKey(), rawValue, (oldVal, newVal) ->
                            // Prefer longer values (more specific) on collision.
                            newVal.length() > oldVal.length() ? newVal : oldVal
                    );
                }
                continue;
            }

            // Unregistered lines are intentionally ignored. Only stats declared
            // in StatDefinition appear in the result — this prevents Area, Server,
            // Profile, and other tab metadata from polluting the output.
        }

        return new ParseResult(stats);
    }

    // ── Normalisation ─────────────────────────────────────────────────────────

    /**
     * Strips Minecraft colour codes, decorative Unicode icons, and normalises
     * whitespace. The result is safe for case-insensitive alias matching.
     *
     * @param raw the original tab-line text; {@code null} is treated as empty
     * @return the cleaned, single-space-collapsed string
     */
    public static String normalize(String raw) {
        if (raw == null) return "";

        String noColour = StringUtil.stripColorCodes(raw);
        if (noColour.isEmpty()) return "";

        // Strip decorative chars char-by-char (fast path for ASCII).
        int len = noColour.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = noColour.charAt(i);
            if (shouldKeep(ch)) {
                sb.append(ch);
            }
        }

        String stripped = sb.toString().trim();
        if (stripped.isEmpty()) return "";

        return MULTI_SPACE.matcher(stripped).replaceAll(" ");
    }

    // ── Key-value extraction ──────────────────────────────────────────────────

    /**
     * Attempts to extract a generic {@code key → value} pair from a line that
     * did not match any registered {@link StatDefinition}.
     *
     * @return the entry if the line looks like a stat, else {@link Optional#empty()}
     */
    public static Optional<AbstractMap.SimpleEntry<String, String>> tryExtractPair(String normalized) {
        if (normalized == null || normalized.length() < 5) return Optional.empty();

        java.util.regex.Matcher m = COLON_PAIR.matcher(normalized);
        if (m.matches()) {
            String key = m.group(1).trim().toUpperCase(Locale.ROOT);
            String value = m.group(2).trim();
            if (looksLikeStatName(key) && !value.isEmpty()) {
                return Optional.of(new AbstractMap.SimpleEntry<>(key, value));
            }
        }

        m = SPACE_PAIR.matcher(normalized);
        if (m.matches()) {
            String key = m.group(1).trim().toUpperCase(Locale.ROOT);
            String value = m.group(2).trim();
            if (looksLikeStatName(key) && !value.isEmpty()) {
                return Optional.of(new AbstractMap.SimpleEntry<>(key, value));
            }
        }

        return Optional.empty();
    }

    // ── Value parsing ─────────────────────────────────────────────────────────

    /**
     * Parses an integer from a raw tab value, handling commas,
     * {@code k}/{@code M}/{@code B} suffixes, and sentinel strings.
     *
     * @param value the raw value string (e.g. "6,400", "1.2k", "N/A")
     * @return the parsed value, or {@link OptionalInt#empty()} if unparseable
     */
    public static OptionalInt parseInt(String value) {
        if (value == null || value.isEmpty()) return OptionalInt.empty();
        String cleaned = cleanValue(value);
        if (cleaned.isEmpty()) return OptionalInt.empty();

        Double expanded = expandAbbreviation(cleaned);
        if (expanded != null) {
            return safeDoubleToInt(expanded);
        }

        try {
            return OptionalInt.of(Integer.parseInt(cleaned));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** Same contract as {@link #parseInt} but for 64-bit values. */
    public static OptionalLong parseLong(String value) {
        if (value == null || value.isEmpty()) return OptionalLong.empty();
        String cleaned = cleanValue(value);
        if (cleaned.isEmpty()) return OptionalLong.empty();

        Double expanded = expandAbbreviation(cleaned);
        if (expanded != null) {
            return safeDoubleToLong(expanded);
        }

        try {
            return OptionalLong.of(Long.parseLong(cleaned));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Parses a floating-point value. Percentage signs are stripped
     * automatically (e.g. "75.5%" → 75.5).
     */
    public static OptionalDouble parseDouble(String value) {
        if (value == null || value.isEmpty()) return OptionalDouble.empty();
        String cleaned = cleanValue(value);
        if (cleaned.isEmpty()) return OptionalDouble.empty();
        if (cleaned.endsWith("%")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();

        Double expanded = expandAbbreviation(cleaned);
        if (expanded != null) return OptionalDouble.of(expanded);

        try {
            return OptionalDouble.of(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ParseResult
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Immutable snapshot of parsed tab stats. All getters are O(1).
     */
    public static final class ParseResult {
        private final Map<String, String> stats;

        ParseResult(Map<String, String> stats) {
            this.stats = stats;
        }

        public Optional<String> getString(String key) {
            return Optional.ofNullable(stats.get(key));
        }

        public OptionalInt getInt(String key) {
            String raw = stats.get(key);
            return raw != null ? TabStatParser.parseInt(raw) : OptionalInt.empty();
        }

        public OptionalLong getLong(String key) {
            String raw = stats.get(key);
            return raw != null ? TabStatParser.parseLong(raw) : OptionalLong.empty();
        }

        public OptionalDouble getDouble(String key) {
            String raw = stats.get(key);
            return raw != null ? TabStatParser.parseDouble(raw) : OptionalDouble.empty();
        }

        public boolean hasStat(String key) {
            return stats.containsKey(key);
        }

        public int statCount() {
            return stats.size();
        }

        /** Returns an unmodifiable view of all parsed stats (primaryKey → rawValue). */
        public Map<String, String> getAllStats() {
            return Collections.unmodifiableMap(stats);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Strips commas, whitespace, and known sentinel strings from a raw value. */
    private static String cleanValue(String value) {
        String v = value.trim();
        String upper = v.toUpperCase(Locale.ROOT);
        if (upper.equals("N/A") || upper.equals("MAX") || upper.equals("—")
                || upper.equals("-") || upper.equals("∞") || upper.equals("INFINITE")) {
            return "";
        }
        // Remove digit-separator commas (e.g. "6,400" → "6400").
        return v.replace(",", "").trim();
    }

    /**
     * Expands abbreviated numbers: 1.2k → 1200, 3M → 3000000, 5.1B → 5100000000.
     * Returns {@code null} if the string is not abbreviated.
     */
    private static Double expandAbbreviation(String cleaned) {
        int len = cleaned.length();
        if (len < 2) return null;

        char last = Character.toUpperCase(cleaned.charAt(len - 1));
        String numberPart = cleaned.substring(0, len - 1).trim();

        double multiplier;
        switch (last) {
            case 'K' -> multiplier = 1_000.0;
            case 'M' -> multiplier = 1_000_000.0;
            case 'B' -> multiplier = 1_000_000_000.0;
            default -> { return null; }
        }

        try {
            return Double.parseDouble(numberPart) * multiplier;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static OptionalInt safeDoubleToInt(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return OptionalInt.empty();
        return OptionalInt.of((int) Math.round(value));
    }

    private static OptionalLong safeDoubleToLong(double value) {
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) return OptionalLong.empty();
        return OptionalLong.of(Math.round(value));
    }

    /**
     * Checks whether the line matches any registered {@link StatDefinition}.
     * Returns the first match (order of registration).
     */
    private static Optional<StatDefinition> matchDefinition(String normalized) {
        for (StatDefinition def : StatDefinition.getAll()) {
            if (def.matches(normalized)) return Optional.of(def);
        }
        return Optional.empty();
    }

    /**
     * Heuristic: a string looks like a stat name if it is at least 3 characters,
     * starts with a letter, and is mostly alphabetic.
     */
    private static boolean looksLikeStatName(String text) {
        if (text.length() < 3) return false;
        if (!Character.isLetter(text.charAt(0))) return false;

        int letters = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (Character.isLetter(ch) || ch == ' ') letters++;
        }
        // At least 60% letters (allow spaces).
        return letters * 10 >= len * 6;
    }

    /**
     * Returns true if the text looks like a standalone value (number, percentage,
     * abbreviation, or ratio). Used for multi-line stat detection.
     */
    private static boolean isValueLike(String text) {
        if (text.isEmpty()) return false;
        char first = text.charAt(0);
        return Character.isDigit(first) || first == '+' || first == '-' || first == '~'
                || (text.endsWith("%") && text.length() > 1)
                || text.contains("/");
    }
}
