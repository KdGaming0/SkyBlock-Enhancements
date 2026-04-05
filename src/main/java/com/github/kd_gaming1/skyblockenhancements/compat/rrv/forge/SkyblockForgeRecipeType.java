package com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 2×3 input grid → output with duration text. Slots 0–5 = inputs, slot 6 = output. */
public class SkyblockForgeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockForgeRecipeType INSTANCE = new SkyblockForgeRecipeType();

    private static final int SLOT = 18;

    private final ItemStack icon = new ItemStack(Items.ANVIL);
    private final List<ItemStack> craftReferences = List.of(icon);

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Forge");
    }

    @Override
    public int getDisplayWidth() {
        return 120;
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
        return 7;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                def.addItemSlot(row * 3 + col, col * SLOT, row * SLOT + 9);
            }
        }
        def.addItemSlot(6, 96, 18);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_forge");
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Override
    public List<ItemStack> getCraftReferences() {
        return craftReferences;
    }
}