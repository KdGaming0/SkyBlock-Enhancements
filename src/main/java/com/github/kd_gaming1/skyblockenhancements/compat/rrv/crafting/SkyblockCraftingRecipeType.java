package com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting;


import java.util.List;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Defines the 3×3 SkyBlock crafting grid recipe type for RRV. Layout: 9 input slots in a 3×3
 * grid on the left, 1 output slot on the right.
 */
public class SkyblockCraftingRecipeType implements ReliableClientRecipeType {

    public static final SkyblockCraftingRecipeType INSTANCE = new SkyblockCraftingRecipeType();

    // Slot layout constants (pixels, relative to recipe area)
    private static final int SLOT = 18;
    private static final int OUTPUT_X = 94;
    private static final int OUTPUT_Y = 18;

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Crafting");
    }

    @Override
    public int getDisplayWidth() {
        return 118;
    }

    @Override
    public int getDisplayHeight() {
        return 54;
    }

    @Override
    public Identifier getGuiTexture() {
        // null = no background texture; slots render with MC's default slot texture.
        // Replace with a custom texture later for a polished look.
        return null;
    }

    @Override
    public int getSlotCount() {
        return 10; // 9 inputs + 1 output
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        // 3×3 crafting grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                def.addItemSlot(row * 3 + col, col * SLOT, row * SLOT);
            }
        }
        // Output
        def.addItemSlot(9, OUTPUT_X, OUTPUT_Y);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_crafting");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    @Override
    public List<ItemStack> getCraftReferences() {
        return List.of(new ItemStack(Items.CRAFTING_TABLE));
    }
}