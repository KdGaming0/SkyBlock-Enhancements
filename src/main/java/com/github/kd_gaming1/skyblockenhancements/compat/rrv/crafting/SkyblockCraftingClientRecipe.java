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
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client-side display for a 3×3 SkyBlock crafting recipe with optional wiki button. */
public class SkyblockCraftingClientRecipe implements ReliableClientRecipe {

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final String[] wikiUrls;

    /** Tracks the wiki button so we re-add it only when the screen clears its widgets. */
    private Button wikiButton;

    public SkyblockCraftingClientRecipe(
            SlotContent[] inputs, SlotContent output, String[] wikiUrls) {
        this.inputs = inputs;
        this.output = output;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
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
            RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
            int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(
                Minecraft.getInstance().font, Component.literal("→"), 62, 22, 0xFF404040, false);

        if (wikiButton == null || !screen.children().contains(wikiButton)) {
            wikiButton = SkyblockRecipeUtil.addWikiButton(
                    screen, wikiUrls, pos.left(), pos.top() + 56);
        }
    }
}