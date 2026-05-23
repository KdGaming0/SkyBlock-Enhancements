package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects crop placements in a garden mutation layout using scanline-order
 * rectangle detection guided by a global {@code cropSizes} index.
 *
 * <p>Algorithm: scan cells left-to-right, top-to-bottom. When an unclaimed
 * cell contains a crop listed in {@code cropSizes} with width &gt; 1 or
 * height &gt; 1, the code tries to match a rectangle of that size starting
 * from the current cell. If the rectangle matches, one region is created
 * for the entire block. If not, the cell falls back to single-cell
 * rendering. Crops not in {@code cropSizes} always render as individual
 * cells.
 *
 * <p>This replaces the old flood-fill approach which incorrectly merged
 * adjacent individual placements into irregular blobs.
 */
public final class GardenMutationRegion {

    // 9×9 internal grid dimension
    private static final int GRID_DIM = 9;
    // Offset of the visible 6×6 area from the grid edges
    private static final int VISIBLE_OFFSET = 1;
    // Visible slot area size
    private static final int VISIBLE_SIZE = 6;

    private GardenMutationRegion() {
    }

    /**
     * A rectangular placement of a crop. Every region is a proper rectangle;
     * multi-cell regions come from {@code cropSizes}, single-cell regions
     * from ordinary 1×1 crops.
     */
    public record Region(
            int minRow, int minCol,
            int maxRow, int maxCol,
            GardenMutationLayout.CellType type,
            @Nullable String itemId,
            List<Integer> cellIndices,
            int visibleCenterRow,
            int visibleCenterCol
    ) {
        /** Width of this region in cells (always ≥ 1). */
        public int width() {
            return maxCol - minCol + 1;
        }

        /** Height of this region in cells (always ≥ 1). */
        public int height() {
            return maxRow - minRow + 1;
        }

        public boolean isSingleCell() {
            return cellIndices.size() == 1;
        }

        public boolean isVisible() {
            return visibleCenterRow >= 0 && visibleCenterCol >= 0;
        }

        public int cellCount() {
            return cellIndices.size();
        }

        /** True for any region that spans more than one cell. */
        public boolean isMultiblock() {
            return cellIndices.size() > 1;
        }

        /** Colour for the dashed perimeter border (60 % opacity). */
        public int dashedBorderColor() {
            return switch (type) {
                case TARGET -> 0x996bb3ff;
                case INGREDIENT -> 0x99c4a44a;
                default -> 0x99FFFFFF;
            };
        }
    }

    /**
     * Scans the layout and returns one {@link Region} per crop placement.
     * Multi-block crops (listed in {@code cropSizes}) produce one region
     * per rectangular placement; all other crops produce one region per cell.
     */
    public static List<Region> detectRegions(GardenMutationLayout layout) {
        GardenMutationLayout.Cell[] grid = layout.grid();
        if (grid == null || grid.length != GRID_DIM * GRID_DIM) {
            return Collections.emptyList();
        }

        boolean[] claimed = new boolean[GRID_DIM * GRID_DIM];
        List<Region> regions = new ArrayList<>();

        for (int row = 0; row < GRID_DIM; row++) {
            for (int col = 0; col < GRID_DIM; col++) {
                int idx = row * GRID_DIM + col;
                if (claimed[idx]) {
                    continue;
                }

                GardenMutationLayout.Cell cell = grid[idx];
                if (cell.type() == GardenMutationLayout.CellType.EMPTY) {
                    claimed[idx] = true;
                    continue;
                }

                String cropId = cropIdForCell(cell, layout);
                GardenMutationLayout.CropSize size = layout.cropSize(cropId);

                if (size != null && (size.width() > 1 || size.height() > 1)) {
                    Region region = tryMatchRectangle(
                            grid, claimed, row, col, cell, size);
                    if (region != null) {
                        regions.add(region);
                        continue;
                    }
                    // Partial match — fall through to single-cell rendering
                }

                // Single-cell region
                regions.add(createSingleCellRegion(grid, claimed, row, col, cell));
            }
        }

        return Collections.unmodifiableList(regions);
    }

    /**
     * Returns the crop ID for a cell — the itemId for ingredients, the
     * mutation ID for the target.
     */
    private static String cropIdForCell(GardenMutationLayout.Cell cell,
                                        GardenMutationLayout layout) {
        return switch (cell.type()) {
            case TARGET -> layout.mutationId();
            case INGREDIENT -> cell.itemId() != null ? cell.itemId() : "";
            default -> "";
        };
    }

    /**
     * Attempts to match a {@code width} × {@code height} rectangle of
     * identical cells starting from ({@code startRow}, {@code startCol}).
     * Returns a {@link Region} on success, or {@code null} if the rectangle
     * does not match (partial placement, wrong shape, etc.).
     */
    @Nullable
    private static Region tryMatchRectangle(
            GardenMutationLayout.Cell[] grid,
            boolean[] claimed,
            int startRow, int startCol,
            GardenMutationLayout.Cell seed,
            GardenMutationLayout.CropSize size) {

        int expectedW = size.width();
        int expectedH = size.height();

        // Clamp to grid bounds
        int maxW = Math.min(expectedW, GRID_DIM - startCol);
        int maxH = Math.min(expectedH, GRID_DIM - startRow);

        // Find actual rectangle width (consecutive matching cells in first row)
        int actualW = 0;
        for (int dc = 0; dc < maxW; dc++) {
            int idx = startRow * GRID_DIM + (startCol + dc);
            if (!claimed[idx] && matches(grid[idx], seed)) {
                actualW++;
            } else {
                break;
            }
        }

        if (actualW == 0) {
            return null;
        }

        // Find actual rectangle height (all rows must match the full width)
        int actualH = 0;
        for (int dr = 0; dr < maxH; dr++) {
            int row = startRow + dr;
            boolean rowMatches = true;
            for (int dc = 0; dc < actualW; dc++) {
                int idx = row * GRID_DIM + (startCol + dc);
                if (claimed[idx] || !matches(grid[idx], seed)) {
                    rowMatches = false;
                    break;
                }
            }
            if (rowMatches) {
                actualH++;
            } else {
                break;
            }
        }

        // Only accept if the found rectangle matches the expected size exactly
        if (actualW != expectedW || actualH != expectedH) {
            return null;
        }

        // Build the region
        List<Integer> indices = new ArrayList<>(actualW * actualH);
        int visibleRowSum = 0;
        int visibleColSum = 0;
        int visibleCount = 0;

        for (int dr = 0; dr < actualH; dr++) {
            for (int dc = 0; dc < actualW; dc++) {
                int row = startRow + dr;
                int col = startCol + dc;
                int idx = row * GRID_DIM + col;
                claimed[idx] = true;
                indices.add(idx);

                if (isVisible(row, col)) {
                    visibleRowSum += (row - VISIBLE_OFFSET);
                    visibleColSum += (col - VISIBLE_OFFSET);
                    visibleCount++;
                }
            }
        }

        int centerRow = visibleCount > 0
                ? roundAndClamp(visibleRowSum, visibleCount) : -1;
        int centerCol = visibleCount > 0
                ? roundAndClamp(visibleColSum, visibleCount) : -1;

        return new Region(
                startRow, startCol,
                startRow + actualH - 1, startCol + actualW - 1,
                seed.type(), seed.itemId(),
                Collections.unmodifiableList(indices),
                centerRow, centerCol
        );
    }

    private static Region createSingleCellRegion(
            GardenMutationLayout.Cell[] grid,
            boolean[] claimed,
            int row, int col,
            GardenMutationLayout.Cell cell) {
        int idx = row * GRID_DIM + col;
        claimed[idx] = true;

        int visRow = -1;
        int visCol = -1;
        if (isVisible(row, col)) {
            visRow = row - VISIBLE_OFFSET;
            visCol = col - VISIBLE_OFFSET;
        }

        return new Region(
                row, col, row, col,
                cell.type(), cell.itemId(),
                Collections.singletonList(idx),
                visRow, visCol
        );
    }

    private static boolean matches(GardenMutationLayout.Cell a,
                                   GardenMutationLayout.Cell b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.type() != b.type()) {
            return false;
        }
        if (a.type() == GardenMutationLayout.CellType.INGREDIENT) {
            String idA = a.itemId() != null ? a.itemId() : "";
            String idB = b.itemId() != null ? b.itemId() : "";
            return idA.equals(idB);
        }
        return true;
    }

    private static boolean isVisible(int row, int col) {
        return row >= VISIBLE_OFFSET
                && row < VISIBLE_OFFSET + VISIBLE_SIZE
                && col >= VISIBLE_OFFSET
                && col < VISIBLE_OFFSET + VISIBLE_SIZE;
    }

    private static int roundAndClamp(int sum, int count) {
        int rounded = (int) Math.round((double) sum / count);
        return Math.clamp(rounded, 0, VISIBLE_SIZE - 1);
    }
}
