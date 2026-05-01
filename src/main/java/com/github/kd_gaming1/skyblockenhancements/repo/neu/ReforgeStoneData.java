package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable data for a reforge stone.
 *
 * @param internalName          the stone's item ID (e.g. "AMBER_MATERIAL")
 * @param reforgeName           display name of the reforge applied
 * @param itemTypes             type string when the stone applies to a category (e.g. "PICKAXE")
 * @param specificInternalNames list of specific item IDs the stone applies to
 * @param specificItemIds       list of specific minecraft item IDs the stone applies to
 * @param requiredRarities      list of rarities this reforge supports
 * @param reforgeCosts          rarity → coin cost
 * @param reforgeAbility        plain ability text (if uniform across rarities)
 * @param reforgeAbilityByRarity rarity → ability text (if per-rarity)
 * @param reforgeStats          rarity → stat name → value
 * @param nbtModifier           optional NBT modifier string
 */
public record ReforgeStoneData(
        String internalName,
        String reforgeName,
        Optional<String> itemTypes,
        Optional<List<String>> specificInternalNames,
        Optional<List<String>> specificItemIds,
        List<String> requiredRarities,
        Map<String, Integer> reforgeCosts,
        Optional<String> reforgeAbility,
        Map<String, String> reforgeAbilityByRarity,
        Map<String, Map<String, Double>> reforgeStats,
        Optional<String> nbtModifier) {

    public ReforgeStoneData {
        requiredRarities = List.copyOf(requiredRarities);
        reforgeCosts = Collections.unmodifiableMap(reforgeCosts);
        reforgeAbilityByRarity = Collections.unmodifiableMap(reforgeAbilityByRarity);
        reforgeStats = Collections.unmodifiableMap(reforgeStats);
        specificInternalNames = specificInternalNames.map(List::copyOf);
        specificItemIds = specificItemIds.map(List::copyOf);
    }

    /**
     * Returns the ability text for the given rarity, falling back to the uniform ability.
     */
    public Optional<String> abilityForRarity(String rarity) {
        if (reforgeAbility.isPresent()) return reforgeAbility;
        return Optional.ofNullable(reforgeAbilityByRarity.get(rarity));
    }

    /**
     * Returns the coin cost for the given rarity, or empty if none.
     */
    public Optional<Integer> costForRarity(String rarity) {
        return Optional.ofNullable(reforgeCosts.get(rarity));
    }

    /**
     * Returns the stats map for the given rarity, or an empty map if none.
     */
    public Map<String, Double> statsForRarity(String rarity) {
        return reforgeStats.getOrDefault(rarity, Map.of());
    }
}
