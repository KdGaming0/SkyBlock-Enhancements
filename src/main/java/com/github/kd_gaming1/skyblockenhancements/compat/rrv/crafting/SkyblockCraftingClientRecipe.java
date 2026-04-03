package com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side display wrapper for a SkyBlock crafting recipe. Binds 9 input slots and 1 output
 * slot, and renders an arrow between the grid and the result.
 */
public class SkyblockCraftingClientRecipe implements ReliableClientRecipe {

    private final SlotContent[] inputs;
    private final SlotContent output;

    public SkyblockCraftingClientRecipe(SlotContent[] inputs, SlotContent output) {
        this.inputs = inputs;
        this.output = output;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockCraftingRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < 9; i++) {
            if (inputs[i] != null) ctx.bindSlot(i, inputs[i]);
        }
        if (output != null) ctx.bindSlot(9, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (SlotContent sc : inputs) {
            if (sc != null) list.add(sc);
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return output != null ? List.of(output) : List.of();
    }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAny(stack, getResults());
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAny(stack, getIngredients());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen,
            RecipePosition pos,
            GuiGraphics gfx,
            int mouseX,
            int mouseY,
            float partialTicks) {
        gfx.drawString(Minecraft.getInstance().font, Component.literal("→"), 62, 22, 0xFF404040, false);
    }
}