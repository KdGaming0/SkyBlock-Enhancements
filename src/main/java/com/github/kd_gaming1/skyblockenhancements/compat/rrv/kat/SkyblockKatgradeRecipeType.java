package com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Kat pet upgrade layout: input pet (slot 0) + up to 4 materials (slots 1–4) → output pet (slot 5).
 * Coins and duration rendered as text by the client recipe.
 */
public class SkyblockKatgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockKatgradeRecipeType INSTANCE = new SkyblockKatgradeRecipeType();

    private static final int SLOT = 18;

    @Override
    public Component getDisplayName() {
        return Component.literal("Kat Pet Upgrade");
    }

    @Override
    public int getDisplayWidth() {
        return 140;
    }

    @Override
    public int getDisplayHeight() {
        return 68;
    }

    @Override
    public Identifier getGuiTexture() {
        return null;
    }

    @Override
    public int getSlotCount() {
        return 6;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, 0, 18);
        def.addItemSlot(1, 36, 9);
        def.addItemSlot(2, 54, 9);
        def.addItemSlot(3, 36, 27);
        def.addItemSlot(4, 54, 27);
        def.addItemSlot(5, 118, 18);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_katgrade");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.BONE);
    }
}