package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeData;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeStoneData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generates reforge recipes from NEU constants.
 *
 * <p>One server recipe is created per (reforge, rarity) pair. When a player clicks an item
 * in RRV, only recipes whose rarity matches the item's rarity and whose item type applies to the
 * item will be shown in the reforge tab.
 *
 * <p>Blacksmith reforges and reforge stones are both expanded across their
 * {@code requiredRarities} list. The client renders each recipe as a compact stat card.
 *
 * <p>Result item lists are pre-computed during generation so the client recipe never has to
 * scan the full {@link NeuItemRegistry}.
 */
public final class ReforgeRecipeGenerator {

    private ReforgeRecipeGenerator() {}

    public static void generate(List<ReliableServerRecipe> out) {
        Map<String, ReforgeData> reforges = NeuConstantsRegistry.getAllReforges();
        Map<String, ReforgeStoneData> stones = NeuConstantsRegistry.getAllReforgeStones();
        if (reforges.isEmpty() && stones.isEmpty()) return;

        // Pre-build reverse indexes once for fast result matching.
        Map<String, List<NeuItem>> byItemId = new java.util.HashMap<>();
        Map<String, List<NeuItem>> bySnbtId = new java.util.HashMap<>();
        Map<String, List<NeuItem>> byLoreType = new java.util.HashMap<>();

        for (NeuItem item : NeuItemRegistry.getAllValues()) {
            if (item.itemId != null && !item.itemId.isEmpty()) {
                byItemId.computeIfAbsent(item.itemId, k -> new ArrayList<>()).add(item);
            }
            if (item.snbtItemId != null && !item.snbtItemId.isEmpty()) {
                bySnbtId.computeIfAbsent(item.snbtItemId, k -> new ArrayList<>()).add(item);
            }
            if (item.loreType != null) {
                byLoreType.computeIfAbsent(item.loreType, k -> new ArrayList<>()).add(item);
            }
        }

        // Blacksmith reforges — one recipe per supported rarity
        for (ReforgeData reforge : reforges.values()) {
            for (String rarity : reforge.requiredRarities()) {
                Map<String, Double> rarityStats = reforge.statsForRarity(rarity);
                List<String> resultNames = computeResultNames(
                        reforge.itemTypes(), List.of(), List.of(),
                        byItemId, bySnbtId, byLoreType);
                out.add(new SkyblockReforgeServerRecipe(
                        reforge.reforgeName(),
                        true,
                        "",
                        reforge.itemTypes(),
                        rarity,
                        reforge.requiredRarities(),
                        rarityStats,
                        0,
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        reforge.nbtModifier(),
                        new String[0],
                        resultNames,
                        ""));
            }
        }

        // Reforge stones — one recipe per supported rarity
        for (ReforgeStoneData stone : stones.values()) {
            String itemType = stone.itemTypes().orElse("");
            List<String> specificInternalNames = stone.specificInternalNames().orElse(List.of());
            List<String> specificItemIds = stone.specificItemIds().orElse(List.of());
            String crafttext = resolveStoneCrafttext(stone.internalName());
            for (String rarity : stone.requiredRarities()) {
                Map<String, Double> rarityStats = stone.statsForRarity(rarity);
                int rarityCost = stone.costForRarity(rarity).orElse(0);
                Optional<String> rarityAbility = stone.abilityForRarity(rarity);
                List<String> resultNames = computeResultNames(
                        itemType, specificInternalNames, specificItemIds,
                        byItemId, bySnbtId, byLoreType);

                out.add(new SkyblockReforgeServerRecipe(
                        stone.reforgeName(),
                        false,
                        stone.internalName(),
                        itemType,
                        rarity,
                        stone.requiredRarities(),
                        rarityStats,
                        rarityCost,
                        rarityAbility,
                        specificInternalNames,
                        specificItemIds,
                        stone.nbtModifier(),
                        new String[0],
                        resultNames,
                        crafttext));
            }
        }
    }

    /**
     * Looks up the NEU item for a reforge stone and returns its collection requirement text.
     * Returns an empty string when the stone has no item entry or no requirement.
     */
    private static String resolveStoneCrafttext(String stoneInternalName) {
        if (stoneInternalName == null || stoneInternalName.isEmpty()) return "";
        NeuItem item = NeuItemRegistry.get(stoneInternalName);
        return item != null && item.hasCrafttext() ? item.crafttext : "";
    }

    /**
     * Computes the internal names of all items matching the given reforge criteria.
     * Uses pre-built reverse indexes for O(k) lookup instead of O(n) registry scans.
     */
    private static List<String> computeResultNames(
            String itemType,
            List<String> specificInternalNames,
            List<String> specificItemIds,
            Map<String, List<NeuItem>> byItemId,
            Map<String, List<NeuItem>> bySnbtId,
            Map<String, List<NeuItem>> byLoreType) {

        Set<String> names = new LinkedHashSet<>();

        for (String id : specificInternalNames) {
            NeuItem item = NeuItemRegistry.get(id);
            if (item != null) {
                names.add(id);
            }
        }

        for (String targetId : specificItemIds) {
            List<NeuItem> byId = byItemId.get(targetId);
            if (byId != null) {
                for (NeuItem item : byId) names.add(item.internalName);
            }
            List<NeuItem> bySnbt = bySnbtId.get(targetId);
            if (bySnbt != null) {
                for (NeuItem item : bySnbt) names.add(item.internalName);
            }
        }

        List<String> loreTypes = ReforgeTypeResolver.getLoreTypesForReforgeType(itemType);
        for (String lt : loreTypes) {
            List<NeuItem> items = byLoreType.get(lt);
            if (items != null) {
                for (NeuItem item : items) names.add(item.internalName);
            }
        }

        return names.isEmpty() ? List.of() : List.copyOf(names);
    }
}
