package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeLayoutConstants;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side garden mutation recipe card.
 *
 * <p>Shows a compact card (140×146) with the mutation name, a 6×6 grid that
 * displays the central area of the 9×9 expanded layout, two lines of info,
 * and a wiki button.  Everything fits inside RRV's 214 px viewport.
 */
public class SkyblockGardenMutationClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_MARGIN_X = 4;
    private static final int INFO_MAX_LINES = 3;

    /** The 9×9 layout is stored expanded; we only render the central 6×6 area. */
    private static final int NINE = 9;
    /** Row/col offset to map the central 6×6 of 9×9 to our 0-based 6×6 slots. */
    private static final int NINE_TO_SIX_OFFSET = 1;

    private final GardenMutationLayout layout;

    /** Resolved stacks for each of the 81 expanded cells; EMPTY cells are null. */
    @Nullable private final ItemStack[] resolvedStacks;
    /** Slot contents for binding; parallel to resolvedStacks. */
    @Nullable private final SlotContent[] slotContents;
    /** Indices of cells that have bound slots, for fast iteration. */
    private final int[] boundSlotIndices;
    private final int boundSlotCount;

    @Nullable private Component cachedNameLine;
    @Nullable private List<Component> cachedInfoLines;
    @Nullable private RecipeViewMenu.AdditionalStackModifier targetTooltipModifier;

    public SkyblockGardenMutationClientRecipe(GardenMutationLayout layout, String[] wikiUrls) {
        super(wikiUrls);
        this.layout = layout;

        ItemStack[] stacks = new ItemStack[NINE * NINE];
        SlotContent[] contents = new SlotContent[NINE * NINE];
        int[] indices = new int[NINE * NINE];
        int count = 0;

        for (int i = 0; i < NINE * NINE; i++) {
            GardenMutationLayout.Cell cell = layout.grid()[i];
            if (cell.type() == GardenMutationLayout.CellType.EMPTY) {
                continue;
            }

            ItemStack stack;
            if (cell.type() == GardenMutationLayout.CellType.TARGET) {
                stack = resolveTargetStack();
            } else {
                stack = resolveIngredientStack(cell.itemId());
            }

            if (stack != null && !stack.isEmpty()) {
                stacks[i] = stack;
                contents[i] = SlotContent.of(stack);
                indices[count++] = i;
            }
        }

        this.resolvedStacks = stacks;
        this.slotContents = contents;
        this.boundSlotIndices = indices;
        this.boundSlotCount = count;
    }

    private ItemStack resolveTargetStack() {
        NeuItem item = NeuItemRegistry.get(layout.mutationId());
        if (item != null) {
            return ItemStackBuilder.build(item).copy();
        }
        ItemStack fallback = new ItemStack(Items.BARRIER);
        fallback.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§c" + layout.mutationId()));
        return fallback;
    }

    private ItemStack resolveIngredientStack(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStackBuilder.buildIngredient(itemId, 1);
    }

    // ── ReliableClientRecipe core ──────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockGardenMutationRecipeType.INSTANCE;
    }

    private static final RecipeViewMenu.OptionalSlotRenderer NO_BG_RENDERER = (gfx, x, y, pt) -> {};

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < boundSlotCount; i++) {
            int nineIdx = boundSlotIndices[i];
            int row9 = nineIdx / NINE;
            int col9 = nineIdx % NINE;

            // Skip cells outside the central 6×6 area of the 9×9 layout
            if (row9 < NINE_TO_SIX_OFFSET || row9 >= NINE_TO_SIX_OFFSET + SkyblockGardenMutationRecipeType.gridSize()
                    || col9 < NINE_TO_SIX_OFFSET || col9 >= NINE_TO_SIX_OFFSET + SkyblockGardenMutationRecipeType.gridSize()) {
                continue;
            }

            int slotIndex = (row9 - NINE_TO_SIX_OFFSET) * SkyblockGardenMutationRecipeType.gridSize()
                    + (col9 - NINE_TO_SIX_OFFSET);
            SlotContent content = slotContents[nineIdx];
            if (content != null && !content.isEmpty()) {
                ctx.bindOptionalSlot(slotIndex, content, NO_BG_RENDERER);
            }
        }

        int centerSlot = (SkyblockGardenMutationRecipeType.gridSize() / 2) * SkyblockGardenMutationRecipeType.gridSize()
                + (SkyblockGardenMutationRecipeType.gridSize() / 2);
        if (targetTooltipModifier == null) {
            String displayName = layout.name();
            targetTooltipModifier = (stack, tooltip) -> {
                tooltip.addLast(Component.empty());
                tooltip.addLast(Component.literal("§7(" + displayName + " Target)"));
            };
        }
        ctx.addAdditionalStackModifier(centerSlot, targetTooltipModifier);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> out = new ArrayList<>(boundSlotCount);
        for (int i = 0; i < boundSlotCount; i++) {
            int idx = boundSlotIndices[i];
            GardenMutationLayout.Cell cell = layout.grid()[idx];
            if (cell.type() == GardenMutationLayout.CellType.INGREDIENT) {
                SlotContent sc = slotContents[idx];
                if (sc != null && !sc.isEmpty()) out.add(sc);
            }
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    @Override
    public List<SlotContent> getResults() {
        for (int i = 0; i < boundSlotCount; i++) {
            int idx = boundSlotIndices[i];
            if (layout.grid()[idx].type() == GardenMutationLayout.CellType.TARGET) {
                SlotContent sc = slotContents[idx];
                if (sc != null && !sc.isEmpty()) return List.of(sc);
            }
        }
        return List.of();
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.MUTATION;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        renderMetadata(gfx);
        renderGrid(gfx, mouseX, mouseY);
        renderInfo(gfx);
        maintainButtons(screen, pos);
    }

    private void renderMetadata(GuiGraphics gfx) {
        Component name = cachedNameLine;
        if (name == null) {
            name = Component.literal("§f" + layout.name());
            cachedNameLine = name;
        }
        gfx.drawString(font(), name, TEXT_MARGIN_X, 2, RecipeColors.WHITE, true);
    }

    private void renderGrid(GuiGraphics gfx, int mouseX, int mouseY) {
        int gridPixelSize = SkyblockGardenMutationRecipeType.gridSize()
                * (SkyblockGardenMutationRecipeType.cellSize() + SkyblockGardenMutationRecipeType.cellGap());
        int startX = (SkyblockGardenMutationRecipeType.DISPLAY_WIDTH - gridPixelSize) / 2;
        int startY = SkyblockGardenMutationRecipeType.gridOffsetY();
        int cellSize = SkyblockGardenMutationRecipeType.cellSize();
        int step = cellSize + SkyblockGardenMutationRecipeType.cellGap();

        for (int row = 0; row < SkyblockGardenMutationRecipeType.gridSize(); row++) {
            for (int col = 0; col < SkyblockGardenMutationRecipeType.gridSize(); col++) {
                int nineIdx = (row + NINE_TO_SIX_OFFSET) * NINE + (col + NINE_TO_SIX_OFFSET);
                GardenMutationLayout.Cell cell = layout.grid()[nineIdx];
                int x = startX + col * step;
                int y = startY + row * step;

                int bgColor = switch (cell.type()) {
                    case EMPTY -> RecipeColors.GRID_EMPTY_BG;
                    case TARGET -> RecipeColors.GRID_TARGET_BG;
                    case INGREDIENT -> RecipeColors.GRID_INGREDIENT_BG;
                };

                gfx.fill(x, y, x + cellSize, y + cellSize, bgColor);
                gfx.fill(x, y, x + cellSize, y + 1, RecipeColors.GRID_BORDER);
                gfx.fill(x, y + cellSize - 1, x + cellSize, y + cellSize, RecipeColors.GRID_BORDER);
                gfx.fill(x, y, x + 1, y + cellSize, RecipeColors.GRID_BORDER);
                gfx.fill(x + cellSize - 1, y, x + cellSize, y + cellSize, RecipeColors.GRID_BORDER);
            }
        }
    }

    private void renderInfo(GuiGraphics gfx) {
        List<Component> lines = cachedInfoLines;
        if (lines == null) {
            lines = buildInfoLines();
            cachedInfoLines = lines;
        }
        if (lines.isEmpty()) return;

        int gridBottom = SkyblockGardenMutationRecipeType.gridOffsetY()
                + SkyblockGardenMutationRecipeType.gridSize()
                        * (SkyblockGardenMutationRecipeType.cellSize() + SkyblockGardenMutationRecipeType.cellGap());
        int y = gridBottom + 1;

        int limit = Math.min(lines.size(), INFO_MAX_LINES);
        for (int i = 0; i < limit; i++) {
            gfx.drawString(font(), lines.get(i), TEXT_MARGIN_X, y, RecipeColors.WHITE, true);
            y += LINE_HEIGHT;
        }
    }

    private List<Component> buildInfoLines() {
        List<Component> lines = new ArrayList<>();

        // Line 1: surface + water icon
        String water = layout.needsWater() ? " §b💧" : "";
        lines.add(Component.literal("§7" + layout.surface() + water));

        // Line 2: cost + reward (very compact)
        String cost = SkyblockRecipeUtil.formatNumber(layout.costCoins());
        lines.add(Component.literal("§7C:§6" + cost + " §7R:§c+" + layout.rewardCopper()));

        // Line 3: first spreading condition, or first effect, or required-for
        List<GardenMutationLayout.SpreadingCondition> conds = layout.spreadingConditions();
        if (!conds.isEmpty()) {
            lines.add(Component.literal("§7Spread: §e" + conds.get(0).text()));
        } else if (!layout.requiredFor().isEmpty()) {
            lines.add(Component.literal("§7For: §e" + layout.requiredFor().get(0)));
        } else if (!layout.effects().isEmpty()) {
            GardenMutationLayout.Effect eff = layout.effects().get(0);
            lines.add(Component.literal("§7Effect: §e" + eff.name()));
        }

        return Collections.unmodifiableList(lines);
    }

    // ── Buttons ────────────────────────────────────────────────────────────────

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int gridBottom = SkyblockGardenMutationRecipeType.gridOffsetY()
                + SkyblockGardenMutationRecipeType.gridSize()
                        * (SkyblockGardenMutationRecipeType.cellSize() + SkyblockGardenMutationRecipeType.cellGap());
        int infoHeight = Math.min(cachedInfoLines != null ? cachedInfoLines.size() : 0, INFO_MAX_LINES);
        infoHeight *= LINE_HEIGHT;
        if (infoHeight > 0) infoHeight += 1; // padding after info

        int btnY = gridBottom + 1 + infoHeight + 1;
        int btnX = (SkyblockGardenMutationRecipeType.DISPLAY_WIDTH - RecipeLayoutConstants.WIKI_BUTTON_WIDTH) / 2;
        return placeWikiButton(screen, pos.left() + btnX, pos.top() + btnY);
    }
}
