package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SkyblockForgeClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int ARROW_X = 68;
    private static final int ARROW_Y = 22;
    private static final int DURATION_X = 2;
    private static final int DURATION_Y = 46;
    private static final int BUTTON_ROW_Y_OFFSET = 56;

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final int durationSeconds;
    private final int tierOffset;

    public SkyblockForgeClientRecipe(SlotContent[] inputs, SlotContent output,
                                     int durationSeconds, String[] wikiUrls) {
        super(wikiUrls);
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
        this.tierOffset = SkyblockRecipeUtil.extractTierFromResults(getResults());
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockForgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < inputs.length && i < 6; i++) {
            if (inputs[i] != null) {
                ctx.bindOptionalSlot(i, inputs[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        gfx.drawString(font, Component.literal("→"), ARROW_X, ARROW_Y, 0xFF404040, false);
        if (durationSeconds > 0) {
            gfx.drawString(font, Component.literal("§7" + formatDuration(durationSeconds)),
                    DURATION_X, DURATION_Y, 0xFF808080, false);
        }
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    /** Formats a second count as {@code "1h 30m"} / {@code "5m"} / {@code "45s"}. */
    public static String formatDuration(int seconds) {
        if (seconds >= 3600) {
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            return m > 0 ? h + "h " + m + "m" : h + "h";
        }
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.FORGE + tierOffset;
    }
}