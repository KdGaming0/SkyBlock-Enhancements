package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Up to 5 cost slots → 1 result slot. Slots 0–4 = costs, slot 5 = result. */
public class SkyblockNpcShopRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();

    private static final int SLOT = 18;

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock NPC Shop");
    }

    @Override
    public int getDisplayWidth() {
        return 120;
    }

    @Override
    public int getDisplayHeight() {
        return 40;
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
        // Up to 5 cost slots in a row
        for (int i = 0; i < 5; i++) {
            def.addItemSlot(i, i * SLOT, 11);
        }
        // Result slot
        def.addItemSlot(5, 102, 11);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.EMERALD);
    }

    @Override
    public List<ItemStack> getCraftReferences() {
        return List.of();
    }
}