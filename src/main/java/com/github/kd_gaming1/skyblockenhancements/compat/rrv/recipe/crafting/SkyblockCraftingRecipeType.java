package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 3×3 SkyBlock crafting grid: 9 input slots + 1 output slot. */
public class SkyblockCraftingRecipeType implements ReliableClientRecipeType {

    public static final SkyblockCraftingRecipeType INSTANCE = new SkyblockCraftingRecipeType();

    private static final int SLOT = 18;
    private static final int OUTPUT_X = 94;
    private static final int OUTPUT_Y = 18;

    private final ItemStack icon = new ItemStack(Items.CRAFTING_TABLE);
    private final List<ItemStack> craftReferences = List.of(icon);

    @Override public Component   getDisplayName() { return Component.literal("SkyBlock Crafting"); }
    @Override public int         getDisplayWidth()  { return 118; }
    @Override public int         getDisplayHeight() { return 68; }
    @Override public Identifier  getGuiTexture()    { return null; }
    @Override public int         getSlotCount()     { return 10; }
    @Override public ItemStack   getIcon()          { return icon; }
    @Override public List<ItemStack> getCraftReferences() { return craftReferences; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                def.addItemSlot(row * 3 + col, col * SLOT, row * SLOT);
            }
        }
        def.addItemSlot(9, OUTPUT_X, OUTPUT_Y);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_crafting");
    }
}