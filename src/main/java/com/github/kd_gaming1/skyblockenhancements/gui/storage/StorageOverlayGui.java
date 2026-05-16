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

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SLOT_SIZE        = 18;
    private static final int PAGE_SLOTS_WIDTH = SLOT_SIZE * 9;
    private static final int PAGE_WIDTH       = PAGE_SLOTS_WIDTH + 4;  // 2 px padding each side
    private static final int PADDING          = 8;
    private static final int SCROLL_BAR_W     = 6;
    private static final int TOP_BAR_H        = 26;
    /**
     * Extra pixels the player inventory is pushed down, giving the overlay that
     * much additional vertical space.  Adjust to taste – 36 px = 2 slot rows.
     */
    private static final int PLAYER_INV_PUSH  = 36;

    // ── Colour palette ────────────────────────────────────────────────────────
    /** Main overlay panel – semi-transparent, very dark indigo */
    private static final int COL_OVERLAY_BG    = 0xD4080810;
    /** Individual page card background */
    private static final int COL_PAGE_BG       = 0xFF141422;
    /** Page card border (default) */
    private static final int COL_PAGE_BORDER   = 0xFF2E2E4A;
    /** Slot fill – empty */
    private static final int COL_SLOT_EMPTY    = 0xFF1C1C2C;
    /** Slot fill – occupied */
    private static final int COL_SLOT_ITEM     = 0xFF22223A;
    /** Slot border – top-left (dark shadow = sunken look) */
    private static final int COL_SLOT_TL       = 0xFF0F0F1E;
    /** Slot border – bottom-right (lighter = sunken look) */
    private static final int COL_SLOT_BR       = 0xFF3A3A5C;
    /** Slot hover highlight */
    private static final int COL_SLOT_HOVER    = 0x60FFFFFF;
    /** Active page title */
    private static final int COL_TITLE_ACTIVE  = 0xFFFFD700;
    /** Inactive page title */
    private static final int COL_TITLE_INACTIVE = 0xFFAAAAAA;
    /** "Not yet opened" placeholder text */
    private static final int COL_PLACEHOLDER   = 0xFF55556A;
    /** Background drawn behind the pushed-down player inventory */
    private static final int COL_PLAYER_INV_BG = 0xCC0A0A16;
    /** Thin separator line above player inventory */
    private static final int COL_SEPARATOR     = 0xFF35355A;

    // ── State ─────────────────────────────────────────────────────────────────
    private final AbstractContainerScreen<?> screen;
    private final StoragePageSlot activeSlot;
    private final Minecraft mc;

    /** Original Y values of the player inventory slots, captured at construction
     *  time before any per-frame push has been applied. */
    private final int[] originalPlayerSlotY;

    private float scroll = 0f;
    private int lastRenderedInnerHeight = 0;
    private boolean knobGrabbed = false;

    private EditBox searchField;
    private String searchQuery = "";
    private String cachedSearch = null;
    private Set<StoragePageSlot> filteredPagesCache = Set.of();

    // Measured layout values (recalculated in onInit / resize)
    private int pageWidthCount;
    private int overviewX, overviewY;
    private int overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;
    private int guiLeft, guiTop;

    // ── Constructor ───────────────────────────────────────────────────────────
    public StorageOverlayGui(AbstractContainerScreen<?> screen, StoragePageSlot activeSlot) {
        this.screen     = screen;
        this.activeSlot = activeSlot;
        this.mc         = Minecraft.getInstance();

        // Capture player-slot Y positions NOW, before any preRender pushes them.
        List<Slot> ps = getPlayerSlots();
        this.originalPlayerSlotY = new int[ps.size()];
        for (int i = 0; i < ps.size(); i++) {
            this.originalPlayerSlotY[i] = ps.get(i).y;
        }
    }

    // ── ContainerOverlay ──────────────────────────────────────────────────────
    @Override
    public void onInit(int screenWidth, int screenHeight) {
        for (int i = 0; i < StoragePageSlot.COUNT; i++) {
            StoragePageSlot slot = new StoragePageSlot(i);
            if (!StorageData.INSTANCE.hasInventory(slot)) {
                StorageData.INSTANCE.updateInventory(slot, slot.defaultName(), null);
            }
        }

        try {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
            guiLeft = a.sbe$getLeftPos();
            guiTop  = a.sbe$getTopPos();
        } catch (Exception e) {
            guiLeft = 0;
            guiTop  = 0;
        }

        recalculateMeasurements();
        scroll = clampScroll(scroll);

        if (searchField == null) {
            searchField = new EditBox(mc.font, 0, 0, 140, 16, Component.literal("Search items..."));
            searchField.setMaxLength(64);
            searchField.setResponder(this::onSearchChanged);
            searchField.setBordered(true);
        }
        updateSearchFieldPosition();
    }

    /**
     * Called every frame, BEFORE hover/slot evaluation.
     *
     * <ul>
     *   <li>Chest slots → moved to their visual overlay position (active page)
     *       or to off-screen (-9999) so vanilla cannot hover or tooltip them.</li>
     *   <li>Player inventory slots → pushed down by {@link #PLAYER_INV_PUSH}.</li>
     * </ul>
     */
    @Override
    public void preRender(int mouseX, int mouseY) {
        Rect scrollPanel   = getScrollPanelInner();
        int contentYOffset = (int) scroll;
        Rect activeRect    = findActivePageRect();

        // ── Reposition chest slots ────────────────────────────────────────────
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;

            if (activeSlot == null || activeRect == null) {
                // Overview screen: no live page → hide every chest slot.
                ((SlotAccessor) slot).setX(-9999);
                ((SlotAccessor) slot).setY(-9999);
            } else {
                int absX = activeRect.x + 2 + (slot.index % 9) * SLOT_SIZE;
                int absY = activeRect.y + mc.font.lineHeight + 6 + (slot.index / 9) * SLOT_SIZE - contentYOffset;

                boolean visible = absY >= scrollPanel.y - SLOT_SIZE + 4
                        && absY <= scrollPanel.y + scrollPanel.height - 4;
                if (visible) {
                    ((SlotAccessor) slot).setX(absX - guiLeft);
                    ((SlotAccessor) slot).setY(absY - guiTop);
                } else {
                    ((SlotAccessor) slot).setX(-9999);
                    ((SlotAccessor) slot).setY(-9999);
                }
            }
        }

        // ── Push player inventory slots down ──────────────────────────────────
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotY.length); i++) {
            ((SlotAccessor) playerSlots.get(i)).setY(originalPlayerSlotY[i] + PLAYER_INV_PUSH);
        }
    }

    @Override
    public void render(GuiGraphics gfx, float delta, int mouseX, int mouseY) {
        // ── Cover the vanilla chest background ────────────────────────────────
        try {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
            int vLeft   = a.sbe$getLeftPos();
            int vTop    = a.sbe$getTopPos();
            int vWidth  = a.sbe$getImageWidth();
            int firstPSlotOrigY = originalPlayerSlotY.length > 0
                    ? originalPlayerSlotY[originalPlayerSlotY.length - 36]   // first of last 36 = first hotbar-row area
                    : screen.getMenu().slots.get(screen.getMenu().slots.size() - 36).y - PLAYER_INV_PUSH;
            int chestEndY = vTop + firstPSlotOrigY - 12;
            gfx.fill(vLeft, vTop, vLeft + vWidth, chestEndY, COL_OVERLAY_BG);
        } catch (Exception ignored) {}

        // ── Main overlay panel ────────────────────────────────────────────────
        // Rounded-ish look via slightly contrasting border
        gfx.fill(overviewX,     overviewY,      overviewX + overviewWidth,     overviewY + overviewHeight,     COL_PAGE_BORDER);
        gfx.fill(overviewX + 1, overviewY + 1,  overviewX + overviewWidth - 1, overviewY + overviewHeight - 1, COL_OVERLAY_BG);

        // ── Scrollable page grid ──────────────────────────────────────────────
        Rect scrollPanel = getScrollPanelInner();
        gfx.enableScissor(scrollPanel.x, scrollPanel.y,
                scrollPanel.x + scrollPanel.width,
                scrollPanel.y + scrollPanel.height);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0f, -scroll);

        Set<StoragePageSlot> filter = getFilteredPages();
        layoutedForEach(filter, (rect, pageSlot, inventory) -> {
            boolean isActive = pageSlot.equals(activeSlot);
            drawPage(gfx, rect, pageSlot, inventory, isActive, mouseX, mouseY, scrollPanel);
        });

        gfx.pose().popMatrix();
        gfx.disableScissor();

        // ── Scrollbar ─────────────────────────────────────────────────────────
        drawScrollbar(gfx);

        // ── Search field ──────────────────────────────────────────────────────
        if (searchField != null) {
            searchField.render(gfx, mouseX, mouseY, delta);
        }

        // ── Player-inventory background (pushed-down area) ────────────────────
        drawPlayerInventoryBackground(gfx);
    }

    @Override
    public boolean shouldDrawForeground() { return false; }

    @Override
    public List<Rect> getBounds() {
        List<Rect> bounds = new ArrayList<>();
        bounds.add(new Rect(overviewX, overviewY, overviewWidth, overviewHeight));
        return bounds;
    }

    @Override
    public EditBox getSearchField() { return searchField; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return searchField != null && searchField.isFocused() && searchField.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return searchField != null && searchField.isFocused() && searchField.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

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
            if (maxScroll > 0)
                scroll = Mth.clamp((float) ((my - track.y) / track.height) * maxScroll, 0, maxScroll);
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
        if (knobGrabbed) { knobGrabbed = false; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (knobGrabbed) {
            Rect track = getScrollbarTrackRect();
            float maxScroll = getMaxScroll();
            if (maxScroll > 0)
                scroll = Mth.clamp((float) ((event.y() - track.y) / track.height) * maxScroll, 0, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Rect scrollPanel = getScrollPanelInner();
        if (scrollPanel.contains(x, y)) {
            float d = (float) (scrollY * SkyblockEnhancementsConfig.storageScrollSpeed
                    * (SkyblockEnhancementsConfig.storageInverseScroll ? 1 : -1));
            scroll = clampScroll(scroll + d);
            return true;
        }
        return false;
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void recalculateMeasurements() {
        pageWidthCount = Math.clamp(
                (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING),
                1,
                SkyblockEnhancementsConfig.storageOverlayColumns);

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth  = innerScrollPanelWidth + 3 * PADDING + SCROLL_BAR_W;
        overviewX      = screen.width / 2 - overviewWidth / 2;
        overviewY      = PADDING;   // sit closer to the top of the screen

        // Calculate the pushed-down position of the player inventory.
        int inventoryStartY = screen.height;
        try {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
            // originalPlayerSlotY[last-36] is the first player slot's original Y
            int firstPSlotOrigY = (originalPlayerSlotY.length >= 36)
                    ? originalPlayerSlotY[originalPlayerSlotY.length - 36]
                    : screen.getMenu().slots.get(screen.getMenu().slots.size() - 36).y;
            // After the push the inventory starts here; leave 12 px breathing room above it.
            inventoryStartY = a.sbe$getTopPos() + firstPSlotOrigY - 12 + PLAYER_INV_PUSH;
        } catch (Exception ignored) {}

        int maxHeight = inventoryStartY - overviewY;
        overviewHeight = Math.clamp(maxHeight, 120, SkyblockEnhancementsConfig.storageOverlayHeight);
        innerScrollPanelHeight = overviewHeight - PADDING * 2 - TOP_BAR_H;
        updateSearchFieldPosition();
    }

    private void updateSearchFieldPosition() {
        if (searchField == null) return;
        searchField.setX(overviewX + PADDING);
        searchField.setY(overviewY + 5);
        searchField.setWidth(Math.max(100, overviewWidth - SCROLL_BAR_W - PADDING * 4));
    }

    private Rect getScrollPanelInner() {
        return new Rect(overviewX + PADDING,
                overviewY + TOP_BAR_H,
                innerScrollPanelWidth,
                innerScrollPanelHeight);
    }

    private Rect getScrollbarTrackRect() {
        return new Rect(overviewX + overviewWidth - PADDING - SCROLL_BAR_W,
                overviewY + TOP_BAR_H,
                SCROLL_BAR_W,
                innerScrollPanelHeight);
    }

    // ── Page drawing ──────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface PageConsumer {
        void accept(Rect rect, StoragePageSlot pageSlot, StorageData.StorageInventory inventory);
    }

    private int calculatePageHeight(StorageData.StorageInventory inventory, StoragePageSlot pageSlot) {
        int rows = 1;
        if (pageSlot.equals(activeSlot)) {
            rows = Math.max(1, (screen.getMenu().slots.size() - 36) / 9);
        } else if (inventory != null && inventory.inventory() != null) {
            rows = inventory.inventory().rows();
        }
        return Math.clamp(rows, 1, 6) * SLOT_SIZE + mc.font.lineHeight + 10;
    }

    private void layoutedForEach(Set<StoragePageSlot> filter, PageConsumer consumer) {
        int xOffset = 0, maxRowH = 0, totalH = 0;
        for (var entry : StorageData.INSTANCE.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;

            StorageData.StorageInventory inv = entry.getValue();
            int pageH = calculatePageHeight(inv, entry.getKey());
            maxRowH = Math.max(maxRowH, pageH);

            int x = overviewX + PADDING + (PAGE_WIDTH + PADDING) * xOffset;
            int y = overviewY + TOP_BAR_H + totalH;
            consumer.accept(new Rect(x, y, PAGE_WIDTH, pageH), entry.getKey(), inv);

            xOffset++;
            if (xOffset >= pageWidthCount) {
                totalH += maxRowH + PADDING;
                xOffset = 0;
                maxRowH = 0;
            }
        }
        lastRenderedInnerHeight = totalH + maxRowH;
    }

    /**
     * Draw one storage-page card.
     *
     * @param scrollPanel  the visible clip region in SCREEN space (not scroll-offset space),
     *                     used to gate tooltip emission so off-screen items never show tooltips.
     */
    private void drawPage(GuiGraphics gfx, Rect rect, StoragePageSlot pageSlot,
                          StorageData.StorageInventory inventory, boolean isActive,
                          int mouseX, int mouseY, Rect scrollPanel) {
        int rows = 1;
        if (isActive) {
            rows = Math.max(1, (screen.getMenu().slots.size() - 36) / 9);
        } else if (inventory != null && inventory.inventory() != null) {
            rows = inventory.inventory().rows();
        }
        rows = Math.clamp(rows, 1, 6);

        int pageContentH = rows * SLOT_SIZE + mc.font.lineHeight + 10;

        // ── Card border + fill ────────────────────────────────────────────────
        int borderCol = parseColor(isActive
                ? SkyblockEnhancementsConfig.storageActivePageOutlineColor
                : SkyblockEnhancementsConfig.storageInactivePageBorderColor);
        int cardTop = rect.y + mc.font.lineHeight + 4;

        // Outer border
        gfx.fill(rect.x, cardTop, rect.x + PAGE_WIDTH, rect.y + pageContentH, borderCol);
        // Inner fill
        gfx.fill(rect.x + 1, cardTop + 1, rect.x + PAGE_WIDTH - 1, rect.y + pageContentH - 1, COL_PAGE_BG);

        // ── Title ─────────────────────────────────────────────────────────────
        String title = (inventory != null && inventory.title() != null)
                ? inventory.title()
                : pageSlot.defaultName();
        gfx.drawString(mc.font, title, rect.x + 4, rect.y + 2,
                isActive ? COL_TITLE_ACTIVE : COL_TITLE_INACTIVE, true);

        // ── "Not yet opened" placeholder ──────────────────────────────────────
        if (inventory == null || inventory.inventory() == null) {
            gfx.drawCenteredString(mc.font, "Not yet opened",
                    rect.x + PAGE_WIDTH / 2, rect.y + pageContentH / 2, COL_PLACEHOLDER);
            return;
        }

        // ── Item grid ─────────────────────────────────────────────────────────
        List<ItemStack> stacks  = isActive ? getLiveStacks() : inventory.inventory().stacks();
        int slotCount           = Math.min(stacks.size(), rows * 9);
        int contentMouseY       = mouseY + (int) scroll;   // mouse Y in scroll-offset space

        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = stacks.get(i);
            int slotX = rect.x + 2 + (i % 9) * SLOT_SIZE;
            int slotY = rect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_SIZE;

            // ── Vanilla-style inset slot ───────────────────────────────────
            // Top + left dark shadow  (makes slot look sunken)
            gfx.fill(slotX,          slotY,          slotX + SLOT_SIZE, slotY + 1,          COL_SLOT_TL);
            gfx.fill(slotX,          slotY,          slotX + 1,         slotY + SLOT_SIZE,  COL_SLOT_TL);
            // Bottom + right lighter highlight
            gfx.fill(slotX,          slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, COL_SLOT_BR);
            gfx.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,   COL_SLOT_BR);
            // Inner fill
            int fillCol = stack.isEmpty() ? COL_SLOT_EMPTY : COL_SLOT_ITEM;
            gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, fillCol);

            // ── Search highlight ───────────────────────────────────────────
            if (!searchQuery.isBlank() && !stack.isEmpty() && matchesSearch(stack, searchQuery)) {
                gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        parseColor(SkyblockEnhancementsConfig.storageSearchHighlightColor));
            }

            if (!stack.isEmpty() && !isActive) {
                // Render the fake item
                gfx.pose().pushMatrix();
                gfx.renderFakeItem(stack, slotX + 1, slotY + 1);
                gfx.renderItemDecorations(mc.font, stack, slotX + 1, slotY + 1);
                gfx.pose().popMatrix();

                // ── Hover highlight + tooltip ──────────────────────────────
                // TOOLTIP BUG FIX: only emit the tooltip if the slot is actually
                // inside the scissor viewport (screen coordinates).
                // Screen Y of the slot = slotY - scroll.
                int screenSlotY = slotY - (int) scroll;
                boolean inViewport = screenSlotY      >= scrollPanel.y
                        && screenSlotY + SLOT_SIZE <= scrollPanel.y + scrollPanel.height
                        && slotX          >= scrollPanel.x
                        && slotX + SLOT_SIZE <= scrollPanel.x + scrollPanel.width;

                boolean isHovered = inViewport
                        && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && contentMouseY >= slotY && contentMouseY < slotY + SLOT_SIZE;

                if (isHovered) {
                    // White-border highlight (same as vanilla slot hover)
                    gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, COL_SLOT_HOVER);
                    gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
                }
            }
        }
    }

    // ── Scrollbar ─────────────────────────────────────────────────────────────

    private void drawScrollbar(GuiGraphics gfx) {
        Rect track = getScrollbarTrackRect();
        // Track background
        gfx.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x30FFFFFF);

        float maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;

        float visibleRatio = (float) innerScrollPanelHeight / Math.max(lastRenderedInnerHeight, 1);
        int knobH = Math.max(20, (int) (track.height * visibleRatio));
        int knobY = track.y + (int) ((scroll / maxScroll) * (track.height - knobH));
        // Knob
        gfx.fill(track.x,     knobY,     track.x + track.width,     knobY + knobH,     0xC0FFFFFF);
        // Inner knob (slightly darker, for depth)
        gfx.fill(track.x + 1, knobY + 1, track.x + track.width - 1, knobY + knobH - 1, 0x80AAAAAA);
    }

    // ── Player-inventory background ───────────────────────────────────────────

    /**
     * Draws a background panel behind the pushed-down player inventory so that
     * the gap between the overlay and the inventory doesn't look hollow.
     */
    private void drawPlayerInventoryBackground(GuiGraphics gfx) {
        try {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
            int firstPSlotOrigY = (originalPlayerSlotY.length >= 36)
                    ? originalPlayerSlotY[originalPlayerSlotY.length - 36]
                    : screen.getMenu().slots.get(screen.getMenu().slots.size() - 36).y - PLAYER_INV_PUSH;

            int vLeft   = a.sbe$getLeftPos();
            int vTop    = a.sbe$getTopPos();
            int vWidth  = a.sbe$getImageWidth();
            // Where the pushed inventory starts (12 px above the first slot row)
            int panelY  = vTop + firstPSlotOrigY - 12 + PLAYER_INV_PUSH;
            // Enough height for 4 slot rows + hotbar row + padding
            int panelH  = 4 * SLOT_SIZE + SLOT_SIZE + 14;

            // Background fill
            gfx.fill(vLeft, panelY, vLeft + vWidth, panelY + panelH, COL_PLAYER_INV_BG);
            // Top separator line
            gfx.fill(vLeft, panelY, vLeft + vWidth, panelY + 1, COL_SEPARATOR);
        } catch (Exception ignored) {}
    }

    // ── Search + filter ───────────────────────────────────────────────────────

    private void onSearchChanged(String query) {
        this.searchQuery  = query != null ? query : "";
        this.cachedSearch = null;
        scroll = 0f;
    }

    private Set<StoragePageSlot> getFilteredPages() {
        if (searchQuery.isBlank()) return StorageData.INSTANCE.getInventories().keySet();
        if (cachedSearch != null && cachedSearch.equals(searchQuery)) return filteredPagesCache;

        filteredPagesCache = StorageData.INSTANCE.getInventories().entrySet().stream()
                .filter(e -> e.getValue() == null
                        || e.getValue().inventory() == null
                        || e.getValue().inventory().stacks().stream()
                        .anyMatch(s -> matchesSearch(s, searchQuery)))
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
        for (Component line : stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                mc.player,
                TooltipFlag.Default.NORMAL)) {
            words.removeIf(line.getString().toLowerCase()::contains);
            if (words.isEmpty()) return true;
        }
        return false;
    }

    // ── Click helpers ─────────────────────────────────────────────────────────

    private StoragePageSlot findPageAt(int mx, int my) {
        StoragePageSlot[] result = { null };
        layoutedForEach(getFilteredPages(), (rect, pageSlot, inventory) -> {
            if (result[0] == null && rect.contains(mx, my + (int) scroll)) result[0] = pageSlot;
        });
        return result[0];
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

    // ── Live-slot helpers ─────────────────────────────────────────────────────

    private List<ItemStack> getLiveStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
                while (stacks.size() <= slot.index) stacks.add(ItemStack.EMPTY);
                stacks.set(slot.index, slot.getItem());
            }
        }
        int rows   = Math.max(1, (stacks.size() + 8) / 9);
        int target = Math.min(6, rows) * 9;
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);
        return stacks;
    }

    private List<Slot> getPlayerSlots() {
        return screen.getMenu().slots.stream()
                .filter(s -> s.container == mc.player.getInventory())
                .toList();
    }

    // ── Scroll math ───────────────────────────────────────────────────────────

    private float getMaxScroll()           { return Math.max(0, lastRenderedInnerHeight - innerScrollPanelHeight); }
    private float clampScroll(float value) { return Mth.clamp(value, 0f, getMaxScroll()); }

    // ── Utility ───────────────────────────────────────────────────────────────

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