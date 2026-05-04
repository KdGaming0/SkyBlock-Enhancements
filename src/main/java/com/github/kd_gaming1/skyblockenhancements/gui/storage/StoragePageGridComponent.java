package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageSlotData;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Renders one mini grid representing a single storage page.
 *
 * <p>Includes a title label, a colored border (gold for active, grey for inactive),
 * and a grid of {@link StorageSlotComponent}s.
 */
public class StoragePageGridComponent extends AbstractComponent {

    private static final int TITLE_HEIGHT = 10;
    private static final int BORDER_THICKNESS = 1;

    private final StorageSnapshot snapshot;
    private final boolean active;
    private final int slotSize;
    private final int slotGap;
    private final String searchQuery;
    private final List<StorageSlotComponent> slotComponents;

    public StoragePageGridComponent(
            int x, int y,
            StorageSnapshot snapshot,
            boolean active,
            int slotSize, int slotGap,
            String searchQuery,
            List<StorageSlotComponent> slotCollector) {

        super(x, y, 0, 0);
        this.snapshot = snapshot;
        this.active = active;
        this.slotSize = slotSize;
        this.slotGap = slotGap;
        this.searchQuery = searchQuery != null ? searchQuery.toLowerCase() : "";
        this.slotComponents = new ArrayList<>();

        int cols = 9;
        int rows = Math.max(1, (snapshot.slots.size() + cols - 1) / cols);

        int gridW = cols * slotSize + (cols - 1) * slotGap;
        int gridH = rows * slotSize + (rows - 1) * slotGap;
        int width = gridW + BORDER_THICKNESS * 2;
        int height = TITLE_HEIGHT + gridH + BORDER_THICKNESS * 2 + 2;

        setWidth(width);
        setHeight(height);

        build(rows, cols, gridW, gridH, slotCollector);
    }

    private void build(int rows, int cols, int gridW, int gridH, List<StorageSlotComponent> slotCollector) {
        this.clear();

        int borderColor = active ? StorageColors.PAGE_BORDER_ACTIVE : StorageColors.PAGE_BORDER_INACTIVE;

        // Border background
        addComponent(new ColorComponent(0, TITLE_HEIGHT, getWidth(), getHeight() - TITLE_HEIGHT, borderColor));
        // Inner background
        addComponent(new ColorComponent(
                BORDER_THICKNESS, TITLE_HEIGHT + BORDER_THICKNESS,
                getWidth() - BORDER_THICKNESS * 2,
                getHeight() - TITLE_HEIGHT - BORDER_THICKNESS * 2,
                StorageColors.PANEL_BG));

        // Title
        String title = snapshot.titleText != null && !snapshot.titleText.isEmpty()
                ? snapshot.titleText
                : snapshot.pageId;
        addComponent(new TextComponent(BORDER_THICKNESS, 0,
                Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                StorageColors.TEXT_TITLE));

        // Slots
        int gridStartX = BORDER_THICKNESS;
        int gridStartY = TITLE_HEIGHT + BORDER_THICKNESS + 1;

        for (int i = 0; i < snapshot.slots.size(); i++) {
            StorageSlotData slotData = snapshot.slots.get(i);
            int col = i % cols;
            int row = i / cols;
            int sx = gridStartX + col * (slotSize + slotGap);
            int sy = gridStartY + row * (slotSize + slotGap);

            boolean highlighted = !searchQuery.isEmpty() && matchesSearch(slotData);
            if (highlighted) {
                addComponent(new ColorComponent(sx, sy, slotSize, slotSize, StorageColors.SEARCH_HIGHLIGHT));
            }

            StorageSlotComponent slotComp = new StorageSlotComponent(
                    sx, sy, slotSize, slotData.getCachedStack(), slotData.slotIndex);
            addComponent(slotComp);
            slotComponents.add(slotComp);
            slotCollector.add(slotComp);
        }
    }

    private boolean matchesSearch(StorageSlotData slotData) {
        if (slotData.isEmpty()) return false;
        ItemStack stack = slotData.getCachedStack();
        if (stack.isEmpty()) return false;

        String name = stack.getHoverName().getString().toLowerCase();
        return name.contains(searchQuery);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt, int pw, int ph) {
        // Child components handle all rendering
    }
}
