package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.ArraySlotRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SkyblockForgeClientRecipe extends ArraySlotRecipe
        implements ReliableClientRecipe {

    /** 4-column grid: arrow sits between the grid (ends at 4*18=72) and the output. */
    private static final int ARROW_X = 74;
    private static final int ARROW_Y = 23;
    private static final int ARROW_HIT_W = 12;
    private static final int ARROW_HIT_H = 10;
    private static final int DURATION_X = 2;
    private static final int DURATION_Y = 46;
    private static final int BUTTON_ROW_Y_OFFSET = 56;
    /** Max inputs bindable; mirrors SkyblockForgeServerRecipe.MAX_INPUTS. */
    private static final int MAX_INPUTS = 8;
    /** Output slot index in the bound layout. */
    private static final int OUTPUT_SLOT = MAX_INPUTS;

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final int durationSeconds;
    private final int tierOffset;
    private final String crafttext;
    private final boolean hasCrafttext;
    @Nullable private Component cachedTooltipLine;

    public SkyblockForgeClientRecipe(SlotContent[] inputs, SlotContent output,
                                     int durationSeconds, String[] wikiUrls, String crafttext) {
        super(wikiUrls);
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
        this.tierOffset = SkyblockRecipeUtil.extractTierFromResults(getResults());
        this.crafttext = crafttext != null ? crafttext : "";
        this.hasCrafttext = !this.crafttext.isEmpty();
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockForgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < inputs.length && i < MAX_INPUTS; i++) {
            if (inputs[i] != null) {
                ctx.bindOptionalSlot(i, inputs[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
        }
        if (output != null) ctx.bindSlot(OUTPUT_SLOT, output);
        if (hasCrafttext && SkyblockEnhancementsConfig.showCollectionRequirements) {
            ctx.addAdditionalStackModifier(OUTPUT_SLOT, this::appendRequirementTooltip);
        }
    }

    private void appendRequirementTooltip(ItemStack stack, List<Component> tooltip) {
        tooltip.addLast(Component.literal(""));
        tooltip.addLast(requirementTooltipLine());
    }

    private Component requirementTooltipLine() {
        Component cached = cachedTooltipLine;
        if (cached != null) return cached;
        cached = Component.literal("§cRequirement: §e" + SkyblockRecipeUtil.formatCrafttext(crafttext));
        cachedTooltipLine = cached;
        return cached;
    }

    @Override
    protected SlotContent[] getInputSlots() {
        return inputs;
    }

    @Override
    public List<SlotContent> getResults() {
        return output != null ? List.of(output) : List.of();
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        renderArrow(gfx, ARROW_X, ARROW_Y);
        if (durationSeconds > 0) {
            gfx.drawString(font(), SkyblockRecipeUtil.gray(SkyblockRecipeUtil.formatDuration(durationSeconds)),
                    DURATION_X, DURATION_Y, RecipeColors.DURATION, false);
        }
        if (hasCrafttext && SkyblockEnhancementsConfig.showCollectionRequirements) {
            renderRequirementIndicator(gfx);
            renderArrowTooltipIfHovered(gfx, screen, pos, mouseX, mouseY);
        }
        maintainButtons(screen, pos);
    }

    private void renderRequirementIndicator(GuiGraphics gfx) {
        gfx.drawString(font(), "§c!", ARROW_X + 8, ARROW_Y - 8, RecipeColors.WHITE, false);
    }

    private void renderArrowTooltipIfHovered(GuiGraphics gfx, RecipeViewScreen screen,
                                             RecipePosition pos, int mouseX, int mouseY) {
        if (mouseX < ARROW_X || mouseX >= ARROW_X + ARROW_HIT_W
                || mouseY < ARROW_Y || mouseY >= ARROW_Y + ARROW_HIT_H) {
            return;
        }
        gfx.setComponentTooltipForNextFrame(font(),
                List.of(requirementTooltipLine()),
                pos.left() + mouseX,
                pos.top() + mouseY);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.FORGE + tierOffset;
    }
}
