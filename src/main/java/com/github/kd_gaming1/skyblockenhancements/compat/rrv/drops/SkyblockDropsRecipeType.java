package com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Up to 12 drop slots in a 4×3 grid with mob info rendered as text above. */
public class SkyblockDropsRecipeType implements ReliableClientRecipeType {

    public static final SkyblockDropsRecipeType INSTANCE = new SkyblockDropsRecipeType();

    private static final int SLOT = 18;
    private static final int COLS = 4;
    private static final int ROWS = 3;

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Mob Drops");
    }

    @Override
    public int getDisplayWidth() {
        return COLS * SLOT + 12;
    }

    @Override
    public int getDisplayHeight() {
        return 12 + ROWS * SLOT;
    }

    @Override
    public Identifier getGuiTexture() {
        return null;
    }

    @Override
    public int getSlotCount() {
        return COLS * ROWS;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                def.addItemSlot(row * COLS + col, col * SLOT + 6, 12 + row * SLOT);
            }
        }
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_drops");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override
    public List<ItemStack> getCraftReferences() {
        return List.of();
    }
}