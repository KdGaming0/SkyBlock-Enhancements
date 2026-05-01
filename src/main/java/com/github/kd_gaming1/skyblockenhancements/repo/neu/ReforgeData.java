package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable data for a basic blacksmith reforge.
 *
 * @param reforgeName      display name of the reforge (e.g. "Clean")
 * @param itemTypes        comma-separated type string (e.g. "ARMOR", "SWORD/ROD")
 * @param requiredRarities list of rarities this reforge supports
 * @param reforgeStats     rarity → stat name → value
 * @param nbtModifier      optional NBT modifier string
 */
public record ReforgeData(
        String reforgeName,
        String itemTypes,
        List<String> requiredRarities,
        Map<String, Map<String, Double>> reforgeStats,
        Optional<String> nbtModifier) {

    public ReforgeData {
        requiredRarities = List.copyOf(requiredRarities);
        reforgeStats = Collections.unmodifiableMap(reforgeStats);
    }

    /**
     * Returns the stats map for the given rarity, or an empty map if none.
     */
    public Map<String, Double> statsForRarity(String rarity) {
        return reforgeStats.getOrDefault(rarity, Map.of());
    }
}
