package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.daqem.uilib.gui.component.AbstractComponent;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Horizontal row that holds up to 3 {@link StoragePageGridComponent}s.
 *
 * <p>Used inside a {@link com.daqem.uilib.gui.widget.ScrollContainerWidget}
 * so that multiple rows of page grids can be scrolled vertically.
 */
public class StoragePageRowComponent extends AbstractComponent {

    public StoragePageRowComponent(int x, int y, List<StoragePageGridComponent> grids, int gap, int availableWidth) {
        super(x, y, 0, 0);

        int width = 0;
        int maxHeight = 0;
        for (int i = 0; i < grids.size(); i++) {
            StoragePageGridComponent grid = grids.get(i);
            grid.setX(width);
            grid.setY(0);
            addComponent(grid);
            width += grid.getWidth();
            if (i < grids.size() - 1) {
                width += gap;
            }
            maxHeight = Math.max(maxHeight, grid.getHeight());
        }
        setWidth(width);
        setHeight(maxHeight);

        // Center the row's grids within the available width by offsetting each
        // grid's X position. The availableWidth is the parent/container width
        // (the dashboard width) so rows narrower than the scroll width are centered.
        int leftPad = (availableWidth - width) / 2;
        if (leftPad > 0) {
            for (StoragePageGridComponent grid : grids) {
                grid.setX(grid.getX() + leftPad);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt, int pw, int ph) {
        // Child components handle all rendering
    }
}
