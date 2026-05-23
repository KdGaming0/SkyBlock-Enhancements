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
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side garden mutation recipe card.
 *
 * <p>Shows a compact card (140×146) with the mutation name, a 6×6 grid that
 * displays the central area of the 9×9 expanded layout, three lines of info,
 * and a wiki button. Everything fits inside RRV's 214 px viewport.
 *
 * <p>Multi-block crops are identified by the global {@code cropSizes} index
 * (see {@link GardenMutationLayout#setGlobalCropSizes}). The renderer scans
 * the grid in line order and matches exact rectangles for crops listed in
 * that index; everything else renders as individual cells.
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

    // ── effect rendering ──────────────────────────────────────────────────────

    /** Maximum number of effect names shown inline on line 3 before truncating. */
    private static final int MAX_INLINE_EFFECTS = 3;

    /** Characters of a special mechanic text shown on line 3 before truncating. */
    private static final int MECHANIC_TRUNCATE_LENGTH = 22;

    private static final Set<String> NEGATIVE_EFFECTS =
            Set.of("Harvest Loss", "XP Loss", "Water Drain");

    // ── dashed border constants ───────────────────────────────────────────────

    /** Dash segment length in pixels for the on-phase. */
    private static final int DASH_ON = 4;
    /** Gap segment length in pixels for the off-phase. */
    private static final int DASH_OFF = 2;
    /** Stroke width of the dashed border in pixels. */
    private static final int DASH_STROKE = 2;

    // ── tooltip separator ─────────────────────────────────────────────────────

    private static final Component SECTION_SEPARATOR =
            Component.literal("§8§m─────────────§r");

    // ── fields ────────────────────────────────────────────────────────────────

    private final GardenMutationLayout layout;

    /** One binding per visible region (multi-block or single-cell). */
    private final List<RegionBinding> regionBindings;

    @Nullable private Component cachedNameLine;
    @Nullable private List<Component> cachedInfoLines;
    @Nullable private List<Component> cachedInfoTooltip;
    @Nullable private RecipeViewMenu.AdditionalStackModifier targetTooltipModifier;

    // ── construction ──────────────────────────────────────────────────────────

    public SkyblockGardenMutationClientRecipe(GardenMutationLayout layout, String[] wikiUrls) {
        super(wikiUrls);
        this.layout = layout;
        this.regionBindings = buildRegionBindings(layout);
    }

    // ── region binding (scanline-order rectangle detection) ───────────────────

    /**
     * One resolved item per visible region. The {@code slotIndex} is the
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
     * Creates slot bindings using scanline-order rectangle detection.
     * Multi-block regions (from the global {@code cropSizes} table) get one
     * binding with a centred icon; single-cell regions get one binding each.
     * Collisions are resolved by a spiral search for the nearest free slot.
     */
    private static List<RegionBinding> buildRegionBindings(GardenMutationLayout layout) {
        List<GardenMutationRegion.Region> allRegions = GardenMutationRegion.detectRegions(layout);
        if (allRegions.isEmpty()) {
            return List.of();
        }

        // Separate: multi-block regions need collision resolution for their centre slot,
        // single-cell regions bind at their exact grid position.
        List<GardenMutationRegion.Region> multiblock = new ArrayList<>();
        List<GardenMutationRegion.Region> singleCell = new ArrayList<>();
        for (GardenMutationRegion.Region region : allRegions) {
            if (!region.isVisible()) continue;
            if (region.isMultiblock()) {
                multiblock.add(region);
            } else {
                singleCell.add(region);
            }
        }

        boolean[] usedSlots = new boolean[GRID_SIZE * GRID_SIZE];
        List<RegionBinding> bindings = new ArrayList<>();

        // First: bind multi-block regions (one icon per rectangular placement)
        for (GardenMutationRegion.Region region : multiblock) {
            ItemStack stack = resolveStackForRegion(layout, region);
            if (stack == null || stack.isEmpty()) continue;
            int preferredSlot = region.visibleCenterRow() * GRID_SIZE + region.visibleCenterCol();
            int slot = resolveSlotCollision(preferredSlot, usedSlots);
            usedSlots[slot] = true;
            bindings.add(new RegionBinding(region, stack, SlotContent.of(stack), slot));
        }

        // Second: bind single-cell regions (one icon per cell)
        for (GardenMutationRegion.Region region : singleCell) {
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
     * unused slot. Every slot is guaranteed free after at most 36 steps.
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
            targetTooltipModifier = buildTargetTooltipModifier();
        }
        ctx.addAdditionalStackModifier(targetBinding.slotIndex(), targetTooltipModifier);

        // Ingredient tooltip modifiers — show qty and occupancy for multi-cell ingredients
        for (RegionBinding binding : regionBindings) {
            if (binding.region().type() != GardenMutationLayout.CellType.INGREDIENT) continue;
            String itemId = binding.region().itemId();
            if (itemId == null) continue;
            int count = layout.countIngredientOccurrences(itemId);
            if (count <= 1 && binding.region().isSingleCell()) continue;
            ctx.addAdditionalStackModifier(binding.slotIndex(), (stack, tooltip) -> {
                tooltip.addLast(Component.empty());
                tooltip.addLast(Component.literal("§7Qty: §e" + count + "§7 in layout"));
                GardenMutationRegion.Region region = binding.region();
                if (region.isMultiblock()) {
                    int w = region.width();
                    int h = region.height();
                    tooltip.addLast(Component.literal(
                            "§7Occupies §e" + w + "x" + h + "§7 cells"));
                }
            });
        }
    }

    private RecipeViewMenu.AdditionalStackModifier buildTargetTooltipModifier() {
        return (stack, tooltip) -> {
            tooltip.addLast(Component.empty());
            tooltip.addLast(Component.literal("§7(" + layout.name() + " Target)"));
            if (!layout.effects().isEmpty()) {
                for (GardenMutationLayout.Effect effect : layout.effects()) {
                    boolean negative = NEGATIVE_EFFECTS.contains(effect.name());
                    String arrow = negative ? "§c▼ " : "§a▲ ";
                    tooltip.addLast(Component.literal(arrow + "§f" + effect.name()));
                }
            }
            if (layout.stages() > 0) {
                tooltip.addLast(Component.literal("§7Stages: §e" + layout.stages()));
            } else {
                tooltip.addLast(Component.literal("§7Stages: §e∞"));
            }
            tooltip.addLast(Component.literal("§7Surface: §e" + layout.surface()));
        };
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
        renderSurfaceIndicator(gfx);
        renderWaterIndicator(gfx);
        renderGrid(gfx);
        renderRegionDashedBorders(gfx);
        renderDimensionLabels(gfx);
        renderLegend(gfx);
        renderInfo(gfx);
        maintainButtons(screen, pos);
        // Tooltip last so it renders above all other content
        renderInfoTooltip(gfx, mouseX, mouseY);
    }

    // ── Metadata (mutation name with rarity colour) ───────────────────────────

    private void renderMetadata(GuiGraphics gfx) {
        Component name = cachedNameLine;
        if (name == null) {
            NeuItem item = NeuItemRegistry.get(layout.mutationId());
            String nameText = (item != null && item.displayName != null)
                    ? item.displayName
                    : rarityColorCode(layout.rarity()) + layout.name();
            name = Component.literal(nameText);
            cachedNameLine = name;
        }
        gfx.drawString(font(), name, TEXT_MARGIN_X, NAME_Y_OFFSET, RecipeColors.WHITE, true);
    }

    private static String rarityColorCode(String rarity) {
        return switch (rarity != null ? rarity.toUpperCase() : "") {
            case "UNCOMMON"  -> "§a";
            case "RARE"      -> "§9";
            case "EPIC"      -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC"    -> "§d";
            case "DIVINE"    -> "§b";
            case "SPECIAL"   -> "§c";
            default          -> "§f";
        };
    }

    // ── Surface indicator (block texture, top-left of grid) ───────────────────

    private void renderSurfaceIndicator(GuiGraphics gfx) {
        GridMetrics m = GridMetrics.compute();
        ItemStack surfaceStack = surfaceItemStack(layout.surface());
        if (surfaceStack.isEmpty()) return;

        int x = m.startX() - 9;
        int y = m.startY() - 1;
        // Render at half scale (16×16 → 8×8)
        gfx.pose().pushMatrix();
        gfx.pose().translate(x, y);
        gfx.pose().scale(0.5f, 0.5f);
        gfx.renderItem(surfaceStack, 0, 0);
        gfx.pose().popMatrix();
    }

    private static ItemStack surfaceItemStack(String surface) {
        return switch (surface) {
            case "Farmland"  -> new ItemStack(Items.DIRT);
            case "Soul Sand" -> new ItemStack(Items.SOUL_SAND);
            case "End Stone" -> new ItemStack(Items.END_STONE);
            case "Sand"      -> new ItemStack(Items.SAND);
            default          -> new ItemStack(Items.DIRT);
        };
    }

    // ── Water indicator (4×4 cyan droplet, top-right of grid) ─────────────────

    private void renderWaterIndicator(GuiGraphics gfx) {
        if (!layout.needsWater()) return;
        GridMetrics m = GridMetrics.compute();
        int x = m.startX() + m.pixelSize() + 1;
        int y = m.startY() + 2;
        gfx.fill(x, y, x + 4, y + 4, RecipeColors.WATER_DROPLET_FILL);
        gfx.fill(x + 1, y + 1, x + 2, y + 2, RecipeColors.WATER_DROPLET_HIGHLIGHT);
    }

    // ── Grid rendering with checkerboard texture ──────────────────────────────

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
        renderCheckerboard(gfx, m);
    }

    private static void drawCellBackground(GuiGraphics gfx, int x, int y, int size,
                                           GardenMutationLayout.CellType type) {
        int bgColor = switch (type) {
            case EMPTY -> RecipeColors.GRID_EMPTY_BG;
            case TARGET -> RecipeColors.GRID_TARGET_BG;
            case INGREDIENT -> RecipeColors.GRID_INGREDIENT_BG;
        };

        gfx.fill(x, y, x + size, y + size, bgColor);
        if (type != GardenMutationLayout.CellType.EMPTY) {
            drawCellBorder(gfx, x, y, size, RecipeColors.GRID_BORDER_SUBTLE);
        }
    }

    private static void drawCellBorder(GuiGraphics gfx, int x, int y, int size, int color) {
        gfx.fill(x, y, x + size, y + 1, color);
        gfx.fill(x, y + size - 1, x + size, y + size, color);
        gfx.fill(x, y, x + 1, y + size, color);
        gfx.fill(x + size - 1, y, x + size, y + size, color);
    }

    // ── Checkerboard texture overlay ──────────────────────────────────────────

    private void renderCheckerboard(GuiGraphics gfx, GridMetrics m) {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int nineIdx = (row + EXPANDED_TO_VISIBLE_OFFSET) * EXPANDED_GRID_DIM
                        + (col + EXPANDED_TO_VISIBLE_OFFSET);
                if (layout.grid()[nineIdx].type() == GardenMutationLayout.CellType.EMPTY) continue;
                if (((row + col) & 1) == 0) continue;
                int x = m.startX() + col * m.step();
                int y = m.startY() + row * m.step();
                gfx.fill(x, y, x + 2, y + 2, RecipeColors.CHECKERBOARD_LIGHTEN);
            }
        }
    }

    // ── Dashed perimeter borders for multi-cell regions ───────────────────────

    private void renderRegionDashedBorders(GuiGraphics gfx) {
        GridMetrics m = GridMetrics.compute();
        for (RegionBinding binding : regionBindings) {
            GardenMutationRegion.Region region = binding.region();
            if (region.isSingleCell()) continue;
            renderDashedBorder(gfx, region, m, region.dashedBorderColor());
        }
    }

    private static void renderDashedBorder(GuiGraphics gfx, GardenMutationRegion.Region region,
                                           GridMetrics m, int color) {
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

        int left   = m.startX() + visMinCol * m.step() + 1;
        int top    = m.startY() + visMinRow * m.step() + 1;
        int right  = m.startX() + (visMaxCol + 1) * m.step() - m.step() + m.cellSize() - 1;
        int bottom = m.startY() + (visMaxRow + 1) * m.step() - m.step() + m.cellSize() - 1;

        drawDashedLineH(gfx, left, right, top,     color, DASH_ON, DASH_OFF, DASH_STROKE);
        drawDashedLineH(gfx, left, right, bottom - DASH_STROKE + 1, color, DASH_ON, DASH_OFF, DASH_STROKE);
        drawDashedLineV(gfx, left,  top, bottom, color, DASH_ON, DASH_OFF, DASH_STROKE);
        drawDashedLineV(gfx, right - DASH_STROKE + 1, top, bottom, color, DASH_ON, DASH_OFF, DASH_STROKE);
    }

    private static void drawDashedLineH(GuiGraphics gfx, int x1, int x2, int y,
                                        int color, int dashOn, int dashOff, int stroke) {
        int x = x1;
        boolean on = true;
        while (x < x2) {
            int segLen = on ? dashOn : dashOff;
            int end = Math.min(x + segLen, x2);
            if (on) gfx.fill(x, y, end, y + stroke, color);
            x = end;
            on = !on;
        }
    }

    private static void drawDashedLineV(GuiGraphics gfx, int x, int y1, int y2,
                                        int color, int dashOn, int dashOff, int stroke) {
        int y = y1;
        boolean on = true;
        while (y < y2) {
            int segLen = on ? dashOn : dashOff;
            int end = Math.min(y + segLen, y2);
            if (on) gfx.fill(x, y, x + stroke, end, color);
            y = end;
            on = !on;
        }
    }

    // ── Dimension labels ("2×3" on multi-cell regions ≥2×2) ──────────────────

    private void renderDimensionLabels(GuiGraphics gfx) {
        GridMetrics m = GridMetrics.compute();
        for (RegionBinding binding : regionBindings) {
            GardenMutationRegion.Region region = binding.region();
            if (region.isSingleCell()) continue;
            int w = region.width();
            int h = region.height();
            if (w < 2 || h < 2) continue;

            int visMaxRow = Integer.MIN_VALUE, visMaxCol = Integer.MIN_VALUE;
            for (int idx : region.cellIndices()) {
                int row9 = idx / EXPANDED_GRID_DIM;
                int col9 = idx % EXPANDED_GRID_DIM;
                int row6 = row9 - EXPANDED_TO_VISIBLE_OFFSET;
                int col6 = col9 - EXPANDED_TO_VISIBLE_OFFSET;
                if (row6 < 0 || row6 >= GRID_SIZE || col6 < 0 || col6 >= GRID_SIZE) continue;
                visMaxRow = Math.max(visMaxRow, row6);
                visMaxCol = Math.max(visMaxCol, col6);
            }
            if (visMaxRow < 0 || visMaxCol < 0) continue;

            int regionRight  = m.startX() + (visMaxCol + 1) * m.step() - m.step() + m.cellSize();
            int regionBottom = m.startY() + (visMaxRow + 1) * m.step() - m.step() + m.cellSize();

            String text = w + "x" + h;
            int textWidth = font().width(text);
            int x = regionRight - textWidth - 2;
            int y = regionBottom - font().lineHeight - 1;
            gfx.drawString(font(), text, x, y, RecipeColors.DIMENSION_LABEL, true);
        }
    }

    // ── Info text ─────────────────────────────────────────────────────────────

    private void renderInfo(GuiGraphics gfx) {
        List<Component> lines = cachedInfoLines;
        if (lines == null) {
            lines = buildInfoLines();
            cachedInfoLines = lines;
        }
        if (lines.isEmpty()) return;

        int y = computeGridBottom() + INFO_Y_GAP;
        int limit = Math.min(lines.size(), INFO_MAX_LINES);
        for (int i = 0; i < limit; i++) {
            gfx.drawString(font(), lines.get(i), TEXT_MARGIN_X, y, RecipeColors.WHITE, true);
            y += LINE_HEIGHT;
        }
    }

    private List<Component> buildInfoLines() {
        List<Component> lines = new ArrayList<>(INFO_MAX_LINES);
        lines.add(buildSurfaceLine());
        lines.add(buildCostLine());
        Component behaviorLine = buildBehaviorLine();
        if (behaviorLine != null) lines.add(behaviorLine);
        return Collections.unmodifiableList(lines);
    }

    /**
     * Line 1: surface type, grid dimensions, stage count and water requirement,
     * separated with middle-dot for a cleaner look.
     */
    private Component buildSurfaceLine() {
        char dot = RecipeColors.MIDDLE_DOT;
        String stagesPart = layout.stages() > 0
                ? " " + dot + " §e" + layout.stages() + "§7 stg"
                : " " + dot + " §7∞";
        String waterPart = layout.needsWater() ? " " + dot + " §b[W]" : "";
        return Component.literal(
                "§7" + layout.surface() + " " + dot + " " + layout.gridSize() + "x" + layout.gridSize()
                        + stagesPart + waterPart);
    }

    /** Line 2: "§6800k Coins  §c+400 Copper". */
    private Component buildCostLine() {
        String cost = SkyblockRecipeUtil.formatNumber(layout.costCoins());
        return Component.literal("§6" + cost + " Coins  §c+" + layout.rewardCopper() + " Copper");
    }

    /**
     * Line 3 — highest-priority behaviour hint:
     * <ol>
     *   <li>Special mechanic warning (truncated, prefixed with ⚠)</li>
     *   <li>Effect summary with ▲/▼ arrows and middle-dot separator</li>
     *   <li>First spreading condition with overflow count</li>
     * </ol>
     */
    @Nullable
    private Component buildBehaviorLine() {
        String mechanic = layout.specialMechanic();
        if (mechanic != null && !mechanic.isBlank()) {
            int newline = mechanic.indexOf('\n');
            String firstSentence = newline > 0 ? mechanic.substring(0, newline) : mechanic;
            if (firstSentence.length() > MECHANIC_TRUNCATE_LENGTH) {
                firstSentence = firstSentence.substring(0, MECHANIC_TRUNCATE_LENGTH - 1) + "…";
            }
            return Component.literal("§c⚠ " + firstSentence);
        }

        List<GardenMutationLayout.Effect> effects = layout.effects();
        if (!effects.isEmpty()) {
            return buildEffectSummaryLine(effects);
        }

        List<GardenMutationLayout.SpreadingCondition> conds = layout.spreadingConditions();
        if (!conds.isEmpty()) {
            String overflow = conds.size() > 1 ? " §8+" + (conds.size() - 1) + " more" : "";
            return Component.literal("§7Spread: §e" + conds.getFirst().text() + overflow);
        }

        return null;
    }

    /**
     * Builds an effect summary like "§a▲Harvest §8· §c▼Water" using
     * up/down arrows for quick positive/negative scanning.
     */
    private static Component buildEffectSummaryLine(List<GardenMutationLayout.Effect> effects) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (GardenMutationLayout.Effect effect : effects) {
            if (shown >= MAX_INLINE_EFFECTS) {
                sb.append(" §8+").append(effects.size() - shown).append("…");
                break;
            }
            if (shown > 0) sb.append(" §8· ");
            boolean negative = NEGATIVE_EFFECTS.contains(effect.name());
            sb.append(negative ? "§c▼" : "§a▲").append(abbreviateEffect(effect.name()));
            shown++;
        }
        return Component.literal(sb.toString());
    }

    /**
     * Shortens effect names for the 140px-wide info line.
     * Improved variants get a trailing '+' to distinguish them.
     */
    private static String abbreviateEffect(String name) {
        return switch (name) {
            case "Harvest Boost"          -> "Harvest";
            case "Improved Harvest Boost" -> "Harvest+";
            case "XP Boost"               -> "XP";
            case "Improved XP Boost"      -> "XP+";
            case "Water Retain"           -> "Water";
            case "Improved Water Retain"  -> "Water+";
            case "Harvest Loss"           -> "Harvest";
            case "XP Loss"                -> "XP";
            case "Water Drain"            -> "Water";
            case "Immunity"               -> "Immunity";
            case "Bonus Drops"            -> "Drops";
            case "Effect Spread"          -> "Spread";
            default -> name.length() <= 8 ? name : name.substring(0, 7) + "…";
        };
    }

    // ── Info tooltip (shown when hovering the text area below the grid) ────────

    private void renderInfoTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        int infoTop = computeGridBottom() + INFO_Y_GAP;
        int lineCount = cachedInfoLines != null
                ? Math.min(cachedInfoLines.size(), INFO_MAX_LINES) : INFO_MAX_LINES;
        int infoBottom = infoTop + lineCount * LINE_HEIGHT;

        if (mouseX < 0 || mouseX > SkyblockGardenMutationRecipeType.DISPLAY_WIDTH) return;
        if (mouseY < infoTop || mouseY >= infoBottom) return;

        List<Component> tooltip = cachedInfoTooltip;
        if (tooltip == null) {
            tooltip = buildInfoTooltip();
            cachedInfoTooltip = tooltip;
        }
        if (!tooltip.isEmpty()) {
            gfx.setComponentTooltipForNextFrame(font(), tooltip, mouseX, mouseY);
        }
    }

    private List<Component> buildInfoTooltip() {
        List<Component> lines = new ArrayList<>();
        appendConditionsSection(lines);
        appendEffectsSection(lines);
        appendRequiredForSection(lines);
        appendMechanicSection(lines);
        return Collections.unmodifiableList(lines);
    }

    private void appendConditionsSection(List<Component> lines) {
        List<GardenMutationLayout.SpreadingCondition> conds = layout.spreadingConditions();
        if (conds.isEmpty()) return;
        lines.add(sectionHeader("Spreading Conditions", "§f"));
        lines.add(SECTION_SEPARATOR);
        for (GardenMutationLayout.SpreadingCondition cond : conds) {
            lines.add(Component.literal("  §e" + cond.text()));
        }
    }

    private void appendEffectsSection(List<Component> lines) {
        List<GardenMutationLayout.Effect> effects = layout.effects();
        if (effects.isEmpty()) return;
        if (!lines.isEmpty()) lines.add(Component.empty());
        lines.add(sectionHeader("Effects", "§f"));
        lines.add(SECTION_SEPARATOR);
        for (GardenMutationLayout.Effect effect : effects) {
            boolean negative = NEGATIVE_EFFECTS.contains(effect.name());
            String arrow = negative ? "§c▼ " : "§a▲ ";
            lines.add(Component.literal("  " + arrow + "§f" + effect.name()));
            lines.add(Component.literal("  §7  " + effect.description()));
        }
    }

    private void appendRequiredForSection(List<Component> lines) {
        List<String> reqFor = layout.requiredFor();
        if (reqFor.isEmpty()) return;
        if (!lines.isEmpty()) lines.add(Component.empty());
        lines.add(sectionHeader("Required For", "§f"));
        lines.add(SECTION_SEPARATOR);
        for (String mutationId : reqFor) {
            NeuItem reqItem = NeuItemRegistry.get(mutationId);
            String display = (reqItem != null && reqItem.displayName != null)
                    ? reqItem.displayName : "§7" + mutationId;
            lines.add(Component.literal("  §8• " + display));
        }
    }

    private void appendMechanicSection(List<Component> lines) {
        String mechanic = layout.specialMechanic();
        if (mechanic == null || mechanic.isBlank()) return;
        if (!lines.isEmpty()) lines.add(Component.empty());
        lines.add(sectionHeader("⚠ Special Mechanic", "§c"));
        lines.add(SECTION_SEPARATOR);
        for (String part : mechanic.split("\n")) {
            lines.add(Component.literal("  §7" + part));
        }
    }

    /** Builds a bold section header for tooltip visual separation. */
    private static Component sectionHeader(String title, String colorCode) {
        return Component.literal(colorCode + "§l" + title + "§r");
    }

    // ── Mini legend (rendered inside grid, bottom-right corner) ───────────────

    /**
     * Draws two tiny coloured squares in the bottom-right of the grid area
     * to serve as a visual key. This costs zero extra vertical space.
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
    protected AbstractButton placeButtons(RecipeViewScreen screen, RecipePosition pos) {
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
