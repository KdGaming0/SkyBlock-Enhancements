package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import net.minecraft.world.item.ItemStack;

/**
 * Produces the NPC info card recipe for items whose internal name ends in {@code _NPC}.
 *
 * <p>Unlike shop/crafting parsers this doesn't consume a JSON entry — the info card is built
 * from NPC metadata (head, island, coordinates, lore) stored directly on the {@link NeuItem}.
 */
public final class NpcInfoRecipeBuilder {

    private static final String[] EMPTY_LORE = new String[0];

    private NpcInfoRecipeBuilder() {}

    /**
     * Returns the NPC info card recipe for {@code item}, or {@code null} if the item is not
     * an NPC.
     */
    public static ReliableServerRecipe build(NeuItem item) {
        if (item.internalName == null || !item.internalName.endsWith("_NPC")) return null;

        ItemStack head = ItemStackBuilder.build(item).copy();
        String[] lore = item.lore != null ? item.lore.toArray(new String[0]) : EMPTY_LORE;

        return new SkyblockNpcInfoServerRecipe(
                head,
                item.internalName,
                item.displayName != null ? item.displayName : "",
                item.island != null ? item.island : "",
                item.x, item.y, item.z,
                lore,
                item.getWikiUrls());
    }
}