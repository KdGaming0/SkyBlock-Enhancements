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
import java.util.Set;
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
 *
 * <p>Contiguous regions of the same crop are detected and rendered as a single
 * icon centred on the region, eliminating the visual clutter of repeated icons.
 */
public class SkyblockGardenMutationClientRecipe extends AbstractSkyblockClientRecipe {

    // ── layout / sizing constants ─────────────────────────────────────────────

    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_MARGIN_X = 4;
    private static final int INFO_MAX_LINES = 3;

    private static final int EXPANDED_GRID_DIM = 9;
    private static final int EXPANDED_TO_VISIBLE_OFFSET = 1;
    private static final int GRID_SIZE = SkyblockGardenMutationRecipeType.gridSize();

    private static final int NAME_Y_OFFSET = 2;
    private static final int INFO_Y_GAP = 1;
    private static final int BUTTON_TOP_PADDING = 1;

    // mini-legend rendered inside the grid area (bottom-right corner)
    private static final int LEGEND_SQUARE_SIZE = 3;
    private static final int LEGEND_GAP = 1;
    private static final int LEGEND_MARGIN = 2;

    // ── fields ────────────────────────────────────────────────────────────────

    private final GardenMutationLayout layout;

    /** One binding per visible contiguous region. */
    private final List<RegionBinding> regionBindings;

    @Nullable private Component cachedNameLine;
    @Nullable private List<Component> cachedInfoLines;
    @Nullable private RecipeViewMenu.AdditionalStackModifier targetTooltipModifier;

    // ── construction ──────────────────────────────────────────────────────────

    public SkyblockGardenMutationClientRecipe(GardenMutationLayout layout, String[] wikiUrls) {
        super(wikiUrls);
        this.layout = layout;
        this.regionBindings = buildRegionBindings(layout);
    }

    // ── region binding (core of the multi-cell improvement) ───────────────────

    /**
     * One resolved item per visible region.  The {@code slotIndex} is the
     * 6×6 slot where the icon is displayed (may be displaced from the
     * geometric centre when two regions collide).
     */
    private record RegionBinding(
            GardenMutationRegion.Region region,
            ItemStack stack,
            SlotContent content,
            int slotIndex
    ) {}

    private record GridMetrics(int pixelSize, int startX, int startY, int cellSize, int step) {
        static GridMetrics compute() {
            int cellSize = SkyblockGardenMutationRecipeType.cellSize();
            int cellGap = SkyblockGardenMutationRecipeType.cellGap();
            int pixelSize = GRID_SIZE * cellSize + (GRID_SIZE - 1) * cellGap;
            int startX = (SkyblockGardenMutationRecipeType.DISPLAY_WIDTH - pixelSize) / 2;
            int startY = SkyblockGardenMutationRecipeType.gridOffsetY();
            return new GridMetrics(pixelSize, startX, startY, cellSize, cellSize + cellGap);
        }
    }

    /**
     * Detects contiguous regions and creates bindings.
     * Multiblock regions get one binding per contiguous region.
     * Non-multiblock crops get one binding per individual cell.
     * Collisions are resolved by a spiral search for the nearest free slot.
     */
    private static List<RegionBinding> buildRegionBindings(GardenMutationLayout layout) {
        List<GardenMutationRegion.Region> allRegions = GardenMutationRegion.detectRegions(layout);
        if (allRegions.isEmpty()) {
            return List.of();
        }

        Set<String> multiblockCrops = layout.multiblockCrops();
        List<GardenMutationRegion.Region> multiblockRegions = GardenMutationRegion.filterMultiblockRegions(allRegions, multiblockCrops);

        boolean[] usedSlots = new boolean[GRID_SIZE * GRID_SIZE];
        List<RegionBinding> bindings = new ArrayList<>();

        // First: bind multiblock regions (one slot per contiguous region)
        for (GardenMutationRegion.Region region : multiblockRegions) {
            if (!region.isVisible()) continue;
            ItemStack stack = resolveStackForRegion(layout, region);
            if (stack == null || stack.isEmpty()) continue;
            int preferredSlot = region.visibleCenterRow() * GRID_SIZE + region.visibleCenterCol();
            int slot = resolveSlotCollision(preferredSlot, usedSlots);
            usedSlots[slot] = true;
            bindings.add(new RegionBinding(region, stack, SlotContent.of(stack), slot));
        }

        // Second: bind individual cells for non-multiblock crops
        for (GardenMutationRegion.Region region : allRegions) {
            if (!region.isVisible() || region.isMultiblockRegion(multiblockCrops)) continue;
            // Non-multiblock: bind each cell individually
            ItemStack stack = resolveStackForRegion(layout, region);
            if (stack == null || stack.isEmpty()) continue;
            for (int idx : region.cellIndices()) {
                int row9 = idx / EXPANDED_GRID_DIM;
                int col9 = idx % EXPANDED_GRID_DIM;
                int row6 = row9 - EXPANDED_TO_VISIBLE_OFFSET;
                int col6 = col9 - EXPANDED_TO_VISIBLE_OFFSET;
                if (row6 < 0 || row6 >= GRID_SIZE || col6 < 0 || col6 >= GRID_SIZE) continue;
                int slot = row6 * GRID_SIZE + col6;
                if (usedSlots[slot]) continue;
                usedSlots[slot] = true;
                bindings.add(new RegionBinding(region, stack, SlotContent.of(stack), slot));
            }
        }

        return Collections.unmodifiableList(bindings);
    }

    private static ItemStack resolveStackForRegion(
            GardenMutationLayout layout,
            GardenMutationRegion.Region region
    ) {
        return switch (region.type()) {
            case TARGET -> resolveTargetStack(layout);
            case INGREDIENT -> resolveIngredientStack(region.itemId());
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack resolveTargetStack(GardenMutationLayout layout) {
        NeuItem item = NeuItemRegistry.get(layout.mutationId());
        if (item != null) {
            return ItemStackBuilder.build(item).copy();
        }
        ItemStack fallback = new ItemStack(Items.BARRIER);
        fallback.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§c" + layout.mutationId()));
        return fallback;
    }

    private static ItemStack resolveIngredientStack(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStackBuilder.buildIngredient(itemId, 1);
    }

    /**
     * Spiral search starting from {@code preferred} to find the nearest
     * unused slot.  Every slot is guaranteed free after at most 36 steps.
     */
    private static int resolveSlotCollision(int preferred, boolean[] used) {
        if (preferred >= 0 && preferred < used.length && !used[preferred]) {
            return preferred;
        }

        int row = preferred / GRID_SIZE;
        int col = preferred % GRID_SIZE;

        for (int radius = 1; radius < GRID_SIZE; radius++) {
            for (int dr = -radius; dr <= radius; dr++) {
                for (int dc = -radius; dc <= radius; dc++) {
                    if (Math.abs(dr) != radius && Math.abs(dc) != radius) {
                        continue; // only check the perimeter of the square
                    }
                    int nr = row + dr;
                    int nc = col + dc;
                    int idx = nr * GRID_SIZE + nc;
                    if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE && !used[idx]) {
                        return idx;
                    }
                }
            }
        }

        // Absolute fallback — linear scan (should never reach here)
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) return i;
        }
        return 0;
    }

    // ── RRV contract ──────────────────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockGardenMutationRecipeType.INSTANCE;
    }

    private static final RecipeViewMenu.OptionalSlotRenderer NO_BG_RENDERER = (gfx, x, y, pt) -> {};

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (RegionBinding binding : regionBindings) {
            ctx.bindOptionalSlot(binding.slotIndex(), binding.content(), NO_BG_RENDERER);
        }

        RegionBinding targetBinding = findTargetBinding();
        if (targetBinding == null) {
            return;
        }

        if (targetTooltipModifier == null) {
            targetTooltipModifier = (stack, tooltip) -> {
                tooltip.addLast(Component.empty());
                tooltip.addLast(Component.literal("§7(" + layout.name() + " Target)"));
            };
        }
        ctx.addAdditionalStackModifier(targetBinding.slotIndex(), targetTooltipModifier);
    }

    @Nullable
    private RegionBinding findTargetBinding() {
        for (RegionBinding binding : regionBindings) {
            if (binding.region().type() == GardenMutationLayout.CellType.TARGET) {
                return binding;
            }
        }
        return null;
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> out = new ArrayList<>();
        for (RegionBinding binding : regionBindings) {
            if (binding.region().type() == GardenMutationLayout.CellType.INGREDIENT) {
                out.add(binding.content());
            }
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    @Override
    public List<SlotContent> getResults() {
        RegionBinding target = findTargetBinding();
        return target != null ? List.of(target.content()) : List.of();
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.MUTATION;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        renderMetadata(gfx);
        renderGrid(gfx);
        renderRegionHighlights(gfx);
        renderLegend(gfx);
        renderInfo(gfx);
        maintainButtons(screen, pos);
    }

    private void renderMetadata(GuiGraphics gfx) {
        Component name = cachedNameLine;
        if (name == null) {
            name = Component.literal("§f" + layout.name());
            cachedNameLine = name;
        }
        gfx.drawString(font(), name, TEXT_MARGIN_X, NAME_Y_OFFSET, RecipeColors.WHITE, true);
    }

    private void renderGrid(GuiGraphics gfx) {
        GridMetrics m = GridMetrics.compute();
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int nineIdx = (row + EXPANDED_TO_VISIBLE_OFFSET) * EXPANDED_GRID_DIM
                        + (col + EXPANDED_TO_VISIBLE_OFFSET);
                GardenMutationLayout.Cell cell = layout.grid()[nineIdx];
                int x = m.startX() + col * m.step();
                int y = m.startY() + row * m.step();
                drawCellBackground(gfx, x, y, m.cellSize(), cell.type());
            }
        }
    }

    private static void drawCellBackground(GuiGraphics gfx, int x, int y, int size,
                                           GardenMutationLayout.CellType type) {
        int bgColor = switch (type) {
            case EMPTY -> RecipeColors.GRID_EMPTY_BG;
            case TARGET -> RecipeColors.GRID_TARGET_BG;
            case INGREDIENT -> RecipeColors.GRID_INGREDIENT_BG;
        };

        gfx.fill(x, y, x + size, y + size, bgColor);
        drawCellBorder(gfx, x, y, size);
    }

    private static void drawCellBorder(GuiGraphics gfx, int x, int y, int size) {
        gfx.fill(x, y, x + size, y + 1, RecipeColors.GRID_BORDER);
        gfx.fill(x, y + size - 1, x + size, y + size, RecipeColors.GRID_BORDER);
        gfx.fill(x, y, x + 1, y + size, RecipeColors.GRID_BORDER);
        gfx.fill(x + size - 1, y, x + size, y + size, RecipeColors.GRID_BORDER);
    }

    /**
     * Overlays a semi-transparent highlight on multi-cell regions so the
     * player can visually see which cells belong to the same crop.
     */
    private void renderRegionHighlights(GuiGraphics gfx) {
        GridMetrics m = GridMetrics.compute();
        for (RegionBinding binding : regionBindings) {
            GardenMutationRegion.Region region = binding.region();
            if (region.isSingleCell()) continue;
            renderRegionHighlight(gfx, region, m);
        }
    }

    private static void renderRegionHighlight(GuiGraphics gfx, GardenMutationRegion.Region region,
                                              GridMetrics m) {
        int visMinRow = Integer.MAX_VALUE, visMinCol = Integer.MAX_VALUE;
        int visMaxRow = Integer.MIN_VALUE, visMaxCol = Integer.MIN_VALUE;
        boolean hasVisible = false;

        for (int idx : region.cellIndices()) {
            int row9 = idx / EXPANDED_GRID_DIM;
            int col9 = idx % EXPANDED_GRID_DIM;
            int row6 = row9 - EXPANDED_TO_VISIBLE_OFFSET;
            int col6 = col9 - EXPANDED_TO_VISIBLE_OFFSET;
            if (row6 < 0 || row6 >= GRID_SIZE || col6 < 0 || col6 >= GRID_SIZE) continue;
            hasVisible = true;
            visMinRow = Math.min(visMinRow, row6);
            visMinCol = Math.min(visMinCol, col6);
            visMaxRow = Math.max(visMaxRow, row6);
            visMaxCol = Math.max(visMaxCol, col6);
        }
        if (!hasVisible) return;

        int left   = m.startX() + visMinCol * m.step();
        int top    = m.startY() + visMinRow * m.step();
        int right  = m.startX() + (visMaxCol + 1) * m.step() - m.step() + m.cellSize();
        int bottom = m.startY() + (visMaxRow + 1) * m.step() - m.step() + m.cellSize();
        gfx.fill(left, top, right, bottom, RecipeColors.REGION_HIGHLIGHT);
    }

    // ── Info text ─────────────────────────────────────────────────────────────

    private void renderInfo(GuiGraphics gfx) {
        List<Component> lines = cachedInfoLines;
        if (lines == null) {
            lines = buildInfoLines();
            cachedInfoLines = lines;
        }
        if (lines.isEmpty()) {
            return;
        }

        int y = computeGridBottom() + INFO_Y_GAP;
        int limit = Math.min(lines.size(), INFO_MAX_LINES);
        for (int i = 0; i < limit; i++) {
            gfx.drawString(font(), lines.get(i), TEXT_MARGIN_X, y, RecipeColors.WHITE, true);
            y += LINE_HEIGHT;
        }
    }

    private List<Component> buildInfoLines() {
        List<Component> lines = new ArrayList<>();

        // Line 1: Surface | Size | Water | Stages
        String water = layout.needsWater() ? "§b[W]" : "";
        String stages = layout.stages() > 0 ? " S:" + layout.stages() : "";
        lines.add(Component.literal(
                "§7" + layout.surface() + " | " + layout.gridSize() + "x" + layout.gridSize()
                        + water + "§7" + stages));

        // Line 2: Cost and Reward (compact format)
        String cost = SkyblockRecipeUtil.formatNumber(layout.costCoins());
        lines.add(Component.literal("§6C:§e" + cost + " §7R:§c+" + layout.rewardCopper()));

        // Line 3: Spreading condition / required for / effect
        addThirdInfoLine(lines);

        return Collections.unmodifiableList(lines);
    }

    private void addThirdInfoLine(List<Component> lines) {
        List<GardenMutationLayout.SpreadingCondition> conds = layout.spreadingConditions();
        if (!conds.isEmpty()) {
            lines.add(Component.literal("§7Spread: §e" + conds.getFirst().text()));
            return;
        }
        if (!layout.requiredFor().isEmpty()) {
            lines.add(Component.literal("§7For: §e" + layout.requiredFor().getFirst()));
            return;
        }
        if (!layout.effects().isEmpty()) {
            GardenMutationLayout.Effect eff = layout.effects().getFirst();
            lines.add(Component.literal("§7Effect: §e" + eff.name()));
        }
    }

    // ── Mini legend (rendered inside grid, bottom-right corner) ───────────────

    /**
     * Draws two tiny coloured squares in the bottom-right of the grid area
     * to serve as a visual key.  This costs zero extra vertical space.
     */
    private void renderLegend(GuiGraphics gfx) {
        GridMetrics m = GridMetrics.compute();
        int baseX = m.startX() + m.pixelSize() - LEGEND_MARGIN - LEGEND_SQUARE_SIZE;
        int baseY = m.startY() + m.pixelSize() - LEGEND_MARGIN - LEGEND_SQUARE_SIZE;
        int blueX = baseX - LEGEND_SQUARE_SIZE - LEGEND_GAP;
        gfx.fill(blueX, baseY, blueX + LEGEND_SQUARE_SIZE, baseY + LEGEND_SQUARE_SIZE,
                RecipeColors.GRID_TARGET_BORDER);
        gfx.fill(baseX, baseY, baseX + LEGEND_SQUARE_SIZE, baseY + LEGEND_SQUARE_SIZE,
                RecipeColors.GRID_INGREDIENT_BORDER);
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int y = computeGridBottom() + INFO_Y_GAP;
        int infoLineCount = cachedInfoLines != null
                ? Math.min(cachedInfoLines.size(), INFO_MAX_LINES)
                : 0;
        y += infoLineCount * LINE_HEIGHT;
        if (infoLineCount > 0) {
            y += BUTTON_TOP_PADDING;
        }

        int btnX = (SkyblockGardenMutationRecipeType.DISPLAY_WIDTH
                - RecipeLayoutConstants.WIKI_BUTTON_WIDTH) / 2;
        return placeWikiButton(screen, pos.left() + btnX, pos.top() + y);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static int computeGridPixelSize() {
        int gridSize = SkyblockGardenMutationRecipeType.gridSize();
        int cellSize = SkyblockGardenMutationRecipeType.cellSize();
        int cellGap = SkyblockGardenMutationRecipeType.cellGap();
        return gridSize * cellSize + (gridSize - 1) * cellGap;
    }

    private static int computeGridBottom() {
        return SkyblockGardenMutationRecipeType.gridOffsetY()
                + computeGridPixelSize();
    }
}
