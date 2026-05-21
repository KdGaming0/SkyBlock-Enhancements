package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.ArraySlotRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeLayoutConstants;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SkyblockCraftingClientRecipe extends ArraySlotRecipe
        implements ReliableClientRecipe {

    private static final int ARROW_X   = 62;
    private static final int ARROW_Y   = 22;
    private static final int ARROW_HIT_W = 12;
    private static final int ARROW_HIT_H = 10;
    private static final int BUTTON_ROW_Y_OFFSET = 56;

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final int resultTier;
    private final String crafttext;
    private final boolean hasCrafttext;
    @Nullable private Component cachedTooltipLine;
    private final RecipeViewMenu.AdditionalStackModifier requirementModifier;

    public SkyblockCraftingClientRecipe(SlotContent[] inputs, SlotContent output, String[] wikiUrls, String crafttext) {
        super(wikiUrls);
        this.inputs = inputs;
        this.output = output;
        this.resultTier = SkyblockRecipeUtil.extractTierFromResults(getResults());
        this.crafttext = crafttext != null ? crafttext : "";
        this.hasCrafttext = !this.crafttext.isEmpty();
        this.requirementModifier = hasCrafttext ? this::appendRequirementTooltip : null;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockCraftingRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < 9; i++) {
            bindOptional(ctx, i, inputs[i]);
        }
        bindOptional(ctx, 9, output);
        if (requirementModifier != null && SkyblockEnhancementsConfig.showCollectionRequirements) {
            ctx.addAdditionalStackModifier(9, requirementModifier);
        }
    }

    private void appendRequirementTooltip(ItemStack stack, List<Component> tooltip) {
        tooltip.addLast(Component.empty());
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
        int btnY = pos.top() + BUTTON_ROW_Y_OFFSET;
        Button sentinel = placeWikiButton(screen, pos.left(), btnY);

        if (HypixelLocationState.isOnSkyblock()) {
            String itemId = resolveOutputId();
            if (itemId != null) {
                int craftX = (sentinel != null) ? pos.left() + RecipeLayoutConstants.WIKI_BUTTON_WIDTH + RecipeLayoutConstants.BUTTON_GAP + 2 : pos.left();
                Button craftBtn = Button.builder(
                                Component.literal("Craft"),
                                b -> sendViewRecipeCommand(itemId))
                        .pos(craftX, btnY)
                        .size(RecipeLayoutConstants.WIKI_BUTTON_WIDTH, RecipeLayoutConstants.WIKI_BUTTON_HEIGHT)
                        .build();
                screen.addRecipeWidget(craftBtn);
                if (sentinel == null) sentinel = craftBtn;
            }
        }
        return sentinel;
    }

    @Nullable
    private String resolveOutputId() {
        if (output == null || output.isEmpty()) return null;
        List<ItemStack> contents = output.getValidContents();
        if (contents.isEmpty()) return null;
        return SkyblockRecipeUtil.extractSkyblockId(contents.getFirst());
    }

    private static void sendViewRecipeCommand(String itemId) {
        SkyblockRecipeUtil.sendCommand("viewrecipe " + itemId);
    }

    /** Returns the numeric tier extracted from the recipe's result ID (e.g. 5000 for COMPACTOR_5000). */
    public int getResultTier() {
        return resultTier;
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.CRAFTING;
    }
}
