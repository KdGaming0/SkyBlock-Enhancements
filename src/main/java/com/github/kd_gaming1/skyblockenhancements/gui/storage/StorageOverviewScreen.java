package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageData;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayLifecycle;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageSlot;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.VirtualInventory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Fullscreen overview screen shown when the player closes an active storage page.
 *
 * <p>Displays all cached pages in a scrollable fullscreen grid. Clicking any page
 * sends the navigation command and returns to the container overlay.
 */
public class StorageOverviewScreen extends Screen {

    private static final int SLOT_RENDER_SIZE = 18;
    private static final int PAGE_SLOTS_WIDTH = SLOT_RENDER_SIZE * 9;
    private static final int PAGE_WIDTH = PAGE_SLOTS_WIDTH + 4;
    private static final int PADDING = 10;
    private static final int SCROLL_BAR_WIDTH = 8;
    private static final int TOP_BAR_HEIGHT = 28;

    private final Minecraft mc;
    private float scroll = 0f;
    private int lastRenderedInnerHeight = 0;
    private boolean knobGrabbed = false;

    private EditBox searchField;
    private String searchQuery = "";
    private String cachedSearch = null;
    private Set<StoragePageSlot> filteredPagesCache = Set.of();

    // Measurements
    private int pageWidthCount;
    private int gridX, gridY;
    private int gridWidth, gridHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;

    public StorageOverviewScreen() {
        super(Component.literal("Storage Overview"));
        this.mc = Minecraft.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        recalculateMeasurements();
        scroll = clampScroll(scroll);

        if (searchField == null) {
            this.searchField = new EditBox(mc.font, 0, 0, 200, 18, Component.literal("Search items..."));
            this.searchField.setMaxLength(64);
            this.searchField.setHint(Component.literal("Search items..."));
            this.searchField.setResponder(this::onSearchChanged);
            this.searchField.setBordered(true);
        }
        updateSearchFieldPosition();
    }

    private void recalculateMeasurements() {
        pageWidthCount = Math.max(1,
                Math.min(SkyblockEnhancementsConfig.storageOverlayColumns,
                        (width - PADDING * 4) / (PAGE_WIDTH + PADDING)));

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        gridWidth = innerScrollPanelWidth + 3 * PADDING + SCROLL_BAR_WIDTH;
        gridHeight = height - PADDING * 4 - TOP_BAR_HEIGHT;
        innerScrollPanelHeight = gridHeight - PADDING * 2;

        gridX = width / 2 - gridWidth / 2;
        gridY = PADDING * 2 + TOP_BAR_HEIGHT;

        updateSearchFieldPosition();
    }

    private void updateSearchFieldPosition() {
        if (searchField != null) {
            searchField.setX(gridX + PADDING);
            searchField.setY(gridY - TOP_BAR_HEIGHT + 4);
            searchField.setWidth(Math.max(150, gridWidth - SCROLL_BAR_WIDTH - PADDING * 4));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Dark overlay covering the entire screen
        graphics.fill(0, 0, width, height, 0x90000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Panel background
        graphics.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xE0000000);

        // Pages
        drawPages(graphics, mouseX, mouseY);

        // Scrollbar
        drawScrollbar(graphics);

        // Search
        if (searchField != null) {
            searchField.render(graphics, mouseX, mouseY, delta);
        }
    }

    private void drawPages(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect scrollPanel = getScrollPanelInner();
        graphics.enableScissor(scrollPanel.x, scrollPanel.y,
                scrollPanel.x + scrollPanel.width, scrollPanel.y + scrollPanel.height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, -scroll);

        Set<StoragePageSlot> filter = getFilteredPages();
        StorageData data = StorageData.INSTANCE;

        int yOffset = (int) scroll;
        int xOffset = 0;
        int maxRowHeight = 0;
        int totalHeight = 0;

        for (var entry : data.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;

            StorageData.StorageInventory inv = entry.getValue();
            int rows = (inv.inventory() != null) ? inv.inventory().rows() : 1;
            int pageHeight = rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;
            maxRowHeight = Math.max(maxRowHeight, pageHeight);

            int x = gridX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
            int y = gridY + PADDING + totalHeight - yOffset;

            drawPage(graphics, x, y, entry.getKey(), inv, mouseX, mouseY);

            xOffset++;
            if (xOffset >= pageWidthCount) {
                totalHeight += maxRowHeight + PADDING;
                xOffset = 0;
                maxRowHeight = 0;
            }
        }
        lastRenderedInnerHeight = totalHeight + maxRowHeight;

        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    private void drawPage(GuiGraphics graphics, int x, int y,
                          StoragePageSlot pageSlot, StorageData.StorageInventory inventory,
                          int mouseX, int mouseY) {
        int rows = (inventory.inventory() != null) ? inventory.inventory().rows() : 1;
        int pageContentHeight = rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;

        int borderColor = parseColor(SkyblockEnhancementsConfig.storageInactivePageBorderColor);
        graphics.fill(x, y + mc.font.lineHeight + 4, x + PAGE_WIDTH,
                y + pageContentHeight, borderColor);
        graphics.fill(x + 1, y + mc.font.lineHeight + 5,
                x + PAGE_WIDTH - 1, y + pageContentHeight - 1, 0xFF2A2A3E);

        String title = inventory.title();
        if (title == null || title.isEmpty()) title = pageSlot.defaultName();
        graphics.drawString(mc.font, title, x + 4, y + 2, 0xFFFFFFFF, true);

        VirtualInventory inv = inventory.inventory();
        if (inv == null) {
            graphics.drawCenteredString(mc.font, "Not yet opened",
                    x + PAGE_WIDTH / 2, y + pageContentHeight / 2, 0xFF888888);
            return;
        }

        for (int i = 0; i < inv.stacks().size(); i++) {
            ItemStack stack = inv.stacks().get(i);
            int slotX = x + 2 + (i % 9) * SLOT_RENDER_SIZE;
            int slotY = y + mc.font.lineHeight + 6 + (i / 9) * SLOT_RENDER_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotX + SLOT_RENDER_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_RENDER_SIZE;

            int bgColor = isHovered ? 0x80808080
                    : (stack.isEmpty() ? 0x402A2A3E : 0x40808080);
            graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, bgColor);

            if (!searchQuery.isBlank() && !stack.isEmpty()
                    && matchesSearch(stack, searchQuery)) {
                graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE,
                        parseColor(SkyblockEnhancementsConfig.storageSearchHighlightColor));
            }

            if (!stack.isEmpty()) {
                graphics.renderFakeItem(stack, slotX + 1, slotY + 1);
                graphics.renderItemDecorations(mc.font, stack, slotX + 1, slotY + 1);

                if (isHovered) {
                    graphics.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
                }
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        Rect track = getScrollbarTrackRect();
        graphics.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x40FFFFFF);

        float maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;

        float visibleRatio = (float) innerScrollPanelHeight / lastRenderedInnerHeight;
        int knobHeight = Math.max(20, (int) (track.height * visibleRatio));
        int knobY = track.y + (int) ((scroll / maxScroll) * (track.height - knobHeight));
        graphics.fill(track.x, knobY, track.x + track.width, knobY + knobHeight, 0xC0FFFFFF);
    }

    private Rect getScrollPanelInner() {
        return new Rect(gridX + PADDING, gridY + PADDING,
                innerScrollPanelWidth, innerScrollPanelHeight);
    }

    private Rect getScrollbarTrackRect() {
        return new Rect(gridX + gridWidth - PADDING - SCROLL_BAR_WIDTH, gridY + PADDING,
                SCROLL_BAR_WIDTH, innerScrollPanelHeight);
    }

    // ── Search ──────────────────────────────────────────────────────────────

    private void onSearchChanged(String query) {
        this.searchQuery = query != null ? query : "";
        this.cachedSearch = null;
        scroll = 0f;
    }

    private Set<StoragePageSlot> getFilteredPages() {
        if (searchQuery.isBlank()) {
            return StorageData.INSTANCE.getInventories().keySet();
        }
        if (cachedSearch != null && cachedSearch.equals(searchQuery)) {
            return filteredPagesCache;
        }
        filteredPagesCache = StorageData.INSTANCE.getInventories().entrySet().stream()
                .filter(e -> e.getValue().inventory() == null
                        || e.getValue().inventory().stacks().stream()
                        .anyMatch(stack -> matchesSearch(stack, searchQuery)))
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        cachedSearch = searchQuery;
        return filteredPagesCache;
    }

    private boolean matchesSearch(ItemStack stack, String search) {
        if (stack.isEmpty()) return false;
        Set<String> words = new TreeSet<>(Arrays.asList(search.toLowerCase().split("\\s+")));

        String name = stack.getHoverName().getString().toLowerCase();
        words.removeIf(name::contains);
        if (words.isEmpty()) return true;

        List<Component> lore = stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
        for (Component line : lore) {
            String text = line.getString().toLowerCase();
            words.removeIf(text::contains);
            if (words.isEmpty()) return true;
        }
        return false;
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (searchField != null && searchField.isMouseOver(mx, my)) {
            return searchField.mouseClicked(event, doubleClick);
        }

        Rect track = getScrollbarTrackRect();
        if (track.contains(mx, my)) {
            knobGrabbed = true;
            float maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                float percentage = (float) ((my - track.y) / track.height);
                scroll = Mth.clamp(percentage * maxScroll, 0, maxScroll);
            }
            return true;
        }

        Rect scrollPanel = getScrollPanelInner();
        if (scrollPanel.contains(mx, my)) {
            StoragePageSlot clicked = findPageAt((int) mx, (int) my);
            if (clicked != null) {
                StorageOverlayLifecycle.onNavigateToPage(clicked);
                clicked.navigateTo();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (knobGrabbed) {
            knobGrabbed = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (knobGrabbed) {
            Rect track = getScrollbarTrackRect();
            float maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                float percentage = (float) ((event.y() - track.y) / track.height);
                scroll = Mth.clamp(percentage * maxScroll, 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect scrollPanel = getScrollPanelInner();
        if (scrollPanel.contains(mouseX, mouseY)) {
            float delta = (float) (scrollY * SkyblockEnhancementsConfig.storageScrollSpeed
                    * (SkyblockEnhancementsConfig.storageInverseScroll ? 1 : -1));
            scroll = clampScroll(scroll + delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (searchField != null && searchField.isFocused()) {
            return searchField.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchField != null && searchField.isFocused()) {
            return searchField.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        StorageOverlayLifecycle.onOverviewClosed();
        super.onClose();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private StoragePageSlot findPageAt(int mouseX, int mouseY) {
        Set<StoragePageSlot> filter = getFilteredPages();
        StorageData data = StorageData.INSTANCE;

        int yOffset = (int) scroll;
        int xOffset = 0;
        int maxRowHeight = 0;
        int totalHeight = 0;

        for (var entry : data.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;

            StorageData.StorageInventory inv = entry.getValue();
            int rows = (inv.inventory() != null) ? inv.inventory().rows() : 1;
            int pageHeight = rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;
            maxRowHeight = Math.max(maxRowHeight, pageHeight);

            int x = gridX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
            int y = gridY + PADDING + totalHeight - yOffset;

            if (mouseX >= x && mouseX < x + PAGE_WIDTH && mouseY >= y && mouseY < y + pageHeight) {
                return entry.getKey();
            }

            xOffset++;
            if (xOffset >= pageWidthCount) {
                totalHeight += maxRowHeight + PADDING;
                xOffset = 0;
                maxRowHeight = 0;
            }
        }
        return null;
    }

    private float getMaxScroll() {
        return Math.max(0, lastRenderedInnerHeight - innerScrollPanelHeight);
    }

    private float clampScroll(float value) {
        return Mth.clamp(value, 0f, getMaxScroll());
    }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                int rgba = (int) Long.parseLong(hex, 16);
                return (rgba >> 8) | ((rgba & 0xFF) << 24);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }
}
