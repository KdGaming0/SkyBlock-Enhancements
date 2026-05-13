package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageData;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayLifecycle;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageSlot;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.VirtualInventory;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContainerOverlay} implementation that renders the storage mini-page grid
 * on top of a live {@link AbstractContainerScreen}.
 */
public class StorageOverlayGui extends ContainerOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageOverlayGui.class);

    private static final int SLOT_RENDER_SIZE = 18;
    private static final int PAGE_SLOTS_WIDTH = SLOT_RENDER_SIZE * 9;
    private static final int PAGE_WIDTH = PAGE_SLOTS_WIDTH + 4;
    private static final int PADDING = 10;
    private static final int SCROLL_BAR_WIDTH = 8;
    private static final int TOP_BAR_HEIGHT = 24;

    // Color palette
    private static final int OVERLAY_BACKGROUND = 0xFF1A1A2E;
    private static final int PAGE_INNER_BG = 0xFF252538;
    private static final int SLOT_EMPTY_BG = 0x30151525;
    private static final int SLOT_OCCUPIED_BG = 0x30354550;
    private static final int SLOT_HOVERED_BG = 0x50AAAAAA;
    private static final int TITLE_ACTIVE = 0xFFFFD700;
    private static final int TITLE_INACTIVE = 0xFFCCCCCC;
    private static final int PLACEHOLDER_TEXT = 0xFF666677;

    // Texture sprites (optional, falls back to colors)
    private static final Identifier OVERLAY_BG_SPRITE =
            Identifier.fromNamespaceAndPath("skyblock_enhancements", "storage/overlay_background");

    private final AbstractContainerScreen<?> screen;
    private final StoragePageSlot activeSlot;
    private final Minecraft mc;

    // Scroll state
    private float scroll = 0f;
    private int lastRenderedInnerHeight = 0;
    private boolean knobGrabbed = false;
    private double knobGrabOffset = 0;

    // Search
    private EditBox searchField;
    private String searchQuery = "";
    private String cachedSearch = null;
    private Set<StoragePageSlot> filteredPagesCache = Set.of();

    // Active slot position cache for isHovering proxy
    private final Map<Slot, Rect> activeSlotPositions = new IdentityHashMap<>();

    // Measurements
    private int pageWidthCount;
    private int overviewX, overviewY;
    private int overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;

    public StorageOverlayGui(AbstractContainerScreen<?> screen, StoragePageSlot activeSlot) {
        this.screen = screen;
        this.activeSlot = activeSlot;
        this.mc = Minecraft.getInstance();
    }

    @Override
    public void onInit(int screenWidth, int screenHeight) {
        LOGGER.debug("StorageOverlayGui.onInit() called, screen={}x{}", screenWidth, screenHeight);

        // Pre-populate all 27 pages so the overlay always shows placeholders.
        // Do this BEFORE recalculateMeasurements so we know content exists.
        int added = 0;
        for (int i = 0; i < StoragePageSlot.COUNT; i++) {
            StoragePageSlot slot = new StoragePageSlot(i);
            if (!StorageData.INSTANCE.hasInventory(slot)) {
                StorageData.INSTANCE.updateInventory(slot, slot.defaultName(), null);
                added++;
            }
        }
        LOGGER.debug("StorageOverlayGui.onInit() added {} placeholder pages. Map now has {} entries.",
                added, StorageData.INSTANCE.getInventories().size());

        recalculateMeasurements();
        scroll = clampScroll(scroll);

        if (searchField == null) {
            this.searchField = new EditBox(mc.font, 0, 0, 140, 16, Component.literal("Search items..."));
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
                        (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING)));

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth = innerScrollPanelWidth + 3 * PADDING + SCROLL_BAR_WIDTH;

        overviewX = screen.width / 2 - overviewWidth / 2;
        overviewY = PADDING * 2;

        // Cap height so the overlay never overlaps the vanilla player inventory texture
        int inventoryStartY = screen.height;
        try {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int topPos = accessor.sbe$getTopPos();
            int imageHeight = accessor.sbe$getImageHeight();
            inventoryStartY = topPos + (imageHeight - 96); // 96 = inventory texture height
        } catch (Exception ignored) {
        }
        int maxHeight = inventoryStartY - overviewY - PADDING;
        overviewHeight = Math.min(maxHeight, SkyblockEnhancementsConfig.storageOverlayHeight);
        overviewHeight = Math.max(120, overviewHeight);
        innerScrollPanelHeight = overviewHeight - PADDING * 2 - TOP_BAR_HEIGHT;

        updateSearchFieldPosition();
    }

    private void updateSearchFieldPosition() {
        if (searchField != null) {
            searchField.setX(overviewX + PADDING);
            searchField.setY(overviewY + 4);
            searchField.setWidth(Math.max(100, overviewWidth - SCROLL_BAR_WIDTH - PADDING * 4));
        }
    }

    @Override
    public void preRender(int mouseX, int mouseY) {
        activeSlotPositions.clear();
        if (activeSlot == null) return;

        Rect activeRect = findActivePageRect();
        if (activeRect == null) return;

        List<ItemStack> stacks = getLiveStacks();
        int slotCount = Math.min(stacks.size(), 5 * 9);

        for (int i = 0; i < slotCount; i++) {
            int slotX = activeRect.x + 2 + (i % 9) * SLOT_RENDER_SIZE;
            int slotY = activeRect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_RENDER_SIZE - (int) scroll;
            Slot realSlot = findSlotByIndex(i);
            if (realSlot != null) {
                activeSlotPositions.put(realSlot,
                        new Rect(slotX, slotY, SLOT_RENDER_SIZE, SLOT_RENDER_SIZE));
            }
        }
    }

    private Rect findActivePageRect() {
        Set<StoragePageSlot> filter = getFilteredPages();
        StorageData data = StorageData.INSTANCE;
        Rect[] result = { null };

        if (!data.getInventories().isEmpty()) {
            layoutedForEach(data, filter, (rect, pageSlot, inventory) -> {
                if (result[0] == null && pageSlot.equals(activeSlot)) {
                    result[0] = rect;
                }
            });
        }

        if (result[0] == null) {
            int pageHeight = calculatePageHeight(null);
            result[0] = new Rect(overviewX + PADDING, overviewY + TOP_BAR_HEIGHT, PAGE_WIDTH, pageHeight);
        }

        return result[0];
    }

    @Override
    public void render(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        drawOverlayBackground(graphics);
        drawPages(graphics, mouseX, mouseY);
        drawScrollbar(graphics);

        if (searchField != null) {
            searchField.render(graphics, mouseX, mouseY, delta);
        }

        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty()) {
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(mc.font, carried, mouseX - 8, mouseY - 8);
        }
    }

    private void drawOverlayBackground(GuiGraphics graphics) {
        drawPanelBackground(graphics, overviewX, overviewY, overviewWidth, overviewHeight, OVERLAY_BACKGROUND);
    }

    private void drawPanelBackground(GuiGraphics graphics, int x, int y, int width, int height, int fallbackColor) {
        try {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OVERLAY_BG_SPRITE, x, y, width, height);
        } catch (Exception e) {
            // Sprite not available — fall back to solid color
            graphics.fill(x, y, x + width, y + height, fallbackColor);
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

        // DEBUG: Verify data is actually populated
        LOGGER.debug("drawPages: mapSize={}, filterSize={}, scroll={}",
                data.getInventories().size(), filter.size(), scroll);

        if (data.getInventories().isEmpty()) {
            renderDirectPlaceholders(graphics, mouseX, mouseY);
        } else {
            layoutedForEach(data, filter, (rect, pageSlot, inventory) -> {
                boolean isActive = pageSlot.equals(activeSlot);
                drawPage(graphics, rect, pageSlot, inventory, isActive, mouseX, mouseY);
            });
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    /**
     * Renders all 27 placeholder pages directly without depending on StorageData.
     * This is a safeguard for when the map is unexpectedly empty.
     */
    private void renderDirectPlaceholders(GuiGraphics graphics, int mouseX, int mouseY) {
        int yOffset = (int) scroll;
        int xOffset = 0;
        int maxRowHeight = 0;
        int totalHeight = 0;
        int pageHeight = 1 * SLOT_RENDER_SIZE + mc.font.lineHeight + 10; // placeholder = 1 row

        for (int i = 0; i < StoragePageSlot.COUNT; i++) {
            StoragePageSlot slot = new StoragePageSlot(i);
            maxRowHeight = Math.max(maxRowHeight, pageHeight);

            int x = overviewX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
            int y = overviewY + TOP_BAR_HEIGHT + totalHeight - yOffset;

            boolean isActive = slot.equals(activeSlot);
            drawPageDirect(graphics, x, y, slot, isActive, mouseX, mouseY);

            xOffset++;
            if (xOffset >= pageWidthCount) {
                totalHeight += maxRowHeight + PADDING;
                xOffset = 0;
                maxRowHeight = 0;
            }
        }
        lastRenderedInnerHeight = totalHeight + maxRowHeight;
    }

    private void drawPageDirect(GuiGraphics graphics, int x, int y,
                                StoragePageSlot pageSlot, boolean isActive,
                                int mouseX, int mouseY) {
        int rows = 1;
        int pageContentHeight = rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;

        // Border
        int borderColor = isActive
                ? parseColor(SkyblockEnhancementsConfig.storageActivePageOutlineColor)
                : parseColor(SkyblockEnhancementsConfig.storageInactivePageBorderColor);
        graphics.fill(x, y + mc.font.lineHeight + 4, x + PAGE_WIDTH,
                y + pageContentHeight, borderColor);

        // Inner background
        graphics.fill(x + 1, y + mc.font.lineHeight + 5,
                x + PAGE_WIDTH - 1, y + pageContentHeight - 1, PAGE_INNER_BG);

        // Title
        String title = pageSlot.defaultName();
        int titleColor = isActive ? TITLE_ACTIVE : TITLE_INACTIVE;
        graphics.drawString(mc.font, title, x + 4, y + 2, titleColor, true);

        // Placeholder text
        graphics.drawCenteredString(mc.font, "Not yet opened",
                x + PAGE_WIDTH / 2, y + pageContentHeight / 2, PLACEHOLDER_TEXT);
    }

    private Rect getScrollPanelInner() {
        return new Rect(overviewX + PADDING, overviewY + TOP_BAR_HEIGHT,
                innerScrollPanelWidth, innerScrollPanelHeight);
    }

    @FunctionalInterface
    private interface PageConsumer {
        void accept(Rect rect, StoragePageSlot pageSlot, StorageData.StorageInventory inventory);
    }

    private void layoutedForEach(StorageData data, Set<StoragePageSlot> filter, PageConsumer consumer) {
        int xOffset = 0;
        int maxRowHeight = 0;
        int totalHeight = 0;
        int entriesProcessed = 0;

        for (var entry : data.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;

            StorageData.StorageInventory inv = entry.getValue();
            int pageHeight = calculatePageHeight(inv);
            maxRowHeight = Math.max(maxRowHeight, pageHeight);

            int x = overviewX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
            int y = overviewY + TOP_BAR_HEIGHT + totalHeight;

            consumer.accept(new Rect(x, y, PAGE_WIDTH, pageHeight),
                    entry.getKey(), inv);
            entriesProcessed++;

            xOffset++;
            if (xOffset >= pageWidthCount) {
                totalHeight += maxRowHeight + PADDING;
                xOffset = 0;
                maxRowHeight = 0;
            }
        }

        lastRenderedInnerHeight = totalHeight + maxRowHeight;
        LOGGER.debug("layoutedForEach processed {} entries, lastRenderedInnerHeight={}",
                entriesProcessed, lastRenderedInnerHeight);
    }

    private int calculatePageHeight(StorageData.StorageInventory inventory) {
        if (inventory == null || inventory.inventory() == null) {
            return 1 * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;
        }
        int rows = inventory.inventory().rows();
        rows = Math.max(1, Math.min(5, rows));
        return rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;
    }

    private void drawPage(GuiGraphics graphics, Rect rect, StoragePageSlot pageSlot,
                          StorageData.StorageInventory inventory, boolean isActive,
                          int mouseX, int mouseY) {
        int x = rect.x;
        int y = rect.y;

        // Defensive: inventory should never be null, but handle it gracefully
        if (inventory == null) {
            drawPageDirect(graphics, x, y, pageSlot, isActive, mouseX, mouseY);
            return;
        }

        int rows = (inventory.inventory() != null)
                ? Math.max(1, Math.min(5, inventory.inventory().rows()))
                : 1;
        int pageContentHeight = rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;

        // Border
        int borderColor = isActive
                ? parseColor(SkyblockEnhancementsConfig.storageActivePageOutlineColor)
                : parseColor(SkyblockEnhancementsConfig.storageInactivePageBorderColor);
        graphics.fill(x, y + mc.font.lineHeight + 4, x + PAGE_WIDTH,
                y + pageContentHeight, borderColor);

        // Inner background
        graphics.fill(x + 1, y + mc.font.lineHeight + 5,
                x + PAGE_WIDTH - 1, y + pageContentHeight - 1, PAGE_INNER_BG);

        // Title
        String title = inventory.title();
        if (title == null || title.isEmpty()) title = pageSlot.defaultName();
        int titleColor = isActive ? TITLE_ACTIVE : TITLE_INACTIVE;
        graphics.drawString(mc.font, title, x + 4, y + 2, titleColor, true);

        VirtualInventory inv = inventory.inventory();
        if (inv == null) {
            // Placeholder for never-opened page
            graphics.drawCenteredString(mc.font, "Not yet opened",
                    x + PAGE_WIDTH / 2, y + pageContentHeight / 2, PLACEHOLDER_TEXT);
            return;
        }

        List<ItemStack> stacks = isActive ? getLiveStacks() : inv.stacks();
        int slotCount = Math.min(stacks.size(), rows * 9);

        // Convert screen mouse Y to content-space for hit-testing against un-scrolled slots
        int contentMouseY = mouseY + (int) scroll;

        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = stacks.get(i);
            int slotX = x + 2 + (i % 9) * SLOT_RENDER_SIZE;
            int slotY = y + mc.font.lineHeight + 6 + (i / 9) * SLOT_RENDER_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotX + SLOT_RENDER_SIZE
                    && contentMouseY >= slotY && contentMouseY < slotY + SLOT_RENDER_SIZE;

            // Slot background
            int bgColor = isHovered ? SLOT_HOVERED_BG
                    : (stack.isEmpty() ? SLOT_EMPTY_BG : SLOT_OCCUPIED_BG);
            graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, bgColor);

            // Search highlight
            if (!searchQuery.isBlank() && !stack.isEmpty()
                    && matchesSearch(stack, searchQuery)) {
                graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE,
                        parseColor(SkyblockEnhancementsConfig.storageSearchHighlightColor));
            }

            if (!stack.isEmpty()) {
                // Use renderFakeItem for cached slots, renderItem for active page
                if (isActive) {
                    graphics.renderItem(stack, slotX + 1, slotY + 1);
                } else {
                    graphics.renderFakeItem(stack, slotX + 1, slotY + 1);
                }
                graphics.renderItemDecorations(mc.font, stack, slotX + 1, slotY + 1);

                if (isHovered) {
                    graphics.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
                    // White hover border
                    graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + 1, 0xFFFFFFFF);
                    graphics.fill(slotX, slotY + SLOT_RENDER_SIZE - 1,
                            slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, 0xFFFFFFFF);
                    graphics.fill(slotX, slotY, slotX + 1, slotY + SLOT_RENDER_SIZE, 0xFFFFFFFF);
                    graphics.fill(slotX + SLOT_RENDER_SIZE - 1, slotY,
                            slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, 0xFFFFFFFF);
                }
            }
        }
    }

    private List<ItemStack> getLiveStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
                while (stacks.size() <= slot.index) stacks.add(ItemStack.EMPTY);
                stacks.set(slot.index, slot.getItem());
            }
        }
        // Pad or trim to a valid page size (multiples of 9, max 45)
        int rows = Math.max(1, Math.min(5, (stacks.size() + 8) / 9));
        int target = rows * 9;
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);
        return stacks;
    }

    private Slot findSlotByIndex(int index) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory() && slot.index == index) {
                return slot;
            }
        }
        return null;
    }

    private void drawScrollbar(GuiGraphics graphics) {
        Rect track = getScrollbarTrackRect();
        // Track background
        graphics.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x40FFFFFF);

        float maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;

        float visibleRatio = (float) innerScrollPanelHeight / Math.max(lastRenderedInnerHeight, 1);
        int knobHeight = Math.max(20, (int) (track.height * visibleRatio));
        int knobY = track.y + (int) ((scroll / maxScroll) * (track.height - knobHeight));

        graphics.fill(track.x, knobY, track.x + track.width, knobY + knobHeight, 0xC0FFFFFF);
    }

    private Rect getScrollbarTrackRect() {
        return new Rect(overviewX + overviewWidth - PADDING - SCROLL_BAR_WIDTH,
                overviewY + TOP_BAR_HEIGHT,
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
                .filter(e -> e.getValue() == null
                        || e.getValue().inventory() == null
                        || e.getValue().inventory().stacks().stream()
                        .anyMatch(stack -> matchesSearch(stack, searchQuery)))
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        cachedSearch = searchQuery;
        return filteredPagesCache;
    }

    private boolean matchesSearch(ItemStack stack, String search) {
        if (stack.isEmpty()) return false;
        Set<String> words = new TreeSet<>(Arrays.asList(search.toLowerCase().split("\s+")));

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

    // ── Input handling ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // Scrollbar drag
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

        // Click on inactive page
        Rect scrollPanel = getScrollPanelInner();
        if (scrollPanel.contains(mx, my)) {
            StoragePageSlot clicked = findPageAt((int) mx, (int) my);
            if (clicked != null && !clicked.equals(activeSlot)) {
                clicked.navigateTo();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (knobGrabbed) {
            knobGrabbed = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (knobGrabbed) {
            Rect track = getScrollbarTrackRect();
            float maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                float percentage = (float) ((event.y() - track.y) / track.height);
                scroll = Mth.clamp(percentage * maxScroll, 0, maxScroll);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Rect scrollPanel = getScrollPanelInner();
        if (scrollPanel.contains(x, y)) {
            float delta = (float) (scrollY * SkyblockEnhancementsConfig.storageScrollSpeed
                    * (SkyblockEnhancementsConfig.storageInverseScroll ? 1 : -1));
            scroll = clampScroll(scroll + delta);
            return true;
        }
        return false;
    }

    @Override
    public EditBox getSearchField() {
        return searchField;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchField != null && searchField.isFocused()) {
            return searchField.keyPressed(event);
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchField != null && searchField.isFocused()) {
            return searchField.charTyped(event);
        }
        return false;
    }

    // ── Slot hit-testing ────────────────────────────────────────────────────

    @Override
    public boolean isPointOverSlot(Slot slot, double pointX, double pointY) {
        if (slot.container == mc.player.getInventory()) return false;
        if (activeSlot == null) return false;

        Rect pos = activeSlotPositions.get(slot);
        if (pos != null) {
            return pointX >= pos.x - 1 && pointX < pos.x + pos.width + 1
                    && pointY >= pos.y - 1 && pointY < pos.y + pos.height + 1;
        }
        return false;
    }

    @Override
    public List<Rect> getBounds() {
        List<Rect> bounds = new ArrayList<>();
        bounds.add(new Rect(overviewX, overviewY, overviewWidth, overviewHeight));
        return bounds;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private StoragePageSlot findPageAt(int mouseX, int mouseY) {
        // Check data-driven pages first
        Set<StoragePageSlot> filter = getFilteredPages();
        StorageData data = StorageData.INSTANCE;
        StoragePageSlot[] result = { null };

        if (!data.getInventories().isEmpty()) {
            layoutedForEach(data, filter, (rect, pageSlot, inventory) -> {
                if (result[0] == null && rect.contains(mouseX, mouseY + (int) scroll)) {
                    result[0] = pageSlot;
                }
            });
        }

        // If no data pages matched, check direct placeholders
        if (result[0] == null && data.getInventories().isEmpty()) {
            int yOffset = (int) scroll;
            int xOffset = 0;
            int maxRowHeight = 0;
            int totalHeight = 0;
            int pageHeight = 1 * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;

            for (int i = 0; i < StoragePageSlot.COUNT && result[0] == null; i++) {
                StoragePageSlot slot = new StoragePageSlot(i);
                maxRowHeight = Math.max(maxRowHeight, pageHeight);
                int x = overviewX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
                int y = overviewY + TOP_BAR_HEIGHT + totalHeight - yOffset;
                Rect rect = new Rect(x, y, PAGE_WIDTH, pageHeight);
                if (rect.contains(mouseX, mouseY + yOffset)) {
                    result[0] = slot;
                }
                xOffset++;
                if (xOffset >= pageWidthCount) {
                    totalHeight += maxRowHeight + PADDING;
                    xOffset = 0;
                    maxRowHeight = 0;
                }
            }
        }

        return result[0];
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
                // RGBA format -> ARGB
                int rgba = (int) Long.parseLong(hex, 16);
                return (rgba >> 8) | ((rgba & 0xFF) << 24);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }
}
