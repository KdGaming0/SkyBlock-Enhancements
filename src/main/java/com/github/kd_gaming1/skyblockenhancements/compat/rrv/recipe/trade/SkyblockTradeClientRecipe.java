package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SkyblockTradeClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int ARROW_X = 28;
    private static final int ARROW_Y = 13;
    private static final int BUTTON_ROW_Y_OFFSET = 36;

    private final SlotContent cost;
    private final SlotContent result;

    public SkyblockTradeClientRecipe(SlotContent cost, SlotContent result, String[] wikiUrls) {
        super(wikiUrls);
        this.cost = cost;
        this.result = result;
    }

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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(Minecraft.getInstance().font,
                Component.literal("→"), ARROW_X, ARROW_Y, 0xFF404040, false);
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.TRADE;
    }
}