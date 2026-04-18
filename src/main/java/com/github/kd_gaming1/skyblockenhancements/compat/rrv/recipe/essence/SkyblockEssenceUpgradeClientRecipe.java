package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SkyblockEssenceUpgradeClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int HEADER_X  = 62;
    private static final int HEADER_Y  = 2;
    private static final int ARROW_X   = 82;
    private static final int ARROW_Y   = 22;
    private static final int BUTTON_ROW_Y_OFFSET = 56;

    private final SlotContent input;
    private final SlotContent output;
    private final SlotContent essence;
    private final SlotContent[] companions;
    private final int starLevel;
    private final String essenceType;

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
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockEssenceUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (input != null)   ctx.bindSlot(0, input);
        if (essence != null) ctx.bindSlot(1, essence);
        for (int i = 0; i < companions.length && i < 4; i++) {
            if (companions[i] != null) {
                ctx.bindOptionalSlot(2 + i, companions[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
        }
        if (output != null) ctx.bindSlot(6, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        if (input != null)   list.add(input);
        if (essence != null) list.add(essence);
        for (SlotContent comp : companions) {
            if (comp != null) list.add(comp);
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return output != null ? List.of(output) : List.of();
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        gfx.drawString(font,
                Component.literal("§6" + "✪".repeat(starLevel) + " §e" + essenceType),
                HEADER_X, HEADER_Y, 0xFFFFFF, true);
        gfx.drawString(font, Component.literal("→"), ARROW_X, ARROW_Y, 0xFF404040, false);
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.ESSENCE_UPGRADE;
    }
}