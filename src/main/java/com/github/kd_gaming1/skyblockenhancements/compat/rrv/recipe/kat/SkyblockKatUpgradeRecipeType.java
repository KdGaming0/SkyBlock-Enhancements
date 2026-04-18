package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Input pet (0) + up to 4 materials (1–4) → output pet (5); coins & duration in text. */
public class SkyblockKatUpgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockKatUpgradeRecipeType INSTANCE = new SkyblockKatUpgradeRecipeType();

    private static final int SLOT = 18;

    private final ItemStack icon = new ItemStack(Items.BONE);

    @Override public Component  getDisplayName()   { return Component.literal("Kat Pet Upgrade"); }
    @Override public int        getDisplayWidth()  { return 140; }
    @Override public int        getDisplayHeight() { return 68; }
    @Override public Identifier getGuiTexture()    { return null; }
    @Override public int        getSlotCount()     { return 6; }
    @Override public ItemStack  getIcon()          { return icon; }

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
}