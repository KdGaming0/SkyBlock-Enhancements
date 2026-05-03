package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeData;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeStoneData;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates reforge recipes from NEU constants.
 *
 * <p>One server recipe is created per (reforge, rarity) pair. When a player clicks an item
 * in RRV, only recipes whose rarity matches the item's rarity and whose item type applies to
 * the item will be shown in the reforge tab.
 *
 * <p>Blacksmith reforges and reforge stones are both expanded across their
 * {@code requiredRarities} list. The client renders each recipe as a compact stat card.
 */
public final class ReforgeRecipeGenerator {

    private ReforgeRecipeGenerator() {}

    public static void generate(List<ReliableServerRecipe> out) {
        Map<String, ReforgeData> reforges = NeuConstantsRegistry.getAllReforges();
        Map<String, ReforgeStoneData> stones = NeuConstantsRegistry.getAllReforgeStones();
        if (reforges.isEmpty() && stones.isEmpty()) return;

        // Blacksmith reforges — one recipe per supported rarity
        for (ReforgeData reforge : reforges.values()) {
            for (String rarity : reforge.requiredRarities()) {
                Map<String, Double> rarityStats = reforge.statsForRarity(rarity);
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
                        new String[0]));
            }
        }

        // Reforge stones — one recipe per supported rarity
        for (ReforgeStoneData stone : stones.values()) {
            String itemType = stone.itemTypes().orElse("");
            for (String rarity : stone.requiredRarities()) {
                Map<String, Double> rarityStats = stone.statsForRarity(rarity);
                int rarityCost = stone.costForRarity(rarity).orElse(0);
                Optional<String> rarityAbility = stone.abilityForRarity(rarity);

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
                        stone.specificInternalNames().orElse(List.of()),
                        stone.specificItemIds().orElse(List.of()),
                        stone.nbtModifier(),
                        new String[0]));
            }
        }
    }
}
