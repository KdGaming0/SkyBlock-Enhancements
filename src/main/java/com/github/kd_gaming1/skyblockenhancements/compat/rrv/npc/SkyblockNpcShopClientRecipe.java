package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Client display for NPC shop recipes. */
public class SkyblockNpcShopClientRecipe implements ReliableClientRecipe {

    private final SlotContent[] costs;
    private final SlotContent result;

    public SkyblockNpcShopClientRecipe(SlotContent[] costs, SlotContent result) {
        this.costs = costs;
        this.result = result;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockNpcShopRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < costs.length && i < 5; i++) {
            if (costs[i] != null) {
                ctx.bindOptionalSlot(i, costs[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
        }
        if (result != null) {
            ctx.bindSlot(5, result);
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (SlotContent sc : costs) {
            if (sc != null) list.add(sc);
        }
        return list;
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
                Minecraft.getInstance().font, Component.literal("→"), 91, 15, 0xFF404040, false);
    }
}