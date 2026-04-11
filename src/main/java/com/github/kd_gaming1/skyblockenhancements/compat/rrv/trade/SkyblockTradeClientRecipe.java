package com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SkyblockTradeClientRecipe implements ReliableClientRecipe {

    private final SlotContent cost;
    private final SlotContent result;
    private final String[] wikiUrls;

    // True when buttons need to be (re)added to the screen.
    private boolean buttonsDirty = true;

    public SkyblockTradeClientRecipe(SlotContent cost, SlotContent result, String[] wikiUrls) {
        this.cost     = cost;
        this.result   = result;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    @Override
    public void initRecipe() {
        buttonsDirty = true;
    }

    @Override
    public void fadeRecipe() {
        buttonsDirty = true;
    }

    // ── ReliableClientRecipe ──────────────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockTradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (cost != null)   ctx.bindSlot(0, cost);
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
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getResults());
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getIngredients());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
            int mouseX, int mouseY, float partialTicks) {

        gfx.drawString(Minecraft.getInstance().font,
                Component.literal("→"), 28, 13, 0xFF404040, false);

        if (buttonsDirty) {
            addButtons(screen, pos);
            buttonsDirty = false;
        }
    }

    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, pos.left(), pos.top() + 36);
    }

    @Override
    public int getPriority() { return SkyblockRecipePriority.TRADE; }
}