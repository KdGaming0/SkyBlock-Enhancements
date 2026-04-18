package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Groups a flat list of {@link ReliableServerRecipe} instances by their
 * {@link ReliableServerRecipeType} and assigns synthetic resource identifiers.
 *
 * <p>Extracted from {@link SkyblockInjectionCache} to keep the cache class focused
 * on state management only.
 */
public final class SkyblockRecipeGrouper {

    private SkyblockRecipeGrouper() {}

    /**
     * Groups recipes by type and assigns a unique {@link Identifier} to each entry.
     *
     * @param allRecipes flat list of all generated recipes
     * @return map from recipe type to list of entries with synthetic IDs
     */
    public static Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> group(
            List<ReliableServerRecipe> allRecipes) {

        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped = new HashMap<>();

        int idCounter = 0;
        for (ReliableServerRecipe recipe : allRecipes) {
            grouped.computeIfAbsent(recipe.getRecipeType(), k -> new ArrayList<>())
                    .add(new ServerRecipeEntry(
                            Identifier.fromNamespaceAndPath(
                                    "skyblock_enhancements", "recipe_" + (idCounter++)),
                            recipe));
        }
        return grouped;
    }
}