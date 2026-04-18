package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge.SkyblockForgeClientRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SkyblockKatUpgradeClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int BUTTON_ROW_Y_OFFSET = 56;

    private final SlotContent input;
    private final SlotContent output;
    private final SlotContent[] materials;
    private final long coins;
    private final int timeSeconds;

    public SkyblockKatUpgradeClientRecipe(
            SlotContent input, SlotContent output, SlotContent[] materials,
            long coins, int timeSeconds, String[] wikiUrls) {
        super(wikiUrls);
        this.input = input;
        this.output = output;
        this.materials = materials;
        this.coins = coins;
        this.timeSeconds = timeSeconds;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockKatUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (input != null) ctx.bindSlot(0, input);
        for (int i = 0; i < materials.length && i < 4; i++) {
            if (materials[i] != null) {
                ctx.bindOptionalSlot(i + 1, materials[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        gfx.drawString(font, Component.literal("→"), 22, 22, 0xFF404040, false);
        gfx.drawString(font, Component.literal("→"), 80, 22, 0xFF404040, false);

        if (coins > 0) {
            gfx.drawString(font,
                    Component.literal("§6" + SkyblockRecipeUtil.formatNumber(coins) + " coins"),
                    2, 46, 0xFFAA8800, false);
        }
        if (timeSeconds > 0) {
            gfx.drawString(font,
                    Component.literal("§7" + SkyblockForgeClientRecipe.formatDuration(timeSeconds)),
                    90, 46, 0xFF808080, false);
        }
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.KAT_UPGRADE;
    }
}