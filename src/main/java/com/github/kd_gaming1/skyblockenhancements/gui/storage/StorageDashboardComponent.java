package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayManager;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Root component for the storage dashboard.
 *
 * <p>Draws an opaque background panel and a scrollable grid of page rows.
 * Each row holds up to 3 {@link StoragePageGridComponent}s so all storage
 * pages are visible at once. Tracks the active page's slot-grid bounds so the
 * mixin can proxy clicks back to vanilla slots.
 */
public class StorageDashboardComponent extends AbstractComponent {

    public static final int TOP_BAR_HEIGHT = 28;
    public static final int PAGES_PER_ROW = 3;
    public static final int PAGE_GRID_GAP = 8;

    private final StorageOverlayManager manager;
    private final String activePageId;
    private final int slotSize;
    private final int slotGap;
    private final int pagesPerRow;
    private String searchQuery;
    private final List<StorageSlotComponent> slotCollector;
    private final ScrollContainerWidget scrollContainer;

    private List<StorageSnapshot> cachedSnapshots = List.of();
    private long cachedVersion = -1;

    public StorageDashboardComponent(
            int x, int y, int width, int height,
            StorageOverlayManager manager,
            String activePageId,
            int slotSize, int slotGap,
            String searchQuery,
            List<StorageSlotComponent> slotCollector,
            ScrollContainerWidget scrollContainer,
            int pagesPerRow) {

        super(x, y, width, height);
        this.manager = manager;
        this.activePageId = activePageId;
        this.slotSize = slotSize;
        this.slotGap = slotGap;
        this.searchQuery = searchQuery != null ? searchQuery : "";
        this.slotCollector = slotCollector;
        this.scrollContainer = scrollContainer;
        this.pagesPerRow = pagesPerRow;

        this.cachedVersion = manager.getSnapshotVersion();
        this.cachedSnapshots = manager.getSnapshots();

        addWidget(scrollContainer);
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
        this.clearComponents();
        slotCollector.clear();
        scrollContainer.clearComponents();

        // Background panel
        addComponent(new ColorComponent(0, 0, getWidth(), getHeight(), StorageColors.PANEL_BG));

        List<StorageSnapshot> snapshots = new ArrayList<>(cachedSnapshots);
        snapshots.sort(java.util.Comparator
                .comparing((StorageSnapshot s) -> s.type)
                .thenComparingInt(s -> s.pageNumber));
        if (snapshots.isEmpty()) return;

        // Build rows of page grids (up to pagesPerRow per row)
        List<StoragePageGridComponent> currentRow = new ArrayList<>();
        for (StorageSnapshot snap : snapshots) {
            boolean active = snap.pageId.equals(activePageId);
            StoragePageGridComponent grid = new StoragePageGridComponent(
                    0, 0, snap, active, slotSize, slotGap, searchQuery, slotCollector);
            currentRow.add(grid);

            if (currentRow.size() >= pagesPerRow) {
                addRow(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            addRow(currentRow);
        }
    }

    private void addRow(List<StoragePageGridComponent> grids) {
        StoragePageRowComponent row = new StoragePageRowComponent(0, 0, grids, PAGE_GRID_GAP, getWidth());
        scrollContainer.addComponent(row);
    }

    /** Returns the scroll container so the mixin can forward mouse events. */
    public ScrollContainerWidget getScrollContainer() {
        return scrollContainer;
    }

    /** Returns the current vertical scroll offset in pixels. */
    public int getScrollOffset() {
        return (int) scrollContainer.scrollAmount();
    }

    /** Returns the grid (if any) whose bounds contain the given screen coordinates. */
    public StoragePageGridComponent findGridAt(int mouseX, int mouseY) {
        int scrollTop = scrollContainer.getY();
        int scrollBottom = scrollTop + scrollContainer.getHeight();
        for (var rowComp : scrollContainer.getComponents()) {
            if (rowComp instanceof StoragePageRowComponent row) {
                for (var gridComp : row.getComponents()) {
                    if (gridComp instanceof StoragePageGridComponent grid) {
                        int gy = grid.getTotalY();
                        // Skip grids that are completely outside the visible scroll area
                        if (gy + grid.getHeight() < scrollTop || gy > scrollBottom) {
                            continue;
                        }
                        int gx = grid.getTotalX();
                        if (mouseX >= gx && mouseX < gx + grid.getWidth()
                                && mouseY >= gy && mouseY < gy + grid.getHeight()) {
                            return grid;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Returns true if the given screen coordinates fall inside the active page slot grid. */
    public boolean isInsideActiveGrid(int mouseX, int mouseY, int scrollOffset) {
        StoragePageGridComponent active = findActiveGrid();
        if (active == null) return false;

        int innerX = active.getTotalX() + active.getGridInnerOffsetX();
        int innerY = active.getTotalY() + active.getGridInnerOffsetY();
        int innerW = active.getGridInnerWidth();
        int innerH = active.getGridInnerHeight();

        return mouseX >= innerX && mouseX < innerX + innerW
                && mouseY >= innerY && mouseY < innerY + innerH;
    }

    /**
     * Translates a mouse coordinate inside the active grid to a vanilla slot index.
     *
     * @return slot index within the container, or -1 if out of bounds
     */
    public int translateActiveGridClick(int mouseX, int mouseY, int scrollOffset) {
        StoragePageGridComponent active = findActiveGrid();
        if (active == null) return -1;

        int innerX = active.getTotalX() + active.getGridInnerOffsetX();
        int innerY = active.getTotalY() + active.getGridInnerOffsetY();

        int localX = mouseX - innerX;
        int localY = mouseY - innerY;

        int step = active.getSlotSize() + active.getSlotGap();
        int col = localX / step;
        int row = localY / step;

        // Reject clicks in the gap between slots
        int colStart = col * step;
        int rowStart = row * step;
        if (localX - colStart >= active.getSlotSize() || localY - rowStart >= active.getSlotSize()) {
            return -1;
        }

        int cols = 9;
        int rows = Math.max(1, (active.getSlotComponents().size() + cols - 1) / cols);
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return -1;
        }
        int localIndex = row * cols + col;
        if (localIndex < 0 || localIndex >= active.getSlotComponents().size()) {
            return -1;
        }
        return active.getSlotComponents().get(localIndex).getSlotIndex();
    }

    public void refreshActiveGrid(java.util.function.IntFunction<ItemStack> provider) {
        StoragePageGridComponent active = findActiveGrid();
        if (active != null) {
            active.refreshLiveStacks(provider);
        }
    }

    public StoragePageGridComponent findActiveGrid() {
        for (var rowComp : scrollContainer.getComponents()) {
            if (rowComp instanceof StoragePageRowComponent row) {
                for (var gridComp : row.getComponents()) {
                    if (gridComp instanceof StoragePageGridComponent grid && grid.isActive()) {
                        return grid;
                    }
                }
            }
        }
        return null;
    }

    public StoragePageRowComponent findActiveRow() {
        StoragePageGridComponent active = findActiveGrid();
        if (active == null) return null;
        for (var rowComp : scrollContainer.getComponents()) {
            if (rowComp instanceof StoragePageRowComponent row) {
                for (var gridComp : row.getComponents()) {
                    if (gridComp == active) {
                        return row;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt, int pw, int ph) {
        // Background is rendered by child ColorComponent; scroll container is rendered as widget
    }

    @Override
    public void updateParentPosition(int parentX, int parentY, int parentWidth, int parentHeight) {
        super.updateParentPosition(parentX, parentY, parentWidth, parentHeight);
        if (scrollContainer != null) {
            int absX = getTotalX();
            int absY = getTotalY() + TOP_BAR_HEIGHT;
            scrollContainer.setX(absX);
            scrollContainer.setY(absY);
            scrollContainer.uilib$updateParentPosition(absX, absY);
        }
    }
}
