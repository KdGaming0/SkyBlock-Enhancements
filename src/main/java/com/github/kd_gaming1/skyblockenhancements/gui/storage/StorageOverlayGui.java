package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageData;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageSlot;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractContainerScreenAccessor;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.SlotAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class StorageOverlayGui extends ContainerOverlay {

    private static final int SLOT_RENDER_SIZE = 18;
    private static final int PAGE_SLOTS_WIDTH = SLOT_RENDER_SIZE * 9;
    private static final int PAGE_WIDTH = PAGE_SLOTS_WIDTH + 4;
    private static final int PADDING = 10;
    private static final int SCROLL_BAR_WIDTH = 8;
    private static final int TOP_BAR_HEIGHT = 24;

    private static final int OVERLAY_BACKGROUND = 0xFF1A1A2E;
    private static final int PAGE_INNER_BG = 0xFF252538;
    private static final int SLOT_EMPTY_BG = 0x30151525;
    private static final int SLOT_OCCUPIED_BG = 0x30354550;
    private static final int SLOT_HOVERED_BG = 0x50AAAAAA;
    private static final int TITLE_ACTIVE = 0xFFFFD700;
    private static final int TITLE_INACTIVE = 0xFFCCCCCC;
    private static final int PLACEHOLDER_TEXT = 0xFF666677;

    private final AbstractContainerScreen<?> screen;
    private final StoragePageSlot activeSlot;
    private final Minecraft mc;

    private float scroll = 0f;
    private int lastRenderedInnerHeight = 0;
    private boolean knobGrabbed = false;

    private EditBox searchField;
    private String searchQuery = "";
    private String cachedSearch = null;
    private Set<StoragePageSlot> filteredPagesCache = Set.of();

    private int pageWidthCount;
    private int overviewX, overviewY;
    private int overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;
    private int guiLeft;
    private int guiTop;

    public StorageOverlayGui(AbstractContainerScreen<?> screen, StoragePageSlot activeSlot) {
        this.screen = screen;
        this.activeSlot = activeSlot;
        this.mc = Minecraft.getInstance();
    }

    @Override
    public void onInit(int screenWidth, int screenHeight) {
        for (int i = 0; i < StoragePageSlot.COUNT; i++) {
            StoragePageSlot slot = new StoragePageSlot(i);
            if (!StorageData.INSTANCE.hasInventory(slot)) {
                StorageData.INSTANCE.updateInventory(slot, slot.defaultName(), null);
            }
        }

        try {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            guiLeft = accessor.sbe$getLeftPos();
            guiTop = accessor.sbe$getTopPos();
        } catch (Exception e) {
            guiLeft = 0; guiTop = 0;
        }

        recalculateMeasurements();
        scroll = clampScroll(scroll);

        if (searchField == null) {
            this.searchField = new EditBox(mc.font, 0, 0, 140, 16, Component.literal("Search items..."));
            this.searchField.setMaxLength(64);
            this.searchField.setResponder(this::onSearchChanged);
            this.searchField.setBordered(true);
        }
        updateSearchFieldPosition();
    }

    private void recalculateMeasurements() {
        pageWidthCount = Math.max(1, Math.min(SkyblockEnhancementsConfig.storageOverlayColumns, (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING)));
        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth = innerScrollPanelWidth + 3 * PADDING + SCROLL_BAR_WIDTH;
        overviewX = screen.width / 2 - overviewWidth / 2;
        overviewY = PADDING * 2;

        int inventoryStartY = screen.height;
        try {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            inventoryStartY = accessor.sbe$getTopPos() + (accessor.sbe$getImageHeight() - 96);
        } catch (Exception ignored) {}

        int maxHeight = inventoryStartY - overviewY - PADDING;
        overviewHeight = Math.max(120, Math.min(maxHeight, SkyblockEnhancementsConfig.storageOverlayHeight));
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
        Rect scrollPanel = getScrollPanelInner();
        int contentYOffset = (int) scroll;
        Rect activeRect = findActivePageRect();

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;

            if (activeSlot == null || activeRect == null) {
                ((SlotAccessor) slot).setX(-9999);
                ((SlotAccessor) slot).setY(-9999);
            } else {
                int absoluteX = activeRect.x + 2 + (slot.index % 9) * SLOT_RENDER_SIZE;
                int absoluteY = activeRect.y + mc.font.lineHeight + 6 + (slot.index / 9) * SLOT_RENDER_SIZE - contentYOffset;

                if (absoluteY < scrollPanel.y - SLOT_RENDER_SIZE + 4 || absoluteY > scrollPanel.y + scrollPanel.height - 4) {
                    ((SlotAccessor) slot).setX(-9999);
                    ((SlotAccessor) slot).setY(-9999);
                } else {
                    ((SlotAccessor) slot).setX(absoluteX - guiLeft);
                    ((SlotAccessor) slot).setY(absoluteY - guiTop);
                }
            }
        }
    }

    private Rect findActivePageRect() {
        if (activeSlot == null) return null;
        Set<StoragePageSlot> filter = getFilteredPages();
        Rect[] result = { null };
        layoutedForEach(filter, (rect, pageSlot, inventory) -> {
            if (result[0] == null && pageSlot.equals(activeSlot)) result[0] = rect;
        });
        return result[0];
    }

    @Override
    public void render(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.fill(overviewX, overviewY, overviewX + overviewWidth, overviewY + overviewHeight, OVERLAY_BACKGROUND);

        Rect scrollPanel = getScrollPanelInner();
        graphics.enableScissor(scrollPanel.x, scrollPanel.y, scrollPanel.x + scrollPanel.width, scrollPanel.y + scrollPanel.height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, -scroll);

        Set<StoragePageSlot> filter = getFilteredPages();
        layoutedForEach(filter, (rect, pageSlot, inventory) -> {
            boolean isActive = pageSlot.equals(activeSlot);
            drawPage(graphics, rect, pageSlot, inventory, isActive, mouseX, mouseY);
        });

        graphics.pose().popMatrix();
        graphics.disableScissor();

        drawScrollbar(graphics);

        if (searchField != null) {
            searchField.render(graphics, mouseX, mouseY, delta);
        }
    }

    private Rect getScrollPanelInner() {
        return new Rect(overviewX + PADDING, overviewY + TOP_BAR_HEIGHT, innerScrollPanelWidth, innerScrollPanelHeight);
    }

    @FunctionalInterface
    private interface PageConsumer {
        void accept(Rect rect, StoragePageSlot pageSlot, StorageData.StorageInventory inventory);
    }

    private int calculatePageHeight(StorageData.StorageInventory inventory, StoragePageSlot pageSlot) {
        int rows = 1;
        if (pageSlot.equals(activeSlot)) {
            // Live container: Subtract 36 slots for the player inventory, divide remainder by 9 columns.
            rows = Math.max(1, (screen.getMenu().slots.size() - 36) / 9);
        } else if (inventory != null && inventory.inventory() != null) {
            rows = inventory.inventory().rows();
        }
        return Math.clamp(rows, 1, 6) * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;
    }

    private void layoutedForEach(Set<StoragePageSlot> filter, PageConsumer consumer) {
        int xOffset = 0, maxRowHeight = 0, totalHeight = 0;

        for (var entry : StorageData.INSTANCE.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;

            StorageData.StorageInventory inv = entry.getValue();
            int pageHeight = calculatePageHeight(inv, entry.getKey());
            maxRowHeight = Math.max(maxRowHeight, pageHeight);

            int x = overviewX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
            int y = overviewY + TOP_BAR_HEIGHT + totalHeight;

            consumer.accept(new Rect(x, y, PAGE_WIDTH, pageHeight), entry.getKey(), inv);

            xOffset++;
            if (xOffset >= pageWidthCount) {
                totalHeight += maxRowHeight + PADDING;
                xOffset = 0; maxRowHeight = 0;
            }
        }
        lastRenderedInnerHeight = totalHeight + maxRowHeight;
    }

    private void drawPage(GuiGraphics graphics, Rect rect, StoragePageSlot pageSlot, StorageData.StorageInventory inventory, boolean isActive, int mouseX, int mouseY) {
        int rows = 1;
        if (isActive) {
            rows = Math.max(1, (screen.getMenu().slots.size() - 36) / 9);
        } else if (inventory != null && inventory.inventory() != null) {
            rows = inventory.inventory().rows();
        }
        rows = Math.clamp(rows, 1, 6);

        int pageContentHeight = rows * SLOT_RENDER_SIZE + mc.font.lineHeight + 10;

        int borderColor = parseColor(isActive ? SkyblockEnhancementsConfig.storageActivePageOutlineColor : SkyblockEnhancementsConfig.storageInactivePageBorderColor);
        graphics.fill(rect.x, rect.y + mc.font.lineHeight + 4, rect.x + PAGE_WIDTH, rect.y + pageContentHeight, borderColor);
        graphics.fill(rect.x + 1, rect.y + mc.font.lineHeight + 5, rect.x + PAGE_WIDTH - 1, rect.y + pageContentHeight - 1, PAGE_INNER_BG);

        String title = (inventory != null && inventory.title() != null) ? inventory.title() : pageSlot.defaultName();
        graphics.drawString(mc.font, title, rect.x + 4, rect.y + 2, isActive ? TITLE_ACTIVE : TITLE_INACTIVE, true);

        if (inventory == null || inventory.inventory() == null) {
            graphics.drawCenteredString(mc.font, "Not yet opened", rect.x + PAGE_WIDTH / 2, rect.y + pageContentHeight / 2, PLACEHOLDER_TEXT);
            return;
        }

        List<ItemStack> stacks = isActive ? getLiveStacks() : inventory.inventory().stacks();
        int slotCount = Math.min(stacks.size(), rows * 9);
        int contentMouseY = mouseY + (int) scroll;

        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = stacks.get(i);
            int slotX = rect.x + 2 + (i % 9) * SLOT_RENDER_SIZE;
            int slotY = rect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_RENDER_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotX + SLOT_RENDER_SIZE && contentMouseY >= slotY && contentMouseY < slotY + SLOT_RENDER_SIZE;

            int bgColor = isHovered ? SLOT_HOVERED_BG : (stack.isEmpty() ? SLOT_EMPTY_BG : SLOT_OCCUPIED_BG);
            graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, bgColor);

            if (!searchQuery.isBlank() && !stack.isEmpty() && matchesSearch(stack, searchQuery)) {
                graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, parseColor(SkyblockEnhancementsConfig.storageSearchHighlightColor));
            }

            if (!stack.isEmpty() && !isActive) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(0, 0);
                graphics.renderFakeItem(stack, slotX + 1, slotY + 1);
                graphics.renderItemDecorations(mc.font, stack, slotX + 1, slotY + 1);
                graphics.pose().popMatrix();

                if (isHovered) {
                    graphics.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
                    graphics.fill(slotX, slotY, slotX + SLOT_RENDER_SIZE, slotY + 1, 0xFFFFFFFF);
                    graphics.fill(slotX, slotY + SLOT_RENDER_SIZE - 1, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, 0xFFFFFFFF);
                    graphics.fill(slotX, slotY, slotX + 1, slotY + SLOT_RENDER_SIZE, 0xFFFFFFFF);
                    graphics.fill(slotX + SLOT_RENDER_SIZE - 1, slotY, slotX + SLOT_RENDER_SIZE, slotY + SLOT_RENDER_SIZE, 0xFFFFFFFF);
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
        int rows = Math.max(1, (stacks.size() + 8) / 9);
        int target = Math.min(6, rows) * 9; // Allow up to 6 rows
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);
        return stacks;
    }

    private void drawScrollbar(GuiGraphics graphics) {
        Rect track = getScrollbarTrackRect();
        graphics.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x40FFFFFF);

        float maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;

        float visibleRatio = (float) innerScrollPanelHeight / Math.max(lastRenderedInnerHeight, 1);
        int knobHeight = Math.max(20, (int) (track.height * visibleRatio));
        int knobY = track.y + (int) ((scroll / maxScroll) * (track.height - knobHeight));

        graphics.fill(track.x, knobY, track.x + track.width, knobY + knobHeight, 0xC0FFFFFF);
    }

    private Rect getScrollbarTrackRect() {
        return new Rect(overviewX + overviewWidth - PADDING - SCROLL_BAR_WIDTH, overviewY + TOP_BAR_HEIGHT, SCROLL_BAR_WIDTH, innerScrollPanelHeight);
    }

    private void onSearchChanged(String query) {
        this.searchQuery = query != null ? query : "";
        this.cachedSearch = null;
        scroll = 0f;
    }

    private Set<StoragePageSlot> getFilteredPages() {
        if (searchQuery.isBlank()) return StorageData.INSTANCE.getInventories().keySet();
        if (cachedSearch != null && cachedSearch.equals(searchQuery)) return filteredPagesCache;

        filteredPagesCache = StorageData.INSTANCE.getInventories().entrySet().stream()
                .filter(e -> e.getValue() == null || e.getValue().inventory() == null || e.getValue().inventory().stacks().stream().anyMatch(stack -> matchesSearch(stack, searchQuery)))
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        cachedSearch = searchQuery;
        return filteredPagesCache;
    }

    private boolean matchesSearch(ItemStack stack, String search) {
        if (stack.isEmpty()) return false;
        Set<String> words = new TreeSet<>(Arrays.asList(search.toLowerCase().split("\\s+")));
        words.removeIf(stack.getHoverName().getString().toLowerCase()::contains);
        if (words.isEmpty()) return true;

        for (Component line : stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL)) {
            words.removeIf(line.getString().toLowerCase()::contains);
            if (words.isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (searchField != null) {
            if (searchField.mouseClicked(event, doubleClick)) {
                searchField.setFocused(true);
                return true;
            } else {
                searchField.setFocused(false);
            }
        }

        Rect track = getScrollbarTrackRect();
        if (track.contains(mx, my)) {
            knobGrabbed = true;
            float maxScroll = getMaxScroll();
            if (maxScroll > 0) scroll = Mth.clamp((float) ((my - track.y) / track.height) * maxScroll, 0, maxScroll);
            return true;
        }

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
            if (maxScroll > 0) scroll = Mth.clamp((float) ((event.y() - track.y) / track.height) * maxScroll, 0, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Rect scrollPanel = getScrollPanelInner();
        if (scrollPanel.contains(x, y)) {
            float delta = (float) (scrollY * SkyblockEnhancementsConfig.storageScrollSpeed * (SkyblockEnhancementsConfig.storageInverseScroll ? 1 : -1));
            scroll = clampScroll(scroll + delta);
            return true;
        }
        return false;
    }

    @Override
    public EditBox getSearchField() { return searchField; }

    @Override
    public boolean keyPressed(KeyEvent event) { return searchField != null && searchField.isFocused() && searchField.keyPressed(event); }

    @Override
    public boolean charTyped(CharacterEvent event) { return searchField != null && searchField.isFocused() && searchField.charTyped(event); }

    @Override
    public List<Rect> getBounds() {
        List<Rect> bounds = new ArrayList<>();
        bounds.add(new Rect(overviewX, overviewY, overviewWidth, overviewHeight));
        return bounds;
    }

    private StoragePageSlot findPageAt(int mouseX, int mouseY) {
        StoragePageSlot[] result = { null };
        layoutedForEach(getFilteredPages(), (rect, pageSlot, inventory) -> {
            if (result[0] == null && rect.contains(mouseX, mouseY + (int) scroll)) result[0] = pageSlot;
        });
        return result[0];
    }

    private float getMaxScroll() { return Math.max(0, lastRenderedInnerHeight - innerScrollPanelHeight); }
    private float clampScroll(float value) { return Mth.clamp(value, 0f, getMaxScroll()); }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
            if (hex.length() == 8) {
                int rgba = (int) Long.parseLong(hex, 16);
                return (rgba >> 8) | ((rgba & 0xFF) << 24);
            }
        } catch (NumberFormatException ignored) {}
        return 0xFFFFFFFF;
    }
}