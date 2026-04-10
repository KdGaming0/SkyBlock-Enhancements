package com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeClientRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client display for Kat pet upgrade recipes. */
public class SkyblockKatUpgradeClientRecipe implements ReliableClientRecipe {

    private final SlotContent input;
    private final SlotContent output;
    private final SlotContent[] materials;
    private final long coins;
    private final int timeSeconds;
    private final String[] wikiUrls;
    private Button wikiButton;

    public SkyblockKatUpgradeClientRecipe(
            SlotContent input, SlotContent output, SlotContent[] materials, long coins,
            int timeSeconds, String[] wikiUrls) {
        this.input = input;
        this.output = output;
        this.materials = materials;
        this.coins = coins;
        this.timeSeconds = timeSeconds;
        this.wikiUrls = wikiUrls != null ? wikiUrls : new String[0];
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockKatUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (input != null) ctx.bindSlot(0, input);
        for (int i = 0; i < materials.length && i < 4; i++) {
            if (materials[i] != null)
                ctx.bindOptionalSlot(i + 1, materials[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
        }
        if (output != null) ctx.bindSlot(5, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        if (input != null) list.add(input);
        for (SlotContent sc : materials) {
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
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getResults());
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getIngredients());
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
        gfx.drawString(font, Component.literal("→"), 22, 22, 0xFF404040, false);
        gfx.drawString(font, Component.literal("→"), 80, 22, 0xFF404040, false);
        if (coins > 0) {
            gfx.drawString(
                    font, Component.literal("§6" + formatCoins(coins) + " coins"), 2, 46, 0xFFAA8800, false);
        }
        if (timeSeconds > 0) {
            gfx.drawString(
                    font,
                    Component.literal("§7" + SkyblockForgeClientRecipe.formatDuration(timeSeconds)),
                    90,
                    46,
                    0xFF808080,
                    false);
        }

        if (wikiButton == null || !screen.children().contains(wikiButton)) {
            wikiButton = SkyblockRecipeUtil.addWikiButton(
                    screen, wikiUrls, pos.left(), pos.top() + 56);
        }
    }

    private static String formatCoins(long coins) {
        if (coins >= 1_000_000) return String.format("%.1fM", coins / 1_000_000.0);
        if (coins >= 1_000) return String.format("%.1fk", coins / 1_000.0);
        return String.valueOf(coins);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.KAT_UPGRADE;
    }
}