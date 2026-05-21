package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Detects contiguous regions of the same crop in a 9x9 garden mutation layout
 * using flood-fill. This enables the renderer to show one icon per multi-cell
 * crop region instead of one icon per cell.
 */
public final class GardenMutationRegion {

    // 9x9 internal grid dimension
    private static final int GRID_DIM = 9;
    // Offset of the visible 6x6 area from the grid edges
    private static final int VISIBLE_OFFSET = 1;
    // Visible slot area size
    private static final int VISIBLE_SIZE = 6;

    // Directions: up, down, left, right (row delta, col delta)
    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private GardenMutationRegion() {
    }

    /**
     * A contiguous region of connected cells of the same type.
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

        public boolean isSingleCell() {
            return cellIndices.size() == 1;
        }

        public boolean isVisible() {
            return visibleCenterRow >= 0 && visibleCenterCol >= 0;
        }

        public int cellCount() {
            return cellIndices.size();
        }

        /**
         * Returns true if this region represents a multiblock crop.
         * A region is multiblock if its type is TARGET and the region has
         * more than 1 cell, or if its type is INGREDIENT and its itemId
         * is in the provided multiblock set.
         */
        public boolean isMultiblockRegion(Set<String> multiblockCrops) {
            if (isSingleCell()) return false;
            return switch (type) {
                case TARGET -> true;  // multi-cell target is always multiblock
                case INGREDIENT -> itemId != null && multiblockCrops.contains(itemId);
                default -> false;
            };
        }
    }

    /**
     * Filters regions to only those that should use region-based rendering.
     * Multiblock crops get one slot per region; non-multiblock get one slot per cell.
     */
    public static List<Region> filterMultiblockRegions(List<Region> regions, Set<String> multiblockCrops) {
        List<Region> result = new ArrayList<>();
        for (Region region : regions) {
            if (region.isMultiblockRegion(multiblockCrops)) {
                result.add(region);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Detects all contiguous regions in the given layout.
     */
    public static List<Region> detectRegions(GardenMutationLayout layout) {
        GardenMutationLayout.Cell[] grid = layout.grid();
        if (grid == null || grid.length != GRID_DIM * GRID_DIM) {
            return Collections.emptyList();
        }

        boolean[] visited = new boolean[GRID_DIM * GRID_DIM];
        List<Region> regions = new ArrayList<>();

        for (int idx = 0; idx < grid.length; idx++) {
            if (visited[idx] || grid[idx] == null) {
                continue;
            }

            GardenMutationLayout.Cell cell = grid[idx];
            if (cell.type() == GardenMutationLayout.CellType.EMPTY) {
                visited[idx] = true;
                continue;
            }

            regions.add(floodFillRegion(grid, visited, idx, cell));
        }

        return regions;
    }

    private static Region floodFillRegion(
            GardenMutationLayout.Cell[] grid,
            boolean[] visited,
            int startIdx,
            GardenMutationLayout.Cell seed
    ) {
        List<Integer> indices = new ArrayList<>();

        int minRow = GRID_DIM;
        int minCol = GRID_DIM;
        int maxRow = -1;
        int maxCol = -1;

        int visibleRowSum = 0;
        int visibleColSum = 0;
        int visibleCount = 0;

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(startIdx);

        while (!stack.isEmpty()) {
            int idx = stack.pop();
            if (visited[idx]) {
                continue;
            }
            if (!matches(grid[idx], seed)) {
                continue;
            }

            visited[idx] = true;
            indices.add(idx);

            int row = idx / GRID_DIM;
            int col = idx % GRID_DIM;

            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);

            if (isVisible(row, col)) {
                visibleRowSum += (row - VISIBLE_OFFSET);
                visibleColSum += (col - VISIBLE_OFFSET);
                visibleCount++;
            }

            pushNeighbors(stack, row, col);
        }

        int centerRow = visibleCount > 0 ? roundAndClamp(visibleRowSum, visibleCount) : -1;
        int centerCol = visibleCount > 0 ? roundAndClamp(visibleColSum, visibleCount) : -1;

        return new Region(
                minRow, minCol, maxRow, maxCol,
                seed.type(), seed.itemId(),
                Collections.unmodifiableList(indices),
                centerRow, centerCol
        );
    }

    private static boolean matches(GardenMutationLayout.Cell a, GardenMutationLayout.Cell b) {
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

    private static void pushNeighbors(Deque<Integer> stack, int row, int col) {
        for (int[] dir : DIRS) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (nr >= 0 && nr < GRID_DIM && nc >= 0 && nc < GRID_DIM) {
                stack.push(nr * GRID_DIM + nc);
            }
        }
    }

    private static int roundAndClamp(int sum, int count) {
        int rounded = (int) Math.round((double) sum / count);
        return Math.clamp(rounded, 0, VISIBLE_SIZE - 1);
    }
}
