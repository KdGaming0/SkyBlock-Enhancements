package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SkyblockWikiInfoClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int BUTTON_Y_OFFSET = 24;

    private final @Nullable SlotContent displayItem;

    public SkyblockWikiInfoClientRecipe(ItemStack displayItem, String[] wikiUrls) {
        super(wikiUrls);
        this.displayItem = displayItem != null && !displayItem.isEmpty()
                ? SlotContent.of(displayItem) : null;
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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.WIKI_INFO;
    }
}