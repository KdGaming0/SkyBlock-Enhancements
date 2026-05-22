package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.jetbrains.annotations.Nullable;

public class SkyblockEssenceUpgradeClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int HEADER_X  = 62;
    private static final int HEADER_Y  = 2;
    private static final int ARROW_X   = 82;
    private static final int ARROW_Y   = 22;
    private static final int BUTTON_ROW_Y_OFFSET = 60;

    private final SlotContent input;
    private final SlotContent output;
    private final SlotContent essence;
    private final SlotContent[] companions;
    private final int starLevel;
    private final String essenceType;
    private final List<SlotContent> ingredients;

    public SkyblockEssenceUpgradeClientRecipe(
            SlotContent input, SlotContent output, SlotContent essence,
            SlotContent[] companions, int starLevel, String essenceType, String[] wikiUrls) {
        super(wikiUrls);
        this.input = input;
        this.output = output;
        this.essence = essence;
        this.companions = companions;
        this.starLevel = starLevel;
        this.essenceType = essenceType;
        this.ingredients = buildIngredients(input, essence, companions);
    }

    private static List<SlotContent> buildIngredients(SlotContent input, SlotContent essence, SlotContent[] companions) {
        List<SlotContent> list = new ArrayList<>(2 + companions.length);
        if (input != null)   list.add(input);
        if (essence != null) list.add(essence);
        for (SlotContent comp : companions) {
            if (comp != null) list.add(comp);
        }
        return List.copyOf(list);
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockEssenceUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        bindOptional(ctx, 0, input);
        bindOptional(ctx, 1, essence);
        for (int i = 0; i < companions.length && i < 4; i++) {
            bindOptional(ctx, 2 + i, companions[i]);
        }
        bindOptional(ctx, 6, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        return ingredients;
    }

    @Override
    public List<SlotContent> getResults() {
        return output != null ? List.of(output) : List.of();
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(font(),
                SkyblockRecipeUtil.gold("✪".repeat(starLevel) + " §e" + essenceType),
                HEADER_X, HEADER_Y, RecipeColors.WHITE, true);
        renderArrow(gfx, ARROW_X, ARROW_Y);
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected AbstractButton placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.ESSENCE_UPGRADE;
    }
}