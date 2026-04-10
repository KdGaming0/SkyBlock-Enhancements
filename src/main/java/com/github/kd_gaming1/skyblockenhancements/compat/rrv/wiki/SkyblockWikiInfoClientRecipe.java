package com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;

/**
 * Visual-only client recipe for items with wiki URLs but no other recipe data.
 * Shows the item in a slot with a "Wiki" button.
 */
public class SkyblockWikiInfoClientRecipe implements ReliableClientRecipe {

    private static final int DISPLAY_HEIGHT = 36;

    private final SlotContent displayItem;
    private final String[] wikiUrls;
    private final List<Button> addedButtons = new ArrayList<>();

    public SkyblockWikiInfoClientRecipe(ItemStack displayItem, String[] wikiUrls) {
        this.displayItem = displayItem != null && !displayItem.isEmpty()
                ? SlotContent.of(displayItem) : null;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockWikiInfoRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (displayItem != null) ctx.bindSlot(0, displayItem);
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        return displayItem != null ? List.of(displayItem) : List.of();
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return false;
    }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAny(stack, getResults());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
            int mouseX, int mouseY, float partialTicks) {

        if (!buttonsStillInScreen(screen)) {
            addedButtons.clear();
            addButtons(screen, pos);
        }
    }

    private boolean buttonsStillInScreen(RecipeViewScreen screen) {
        if (addedButtons.isEmpty()) return false;
        return SkyblockRecipeUtil.containsAllByIdentity(screen.children(), addedButtons);
    }

    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnX = pos.left() + 22;
        int btnY = pos.top() + (DISPLAY_HEIGHT - 12) / 2;

        Button wikiBtn = SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, btnX, btnY);
        if (wikiBtn != null) {
            addedButtons.add(wikiBtn);
        }
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.WIKI_INFO;
    }
}