package com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class SkyblockWikiInfoClientRecipe implements ReliableClientRecipe {

    private static final int DISPLAY_HEIGHT = 36;

    private final SlotContent displayItem;
    private final String[] wikiUrls;

    // True when buttons need to be (re)added to the screen.
    private boolean buttonsDirty = true;

    public SkyblockWikiInfoClientRecipe(ItemStack displayItem, String[] wikiUrls) {
        this.displayItem = displayItem != null && !displayItem.isEmpty()
                ? SlotContent.of(displayItem) : null;
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
        return SkyblockWikiInfoRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (displayItem != null) ctx.bindSlot(0, displayItem);
    }

    @Override
    public List<SlotContent> getIngredients() { return List.of(); }

    @Override
    public List<SlotContent> getResults() {
        return displayItem != null ? List.of(displayItem) : List.of();
    }

    @Override
    public boolean isVisualOnly() { return true; }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) { return false; }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAny(stack, getResults());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
            int mouseX, int mouseY, float partialTicks) {

        if (buttonsDirty) {
            addButtons(screen, pos);
            buttonsDirty = false;
        }
    }

    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        SkyblockRecipeUtil.addWikiButton(
                screen, wikiUrls,
                pos.left() + 22,
                pos.top() + (DISPLAY_HEIGHT - 12) / 2);
    }

    @Override
    public int getPriority() { return SkyblockRecipePriority.WIKI_INFO; }
}