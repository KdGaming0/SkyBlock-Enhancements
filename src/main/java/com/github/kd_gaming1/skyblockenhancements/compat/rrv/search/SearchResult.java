package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.util.BitSet;

/**
 * Ranked search result expressed as three {@link BitSet}s over item indices.
 *
 * <ul>
 *   <li>{@code firstPrio} — all query terms matched in item names.</li>
 *   <li>{@code secondPrio} — all query terms matched anywhere (name, lore, metadata).</li>
 *   <li>{@code thirdPrio} — single-term prefix matches (only populated for single-keyword queries).</li>
 * </ul>
 */
public record SearchResult(BitSet firstPrio, BitSet secondPrio, BitSet thirdPrio) {

    public int totalSize() {
        return firstPrio.cardinality() + secondPrio.cardinality() + thirdPrio.cardinality();
    }
}
