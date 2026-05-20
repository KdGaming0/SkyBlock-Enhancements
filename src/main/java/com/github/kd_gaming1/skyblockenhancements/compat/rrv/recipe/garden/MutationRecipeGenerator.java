package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.List;

/**
 * Generates garden mutation server recipes from {@link GardenMutationRegistry}.
 *
 * <p>One recipe is created per mutation. Skips mutations whose item ID is not found
 * in the NEU registry and logs a warning.
 */
public final class MutationRecipeGenerator {

    private MutationRecipeGenerator() {}

    public static void generate(List<ReliableServerRecipe> out) {
        if (!GardenMutationRegistry.isLoaded()) {
            LOGGER.debug("Garden mutation registry not loaded — skipping recipe generation");
            return;
        }

        for (GardenMutationLayout layout : GardenMutationRegistry.all()) {
            NeuItem item = NeuItemRegistry.get(layout.mutationId());
            if (item == null) {
                LOGGER.warn("Mutation item '{}' not found in NEU registry — skipping recipe", layout.mutationId());
                continue;
            }

            out.add(new SkyblockGardenMutationServerRecipe(
                    layout.mutationId(),
                    item.getWikiUrls()));
        }
    }
}
