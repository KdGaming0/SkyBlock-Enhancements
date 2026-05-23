package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zero-allocation direct-scan parser for the search query bar.
 *
 * <p>Splits on whitespace, detects stat threshold operators ({@code > < >= <= =}),
 * and normalises tokens. No regex is used.
 *
 * <h3>Supported syntax</h3>
 * <ul>
 *   <li>Keywords: {@code farming pets} → AND of {@code farming} and {@code pet}.</li>
 *   <li>Stat thresholds: {@code mining_speed>50}, {@code health<=100}, {@code damage=50}.</li>
 *   <li>Mixed: {@code mining_speed>50 fleet} → stat clause AND keyword.</li>
 * </ul>
 */
public final class SearchQueryParser {

    private SearchQueryParser() {}

    /**
     * Parses {@code raw} into a {@link SearchQuery}.
     * Returns an empty query when {@code raw} is null or blank.
     *
     * <p>The query is expected to already be lowercased by the mixin ({@code
     * ItemFiltersMixin#sbe$preLowercaseQuery}), so this parser skips redundant
     * {@code toLowerCase()} when every character is already lowercase.
     */
    private static final SearchQuery EMPTY = new SearchQuery(List.of(), List.of(), null);

    public static SearchQuery parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        String lower = isAlreadyLowercased(raw) ? raw : raw.toLowerCase(Locale.ROOT);

        int len = lower.length();
        int i = 0;

        // Skip leading whitespace and check for actual content
        while (i < len && Character.isWhitespace(lower.charAt(i))) i++;
        if (i >= len) return EMPTY;

        List<SearchQuery.KeywordClause> keywords = null;
        List<SearchQuery.StatClause> stats = null;

        while (i < len) {
            while (i < len && Character.isWhitespace(lower.charAt(i))) {
                i++;
            }
            if (i >= len) break;

            int start = i;
            while (i < len && !Character.isWhitespace(lower.charAt(i))) {
                i++;
            }
            String token = lower.substring(start, i);

            SearchQuery.StatClause stat = tryParseStat(token);
            if (stat != null) {
                if (stats == null) stats = new ArrayList<>(4);
                stats.add(stat);
            } else {
                for (String part : splitOnNonAlphanumeric(token)) {
                    if (keywords == null) keywords = new ArrayList<>(4);
                    keywords.add(new SearchQuery.KeywordClause(part));
                }
            }
        }

        return new SearchQuery(
                keywords != null ? keywords : List.of(),
                stats != null ? stats : List.of(),
                null);
    }

    /**
     * Fast-path check: returns {@code true} if {@code s} contains no uppercase letters.
     * This avoids the allocation + CPU cost of {@code toLowerCase()} when the mixin
     * has already lowercased the query.
     */
    private static boolean isAlreadyLowercased(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return false;
            }
        }
        return true;
    }

    private static SearchQuery.StatClause tryParseStat(String token) {
        int opPos = -1;
        char opChar = '\0';
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '>' || c == '<' || c == '=') {
                opPos = i;
                opChar = c;
                break;
            }
        }
        if (opPos <= 0) {
            return null;
        }

        String statName = normalizeStatName(token.substring(0, opPos));
        if (statName.isEmpty()) {
            return null;
        }

        String valueStr;
        SearchQuery.StatClause.Operator op;

        if (opPos + 1 < token.length() && token.charAt(opPos + 1) == '=') {
            valueStr = token.substring(opPos + 2);
            op = (opChar == '>')
                    ? SearchQuery.StatClause.Operator.GTE
                    : SearchQuery.StatClause.Operator.LTE;
        } else {
            valueStr = token.substring(opPos + 1);
            op = switch (opChar) {
                case '>' -> SearchQuery.StatClause.Operator.GT;
                case '<' -> SearchQuery.StatClause.Operator.LT;
                case '=' -> SearchQuery.StatClause.Operator.EQ;
                default -> null;
            };
        }

        if (valueStr.isEmpty() || op == null) {
            return null;
        }

        try {
            int value = Integer.parseInt(valueStr);
            return new SearchQuery.StatClause(statName, op, value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeStatName(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '_' || Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Splits a token on non-alphanumeric characters, matching the behaviour of
     * {@link SkyblockSearchIndex#tokenize}. Only sub-tokens with length {@code > 1}
     * are returned, mirroring the index's minimum token length.
     */
    private static List<String> splitOnNonAlphanumeric(String token) {
        List<String> parts = new ArrayList<>(4);
        int len = token.length();
        int start = -1;

        for (int i = 0; i < len; i++) {
            char c = token.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) {
                    start = i;
                }
            } else {
                if (start >= 0) {
                    if (i - start > 1) {
                        parts.add(token.substring(start, i));
                    }
                    start = -1;
                }
            }
        }

        if (start >= 0 && len - start > 1) {
            parts.add(token.substring(start, len));
        }

        return parts.isEmpty() ? List.of(token) : parts;
    }
}
