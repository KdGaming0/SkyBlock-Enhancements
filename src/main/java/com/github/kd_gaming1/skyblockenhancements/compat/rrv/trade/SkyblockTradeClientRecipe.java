package com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Client display for trade recipes. */
public class SkyblockTradeClientRecipe implements ReliableClientRecipe {

    private final SlotContent cost;
    private final SlotContent result;

    public SkyblockTradeClientRecipe(SlotContent cost, SlotContent result) {
        this.cost = cost;
        this.result = result;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockTradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (cost != null) ctx.bindSlot(0, cost);
        if (result != null) ctx.bindSlot(1, result);
    }

    @Override
    public List<SlotContent> getIngredients() {
        return cost != null ? List.of(cost) : List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        return result != null ? List.of(result) : List.of();
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen,
            RecipePosition pos,
            GuiGraphics gfx,
            int mouseX,
            int mouseY,
            float partialTicks) {
        gfx.drawString(
                Minecraft.getInstance().font, Component.literal("→"), 28, 13, 0xFF404040, false);
    }
}