package com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 1 cost slot → 1 result slot. Slot 0 = cost, slot 1 = result. */
public class SkyblockTradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockTradeRecipeType INSTANCE = new SkyblockTradeRecipeType();

    private final ItemStack icon = new ItemStack(Items.VILLAGER_SPAWN_EGG);

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Trade");
    }

    @Override
    public int getDisplayWidth() {
        return 80;
    }

    @Override
    public int getDisplayHeight() {
        return 50;
    }

    @Override
    public Identifier getGuiTexture() {
        return null;
    }

    @Override
    public int getSlotCount() {
        return 2;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, 0, 9);
        def.addItemSlot(1, 58, 9);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_trade");
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }
}