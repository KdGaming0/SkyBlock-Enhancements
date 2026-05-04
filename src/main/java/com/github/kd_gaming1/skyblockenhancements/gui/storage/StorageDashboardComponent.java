package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayManager;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Root component for the storage dashboard.
 *
 * <p>Lays out a background panel, a grid of mini page grids, and tracks the
 * active page's bounds so the mixin can proxy clicks back to vanilla slots.
 */
public class StorageDashboardComponent extends AbstractComponent {

    private static final int PAGE_GRID_GAP = 8;
    public static final int TOP_BAR_HEIGHT = 24; // space for search bar + button

    private final StorageOverlayManager manager;
    private final String activePageId;
    private final int slotSize;
    private final int slotGap;
    private String searchQuery;
    private final List<StorageSlotComponent> slotCollector;

    private List<StorageSnapshot> cachedSnapshots = List.of();
    private long cachedVersion = -1;
    private int activeGridX = -1;
    private int activeGridY = -1;
    private int activeGridInnerX = -1;
    private int activeGridInnerY = -1;
    private int activeGridInnerW = -1;
    private int activeGridInnerH = -1;
    private int activeGridCols = 9;
    private int activeGridRows = 6;

    public StorageDashboardComponent(
            int x, int y, int width, int height,
            StorageOverlayManager manager,
            String activePageId,
            int slotSize, int slotGap,
            String searchQuery,
            List<StorageSlotComponent> slotCollector) {
        super(x, y, width, height);
        this.manager = manager;
        this.activePageId = activePageId;
        this.slotSize = slotSize;
        this.slotGap = slotGap;
        this.searchQuery = searchQuery;
        this.slotCollector = slotCollector;
        build();
    }

    /** Rebuilds child components if the underlying snapshots have changed. */
    public void updateIfDirty() {
        long nowVersion = manager.getSnapshotVersion();
        if (nowVersion != cachedVersion) {
            cachedVersion = nowVersion;
            cachedSnapshots = manager.getSnapshots();
            build();
        }
    }

    /** Forces a rebuild with a new search query. */
    public void setSearchQuery(String query) {
        if (query == null) query = "";
        if (!query.equals(this.searchQuery)) {
            this.searchQuery = query;
            build();
        }
    }

    private void build() {
        this.clear();
        slotCollector.clear();

        // Background panel
        addComponent(new ColorComponent(0, 0, getWidth(), getHeight(), StorageColors.PANEL_BG));

        List<StorageSnapshot> snapshots = new ArrayList<>(cachedSnapshots);
        // Ensure active page is present; if not, show what we have.
        if (snapshots.isEmpty()) return;

        int contentY = TOP_BAR_HEIGHT + PAGE_GRID_GAP;
        int availableWidth = getWidth() - PAGE_GRID_GAP * 2;
        int availableHeight = getHeight() - contentY - PAGE_GRID_GAP;
        int startX = PAGE_GRID_GAP;

        int rowY = contentY;
        int rowX = startX;
        int maxRowH = 0;

        for (StorageSnapshot snap : snapshots) {
            boolean active = snap.pageId.equals(activePageId);
            StoragePageGridComponent grid = new StoragePageGridComponent(
                    rowX, rowY, snap, active, slotSize, slotGap, searchQuery, slotCollector);

            // Check if this page fits in the remaining width
            if (rowX + grid.getWidth() > startX + availableWidth && rowX != startX) {
                rowY += maxRowH + PAGE_GRID_GAP;
                rowX = startX;
                maxRowH = 0;
                grid = new StoragePageGridComponent(
                        rowX, rowY, snap, active, slotSize, slotGap, searchQuery, slotCollector);
            }

            addComponent(grid);

            if (active) {
                activeGridX = getTotalX() + rowX;
                activeGridY = getTotalY() + rowY;
                int border = 1;
                int titleH = 10;
                int padding = 1;
                activeGridCols = 9;
                activeGridRows = Math.max(1, (snap.slots.size() + 8) / 9);
                activeGridInnerX = activeGridX + border;
                activeGridInnerY = activeGridY + titleH + border + padding;
                activeGridInnerW = 9 * slotSize + 8 * slotGap;
                activeGridInnerH = activeGridRows * slotSize + (activeGridRows - 1) * slotGap;
            }

            rowX += grid.getWidth() + PAGE_GRID_GAP;
            maxRowH = Math.max(maxRowH, grid.getHeight());
        }
    }

    /** Returns true if the given screen coordinates fall inside the active page slot grid. */
    public boolean isInsideActiveGrid(int mouseX, int mouseY) {
        if (activeGridInnerX < 0) return false;
        return mouseX >= activeGridInnerX && mouseX < activeGridInnerX + activeGridInnerW
                && mouseY >= activeGridInnerY && mouseY < activeGridInnerY + activeGridInnerH;
    }

    /**
     * Translates a mouse coordinate inside the active grid to a vanilla slot index.
     *
     * @return slot index within the container, or -1 if out of bounds
     */
    public int translateActiveGridClick(int mouseX, int mouseY) {
        int localX = mouseX - activeGridInnerX;
        int localY = mouseY - activeGridInnerY;

        int step = slotSize + slotGap;
        int col = localX / step;
        int row = localY / step;

        // Reject clicks in the gap between slots
        int colStart = col * step;
        int rowStart = row * step;
        if (localX - colStart >= slotSize || localY - rowStart >= slotSize) {
            return -1;
        }

        if (col < 0 || col >= activeGridCols || row < 0 || row >= activeGridRows) {
            return -1;
        }
        return row * activeGridCols + col;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt, int pw, int ph) {
        // Background and grids are rendered by child components
    }
}
