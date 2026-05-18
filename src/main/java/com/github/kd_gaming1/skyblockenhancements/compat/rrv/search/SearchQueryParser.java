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
    public static SearchQuery parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SearchQuery(List.of(), List.of(), null);
        }

        String lower = isAlreadyLowercased(raw) ? raw : raw.toLowerCase(Locale.ROOT);

        List<SearchQuery.KeywordClause> keywords = new ArrayList<>();
        List<SearchQuery.StatClause> stats = new ArrayList<>();

        int len = lower.length();
        int i = 0;

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
                stats.add(stat);
            } else {
                keywords.add(new SearchQuery.KeywordClause(token));
            }
        }

        return new SearchQuery(keywords, stats, null);
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
}
