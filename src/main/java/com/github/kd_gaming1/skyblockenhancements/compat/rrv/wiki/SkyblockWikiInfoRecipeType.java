package com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Single item slot + wiki button. For items with wiki URLs but no other recipe data. */
public class SkyblockWikiInfoRecipeType implements ReliableClientRecipeType {

    public static final SkyblockWikiInfoRecipeType INSTANCE = new SkyblockWikiInfoRecipeType();

    private final ItemStack icon = new ItemStack(Items.KNOWLEDGE_BOOK);

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Wiki");
    }

    @Override
    public int getDisplayWidth() {
        return 130;
    }

    @Override
    public int getDisplayHeight() {
        return 36;
    }

    @Override
    public Identifier getGuiTexture() {
        return null;
    }

    @Override
    public int getSlotCount() {
        return 1;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, 0, 0);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_wiki_info");
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }
}