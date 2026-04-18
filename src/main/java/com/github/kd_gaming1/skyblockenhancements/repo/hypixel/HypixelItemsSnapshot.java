package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry.HypixelUpgradeCost;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the Hypixel items API data. Shared between {@link HypixelItemsFetcher}
 * and {@link HypixelItemsCacheStore} so the on-disk and on-wire shapes use the same type.
 *
 * <p>Maps are required to already be unmodifiable views by their producers.
 */
public record HypixelItemsSnapshot(
        Map<String, Map<String, Integer>> baseStats,
        Map<String, Map<String, int[]>> tieredStats,
        Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts) {}