package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.util.List;

/**
 * Immutable representation of a parsed search query.
 *
 * <p>Keywords are AND-ed together. Stat clauses are also AND-ed with keywords.
 * Empty queries return the full item list.
 */
public record SearchQuery(List<KeywordClause> keywords, List<StatClause> stats) {

    public SearchQuery {
        keywords = List.copyOf(keywords);
        stats = List.copyOf(stats);
    }

    public boolean isEmpty() {
        return keywords.isEmpty() && stats.isEmpty();
    }

    public record KeywordClause(String token) {}

    public record StatClause(String statName, Operator op, int value) {
        public enum Operator {
            GT, LT, GTE, LTE, EQ
        }
    }
}
