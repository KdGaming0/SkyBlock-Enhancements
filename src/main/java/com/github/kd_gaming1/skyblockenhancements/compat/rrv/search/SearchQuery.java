package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable representation of a parsed search query.
 *
 * <p>Keywords are AND-ed together. Stat clauses are also AND-ed with keywords.
 * Filter clauses (e.g. {@code rarity:legendary}, {@code slayer:zombie>3}) are AND-ed
 * at the index level for precise structured queries.
 * An optional category filter further narrows results.
 * Empty queries return the full item list.
 */
public record SearchQuery(List<KeywordClause> keywords, List<StatClause> stats,
                          List<FilterClause> filters,
                          @Nullable SkyblockItemCategory categoryFilter) {

    public SearchQuery {
        keywords = List.copyOf(keywords);
        stats = List.copyOf(stats);
        filters = List.copyOf(filters);
    }

    public SearchQuery(List<KeywordClause> keywords, List<StatClause> stats,
                       @Nullable SkyblockItemCategory categoryFilter) {
        this(keywords, stats, List.of(), categoryFilter);
    }

    public boolean isEmpty() {
        return keywords.isEmpty() && stats.isEmpty() && filters.isEmpty() && categoryFilter == null;
    }

    public record KeywordClause(String token) {}

    public record StatClause(String statName, Operator op, int value) {
        public enum Operator {
            GT, LT, GTE, LTE, EQ
        }
    }

    /**
     * Structured filter clause parsed from {@code key:value} or {@code key>value} syntax.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code rarity:legendary} → key="rarity", op=EQ, stringValue="legendary"</li>
     *   <li>{@code slayer:zombie>3} → key="slayer", op=GT, stringValue="zombie", intValue=3</li>
     *   <li>{@code catacombs>10} → key="catacombs", op=GT, intValue=10</li>
     *   <li>{@code soulbound} → key="soulbound", op=EQ</li>
     * </ul>
     */
    public record FilterClause(String key, Operator op,
                               @Nullable String stringValue, int intValue) {
        public enum Operator {
            EQ, GT, LT, GTE, LTE
        }

        public static FilterClause of(String key, String stringValue) {
            return new FilterClause(key, Operator.EQ, stringValue, 0);
        }

        public static FilterClause of(String key, int intValue) {
            return new FilterClause(key, Operator.EQ, null, intValue);
        }

        public static FilterClause of(String key, Operator op, int intValue) {
            return new FilterClause(key, op, null, intValue);
        }

        public static FilterClause of(String key, Operator op, String stringValue, int intValue) {
            return new FilterClause(key, op, stringValue, intValue);
        }
    }
}
