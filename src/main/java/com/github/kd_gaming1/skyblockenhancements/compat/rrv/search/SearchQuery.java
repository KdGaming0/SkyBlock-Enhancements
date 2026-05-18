package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable representation of a parsed search query.
 *
 * <p>Keywords are AND-ed together. Stat clauses are also AND-ed with keywords.
 * An optional category filter further narrows results at the index level.
 * Empty queries return the full item list.
 */
public record SearchQuery(List<KeywordClause> keywords, List<StatClause> stats,
                          @Nullable SkyblockItemCategory categoryFilter) {

    public SearchQuery {
        keywords = List.copyOf(keywords);
        stats = List.copyOf(stats);
    }

    public boolean isEmpty() {
        return keywords.isEmpty() && stats.isEmpty() && categoryFilter == null;
    }

    public record KeywordClause(String token) {}

    public record StatClause(String statName, Operator op, int value) {
        public enum Operator {
            GT, LT, GTE, LTE, EQ
        }
    }
}
