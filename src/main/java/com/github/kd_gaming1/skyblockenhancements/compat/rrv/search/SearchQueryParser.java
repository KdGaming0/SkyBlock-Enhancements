package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Zero-allocation direct-scan parser for the search query bar.
 *
 * <p>Splits on whitespace, detects stat threshold operators ({@code > < >= <= =}),
 * filter prefixes ({@code key:value}, {@code key>value}), and normalises tokens.
 * No regex is used.
 *
 * <h3>Supported syntax</h3>
 * <ul>
 *   <li>Keywords: {@code farming pets} -> AND of {@code farming} and {@code pet}.</li>
 *   <li>Stat thresholds: {@code mining_speed>50}, {@code health<=100}, {@code damage=50}.</li>
 *   <li>Filters: {@code rarity:legendary}, {@code type:sword}, {@code slayer:zombie>3},
 *       {@code catacombs>10}, {@code soulbound}</li>
 *   <li>Mixed: {@code mining_speed>50 fleet rarity:legendary} -> stat AND keyword AND filter.</li>
 * </ul>
 */
public final class SearchQueryParser {

    private SearchQueryParser() {}

    private static final SearchQuery EMPTY = new SearchQuery(List.of(), List.of(), List.of(), null);

    private static final Map<String, String> FILTER_KEY_ALIASES = Map.ofEntries(
            Map.entry("r", "rarity"),
            Map.entry("rarity", "rarity"),
            Map.entry("t", "type"),
            Map.entry("type", "type"),
            Map.entry("sl", "slayer"),
            Map.entry("slayer", "slayer"),
            Map.entry("sk", "skill"),
            Map.entry("skill", "skill"),
            Map.entry("cata", "catacombs"),
            Map.entry("catacombs", "catacombs"),
            Map.entry("soulbound", "soulbound"),
            Map.entry("dungeon", "dungeon"),
            Map.entry("rift", "rift")
    );

    public static SearchQuery parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        String lower = isAlreadyLowercased(raw) ? raw : raw.toLowerCase(Locale.ROOT);

        int len = lower.length();
        int i = 0;

        while (i < len && Character.isWhitespace(lower.charAt(i))) i++;
        if (i >= len) return EMPTY;

        List<SearchQuery.KeywordClause> keywords = null;
        List<SearchQuery.StatClause> stats = null;
        List<SearchQuery.FilterClause> filters = null;

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

            SearchQuery.FilterClause filter = tryParseFilter(token);
            if (filter != null) {
                if (filters == null) filters = new ArrayList<>(4);
                filters.add(filter);
                continue;
            }

            SearchQuery.StatClause stat = tryParseStat(token);
            if (stat != null) {
                if (stats == null) stats = new ArrayList<>(4);
                stats.add(stat);
                continue;
            }

            for (String part : splitOnNonAlphanumeric(token)) {
                if (keywords == null) keywords = new ArrayList<>(4);
                keywords.add(new SearchQuery.KeywordClause(part));
            }
        }

        return new SearchQuery(
                keywords != null ? keywords : List.of(),
                stats != null ? stats : List.of(),
                filters != null ? filters : List.of(),
                null);
    }

    // -- Filter parsing --------------------------------------------------------

    @Nullable
    private static SearchQuery.FilterClause tryParseFilter(String token) {
        int colonPos = token.indexOf(':');
        if (colonPos > 0) {
            String rawKey = token.substring(0, colonPos);
            String canonicalKey = resolveFilterKey(rawKey);
            if (canonicalKey == null) {
                return null;
            }

            String rest = token.substring(colonPos + 1);
            if (rest.isEmpty()) {
                return null;
            }

            OpParse op = parseOperatorSuffix(rest);
            if (op != null) {
                return SearchQuery.FilterClause.of(canonicalKey, op.op, op.stringValue, op.intValue);
            }

            return SearchQuery.FilterClause.of(canonicalKey, rest);
        }

        OpParse op = parseOperatorSuffix(token);
        if (op != null) {
            String canonicalKey = resolveFilterKey(op.stringValue);
            if (canonicalKey != null) {
                return SearchQuery.FilterClause.of(canonicalKey, op.op, null, op.intValue);
            }
        }

        String canonicalKey = resolveFilterKey(token);
        if (canonicalKey != null) {
            return SearchQuery.FilterClause.of(canonicalKey, SearchQuery.FilterClause.Operator.EQ, null, 0);
        }

        return null;
    }

    @Nullable
    private static OpParse parseOperatorSuffix(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '>' || c == '<' || c == '=') {
                String before = token.substring(0, i);
                if (before.isEmpty()) {
                    return null;
                }

                String numStr;
                SearchQuery.FilterClause.Operator op;

                if (i + 1 < token.length() && token.charAt(i + 1) == '=') {
                    numStr = token.substring(i + 2);
                    op = (c == '>')
                            ? SearchQuery.FilterClause.Operator.GTE
                            : SearchQuery.FilterClause.Operator.LTE;
                } else {
                    numStr = token.substring(i + 1);
                    op = switch (c) {
                        case '>' -> SearchQuery.FilterClause.Operator.GT;
                        case '<' -> SearchQuery.FilterClause.Operator.LT;
                        case '=' -> SearchQuery.FilterClause.Operator.EQ;
                        default -> null;
                    };
                }

                if (numStr.isEmpty() || op == null) {
                    return null;
                }

                try {
                    int value = Integer.parseInt(numStr);
                    return new OpParse(op, before, value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String resolveFilterKey(String raw) {
        return FILTER_KEY_ALIASES.get(raw);
    }

    private record OpParse(SearchQuery.FilterClause.Operator op, String stringValue, int intValue) {}

    // -- Stat parsing ----------------------------------------------------------

    @Nullable
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

    private static final Map<String, String> STAT_ALIASES = Map.ofEntries(
            Map.entry("ms", "mining_speed"),
            Map.entry("mfort", "mining_fortune"),
            Map.entry("as", "attack_speed"),
            Map.entry("cc", "critical_chance"),
            Map.entry("cd", "critical_damage"),
            Map.entry("hp", "health"),
            Map.entry("def", "defense"),
            Map.entry("str", "strength"),
            Map.entry("int", "intelligence"),
            Map.entry("sc", "sea_creature_chance"),
            Map.entry("fs", "fishing_speed"),
            Map.entry("ff", "farming_fortune"),
            Map.entry("forgfort", "foraging_fortune"),
            Map.entry("pristine", "pristine"),
            Map.entry("speed", "walk_speed")
    );

    private static String normalizeStatName(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '_' || Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        String normalized = sb.toString();
        String alias = STAT_ALIASES.get(normalized);
        return alias != null ? alias : normalized;
    }

    // -- Keyword splitting -----------------------------------------------------

    private static boolean isAlreadyLowercased(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return false;
            }
        }
        return true;
    }

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
