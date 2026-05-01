package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeData;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeStoneData;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates reforge template recipes from NEU constants.
 * One server recipe is created per reforge (blacksmith) and per reforge stone.
 * The client dynamically resolves the viewed item's rarity and applicable stats.
 */
public final class ReforgeRecipeGenerator {

    private ReforgeRecipeGenerator() {}

    public static void generate(List<ReliableServerRecipe> out) {
        Map<String, ReforgeData> reforges = NeuConstantsRegistry.getAllReforges();
        Map<String, ReforgeStoneData> stones = NeuConstantsRegistry.getAllReforgeStones();
        if (reforges.isEmpty() && stones.isEmpty()) return;

        // Blacksmith reforges (one recipe per reforge)
        for (ReforgeData reforge : reforges.values()) {
            out.add(new SkyblockReforgeServerRecipe(
                    reforge.reforgeName(),
                    true,
                    "",
                    reforge.itemTypes(),
                    reforge.requiredRarities(),
                    reforge.reforgeStats(),
                    Map.of(),
                    Optional.empty(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    reforge.nbtModifier(),
                    new String[0]));
        }

        // Reforge stones (one recipe per stone)
        for (ReforgeStoneData stone : stones.values()) {
            String itemType = stone.itemTypes().orElse("");
            out.add(new SkyblockReforgeServerRecipe(
                    stone.reforgeName(),
                    false,
                    stone.internalName(),
                    itemType,
                    stone.requiredRarities(),
                    stone.reforgeStats(),
                    stone.reforgeCosts(),
                    stone.reforgeAbility(),
                    stone.reforgeAbilityByRarity(),
                    stone.specificInternalNames().orElse(List.of()),
                    stone.specificItemIds().orElse(List.of()),
                    stone.nbtModifier(),
                    new String[0]));
        }
    }
}
