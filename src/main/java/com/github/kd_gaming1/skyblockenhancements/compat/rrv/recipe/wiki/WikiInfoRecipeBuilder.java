package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import net.minecraft.world.item.ItemStack;

/**
 * Produces the visual-only wiki card for items that have wiki URLs but no other recipe data.
 *
 * <p>NPCs are excluded — they already receive an NPC info card which links to the wiki.
 */
public final class WikiInfoRecipeBuilder {

    private WikiInfoRecipeBuilder() {}

    /**
     * Returns the wiki card recipe for {@code item}, or {@code null} when the fallback does
     * not apply (NPC, no wiki URLs, or empty display stack).
     */
    public static ReliableServerRecipe build(NeuItem item) {
        if (item.internalName != null && item.internalName.endsWith("_NPC")) return null;
        if (!item.hasWikiUrls()) return null;

        ItemStack stack = ItemStackBuilder.build(item).copy();
        if (stack.isEmpty()) return null;

        return new SkyblockWikiInfoServerRecipe(stack, item.getWikiUrls());
    }
}