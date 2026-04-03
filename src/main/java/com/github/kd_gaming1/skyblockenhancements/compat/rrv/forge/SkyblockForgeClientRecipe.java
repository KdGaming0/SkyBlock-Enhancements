package com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge;

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

/** Client display for forge recipes with duration info. */
public class SkyblockForgeClientRecipe implements ReliableClientRecipe {

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final int durationSeconds;

    public SkyblockForgeClientRecipe(SlotContent[] inputs, SlotContent output, int durationSeconds) {
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockForgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < inputs.length && i < 6; i++) {
            if (inputs[i] != null)
                ctx.bindOptionalSlot(i, inputs[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
        }
        if (output != null) ctx.bindSlot(6, output);
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
        var font = Minecraft.getInstance().font;
        gfx.drawString(font, Component.literal("→"), 68, 22, 0xFF404040, false);
        if (durationSeconds > 0) {
            gfx.drawString(
                    font, Component.literal("§7" + formatDuration(durationSeconds)), 2, 46, 0xFF808080, false);
        }
    }

    public static String formatDuration(int seconds) {
        if (seconds >= 3600) {
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            return m > 0 ? h + "h " + m + "m" : h + "h";
        }
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }
}