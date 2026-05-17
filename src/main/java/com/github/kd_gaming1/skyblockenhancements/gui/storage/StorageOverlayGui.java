package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageData;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageSlot;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractContainerScreenAccessor;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.SlotAccessor;
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
    private static final int SLOT_SIZE     = 18;
    private static final int PAGE_WIDTH    = SLOT_SIZE * 9 + 4;   // 2 px side padding
    private static final int PADDING       = 8;
    private static final int TOP_BAR_H     = 26;
    private static final int SCROLL_BAR_W  = 6;
    private static final int MIN_OVERLAY_H = 60;

    /** Gap from screen top to overlay top. */
    private static final int OVERVIEW_TOP  = SLOT_SIZE;            // 1 slot

    /** Gap between overlay bottom and inventory panel top. */
    private static final int INVENTORY_GAP = SLOT_SIZE * 5 / 2;   // 2.5 slots = 45 px

    /** Pixels from inventory panel top to the first slot row. Accommodates the label. */
    private static final int INV_SLOTS_TOP = 18;

    /** Padding below the last slot row inside the inventory panel. */
    private static final int BOTTOM_PADDING = 6;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int COL_OVERLAY_BG   = 0xD4080810;
    private static final int COL_PANEL_BORDER = 0xFF2E2E4A;
    private static final int COL_PAGE_BG      = 0xFF141422;
    private static final int COL_SLOT_EMPTY   = 0xFF1C1C2C;
    private static final int COL_SLOT_ITEM    = 0xFF22223A;
    private static final int COL_SLOT_TL      = 0xFF0F0F1E;
    private static final int COL_SLOT_BR      = 0xFF3A3A5C;
    private static final int COL_SLOT_HOVER   = 0x60FFFFFF;
    private static final int COL_TITLE_ACTIVE = 0xFFFFD700;
    private static final int COL_TITLE_IDLE   = 0xFFAAAAAA;
    private static final int COL_PLACEHOLDER  = 0xFF55556A;

    // ── References ────────────────────────────────────────────────────────────
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerScreenAccessor accessor;
    private final StoragePageSlot activeSlot;
    private final Minecraft mc;

    /**
     * Vanilla's topPos, captured once per init. Never mutated.
     * All screen-space Y calculations anchor to this value.
     */
    private int baseTopPos;

    /**
     * Original slot.y values (relative to topPos) for all 36 player slots,
     * captured before any push is applied. Preserves the vanilla hotbar gap.
     */
    private int[] originalPlayerSlotRelY;

    /**
     * Pixels added to each player slot's Y so items land inside our custom panel.
     * Because vanilla renders items at (leftPos + slot.x, topPos + slot.y), this
     * shift is the only thing needed — no topPos mutation.
     */
    private int playerPush;

    // ── Layout state ──────────────────────────────────────────────────────────
    private int pageWidthCount;
    private int overviewX, overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;

    /** Position and size of our custom inventory replacement panel. */
    private int invPanelX, invPanelY, invPanelW, invPanelH;

    // ── Scroll state ──────────────────────────────────────────────────────────
    private float scroll;
    private int lastRenderedContentH;
    private boolean knobGrabbed;

    // ── Search state ──────────────────────────────────────────────────────────
    private EditBox searchField;
    private String searchQuery = "";
    private String cachedSearchQuery;
    private Set<StoragePageSlot> cachedFilteredPages = Set.of();

    // ─────────────────────────────────────────────────────────────────────────

    public StorageOverlayGui(AbstractContainerScreen<?> screen, StoragePageSlot activeSlot) {
        this.screen   = screen;
        this.accessor = (AbstractContainerScreenAccessor) screen;
        this.activeSlot = activeSlot;
        this.mc       = Minecraft.getInstance();
    }

    // ── ContainerOverlay ──────────────────────────────────────────────────────

    @Override
    public void onInit(int screenWidth, int screenHeight) {
        ensureAllSlotsRegistered();
        baseTopPos            = accessor.sbe$getTopPos();
        originalPlayerSlotRelY = capturePlayerSlotRelY();
        recalculateMeasurements();
        scroll = clampScroll(scroll);
        initSearchField();
    }

    /**
     * Repositions chest slots onto the overlay and pushes player slots into our
     * custom inventory panel. Vanilla renders all items at (leftPos+slot.x, topPos+slot.y),
     * so adjusting slot.y is sufficient — no topPos mutation required.
     */
    @Override
    public void preRender(int mouseX, int mouseY) {
        pushPlayerSlots();
        repositionChestSlots();
    }

    @Override
    public void render(GuiGraphics gfx, float delta, int mouseX, int mouseY) {
        drawOverlayPanel(gfx);
        drawScrollableContent(gfx, mouseX, mouseY);
        drawScrollbar(gfx);
        drawInventoryPanel(gfx);
        if (searchField != null) searchField.render(gfx, mouseX, mouseY, delta);
    }

    @Override public boolean shouldDrawForeground() { return false; }

    @Override
    public List<Rect> getBounds() {
        return List.of(
                new Rect(overviewX, OVERVIEW_TOP, overviewWidth, overviewHeight),
                new Rect(invPanelX, invPanelY, invPanelW, invPanelH));
    }

    @Override public EditBox getSearchField() { return searchField; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (handleSearchFieldClick(event, doubleClick)) return true;
        if (handleScrollbarClick(event)) return true;
        return handlePageClick(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (knobGrabbed) { knobGrabbed = false; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!knobGrabbed) return false;
        scroll = scrollForKnobY(event.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!getScrollPanel().contains(x, y)) return false;
        float dir = SkyblockEnhancementsConfig.storageInverseScroll ? 1f : -1f;
        scroll = clampScroll(scroll + (float) (scrollY * SkyblockEnhancementsConfig.storageScrollSpeed * dir));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return searchField != null && searchField.isFocused() && searchField.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return searchField != null && searchField.isFocused() && searchField.charTyped(event);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void recalculateMeasurements() {
        // ── Page overlay ──────────────────────────────────────────────────────
        pageWidthCount = Math.clamp(
                (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING),
                1, SkyblockEnhancementsConfig.storageOverlayColumns);

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth = innerScrollPanelWidth + SCROLL_BAR_W + PADDING * 3;
        overviewX     = screen.width / 2 - overviewWidth / 2;

        // ── Inventory panel ───────────────────────────────────────────────────
        // Height: label section + all slot rows (preserving vanilla hotbar gap) + bottom padding.
        int slotsH = originalPlayerSlotRelY[originalPlayerSlotRelY.length - 1]
                - originalPlayerSlotRelY[0] + SLOT_SIZE;
        invPanelH = INV_SLOTS_TOP + slotsH + BOTTOM_PADDING;
        invPanelX = accessor.sbe$getLeftPos();
        invPanelW = accessor.sbe$getImageWidth();
        invPanelY = screen.height - BOTTOM_PADDING - invPanelH;

        // ── Overlay fills space between screen top and inventory panel ─────────
        overviewHeight         = Math.max(MIN_OVERLAY_H, invPanelY - OVERVIEW_TOP - INVENTORY_GAP);
        innerScrollPanelHeight = overviewHeight - TOP_BAR_H - PADDING * 2;

        // ── Player push: first slot must land at invPanelY + INV_SLOTS_TOP ────
        int targetFirstSlotScreenY = invPanelY + INV_SLOTS_TOP;
        int vanillaFirstSlotScreenY = baseTopPos + originalPlayerSlotRelY[0];
        playerPush = targetFirstSlotScreenY - vanillaFirstSlotScreenY;
    }

    private Rect getScrollPanel() {
        return new Rect(overviewX + PADDING, OVERVIEW_TOP + TOP_BAR_H,
                innerScrollPanelWidth, innerScrollPanelHeight);
    }

    private Rect getScrollbarTrack() {
        return new Rect(overviewX + overviewWidth - PADDING - SCROLL_BAR_W,
                OVERVIEW_TOP + TOP_BAR_H, SCROLL_BAR_W, innerScrollPanelHeight);
    }

    // ── Slot repositioning ────────────────────────────────────────────────────

    private void pushPlayerSlots() {
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotRelY.length); i++) {
            ((SlotAccessor) playerSlots.get(i)).setY(originalPlayerSlotRelY[i] + playerPush);
        }
    }

    private void repositionChestSlots() {
        Rect panel        = getScrollPanel();
        Rect activeRect   = findPageRect(activeSlot);
        int leftPos       = accessor.sbe$getLeftPos();

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;

            if (activeSlot == null || activeRect == null) {
                hideSlot(slot);
                continue;
            }

            int screenX = activeRect.x + 2 + (slot.index % 9) * SLOT_SIZE;
            int screenY = activeRect.y + mc.font.lineHeight + 6 + (slot.index / 9) * SLOT_SIZE - (int) scroll;
            boolean inPanel = screenY >= panel.y && screenY + SLOT_SIZE <= panel.y + panel.height;

            if (inPanel) {
                ((SlotAccessor) slot).setX(screenX - leftPos);
                ((SlotAccessor) slot).setY(screenY - baseTopPos);
            } else {
                hideSlot(slot);
            }
        }
    }

    private static void hideSlot(Slot slot) {
        ((SlotAccessor) slot).setX(-9999);
        ((SlotAccessor) slot).setY(-9999);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void drawOverlayPanel(GuiGraphics gfx) {
        int bottom = OVERVIEW_TOP + overviewHeight;
        gfx.fill(overviewX,     OVERVIEW_TOP,     overviewX + overviewWidth,     bottom,     COL_PANEL_BORDER);
        gfx.fill(overviewX + 1, OVERVIEW_TOP + 1, overviewX + overviewWidth - 1, bottom - 1, COL_OVERLAY_BG);
    }

    private void drawScrollableContent(GuiGraphics gfx, int mouseX, int mouseY) {
        Rect panel = getScrollPanel();
        gfx.enableScissor(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0f, -scroll);

        forEachPage(getFilteredPages(), (rect, slot, inv) ->
                drawPage(gfx, rect, slot, inv, slot.equals(activeSlot), mouseX, mouseY, panel));

        gfx.pose().popMatrix();
        gfx.disableScissor();
    }

    private void drawPage(GuiGraphics gfx, Rect rect, StoragePageSlot pageSlot,
                          StorageData.StorageInventory inv, boolean isActive,
                          int mouseX, int mouseY, Rect panel) {
        int rows    = pageRows(pageSlot, inv, isActive);
        int cardH   = rows * SLOT_SIZE + mc.font.lineHeight + 10;
        int cardTop = rect.y + mc.font.lineHeight + 4;

        int borderColor = parseColor(isActive
                ? SkyblockEnhancementsConfig.storageActivePageOutlineColor
                : SkyblockEnhancementsConfig.storageInactivePageBorderColor);
        gfx.fill(rect.x, cardTop, rect.x + PAGE_WIDTH, rect.y + cardH, borderColor);
        gfx.fill(rect.x + 1, cardTop + 1, rect.x + PAGE_WIDTH - 1, rect.y + cardH - 1, COL_PAGE_BG);

        String title = inv != null && inv.title() != null ? inv.title() : pageSlot.defaultName();
        gfx.drawString(mc.font, title, rect.x + 4, rect.y + 2,
                isActive ? COL_TITLE_ACTIVE : COL_TITLE_IDLE, true);

        if (inv == null || inv.inventory() == null) {
            gfx.drawCenteredString(mc.font, "Not yet opened",
                    rect.x + PAGE_WIDTH / 2, rect.y + cardH / 2, COL_PLACEHOLDER);
            return;
        }

        // Slot backgrounds are always drawn — they provide the grid even for the active page.
        drawSlotBackgrounds(gfx, rect, rows);

        // Fake items only for cached pages; the active page is rendered by vanilla.
        if (!isActive) {
            drawFakeItems(gfx, rect, inv.inventory().stacks(), rows, panel, mouseX, mouseY);
        }
    }

    private void drawSlotBackgrounds(GuiGraphics gfx, Rect pageRect, int rows) {
        for (int i = 0; i < rows * 9; i++) {
            int x = pageRect.x + 2 + (i % 9) * SLOT_SIZE;
            int y = pageRect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_SIZE;
            drawSlotBackground(gfx, x, y);
        }
    }

    private void drawFakeItems(GuiGraphics gfx, Rect pageRect, List<ItemStack> stacks,
                               int rows, Rect panel, int mouseX, int mouseY) {
        int count         = Math.min(stacks.size(), rows * 9);
        int contentMouseY = mouseY + (int) scroll;

        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            int slotX = pageRect.x + 2 + (i % 9) * SLOT_SIZE;
            int slotY = pageRect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_SIZE;

            gfx.renderFakeItem(stack, slotX + 1, slotY + 1);
            gfx.renderItemDecorations(mc.font, stack, slotX + 1, slotY + 1);

            if (!searchQuery.isBlank() && matchesSearch(stack, searchQuery)) {
                gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        parseColor(SkyblockEnhancementsConfig.storageSearchHighlightColor));
            }

            if (isSlotHovered(slotX, slotY, mouseX, contentMouseY, panel)) {
                gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, COL_SLOT_HOVER);
                gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
            }
        }
    }

    /**
     * Draws the custom inventory panel that replaces vanilla's player inventory background.
     * Slot backgrounds are drawn here; vanilla renders the actual items on top via slot positions.
     */
    private void drawInventoryPanel(GuiGraphics gfx) {
        // Panel background
        gfx.fill(invPanelX,     invPanelY,     invPanelX + invPanelW,     invPanelY + invPanelH,     COL_PANEL_BORDER);
        gfx.fill(invPanelX + 1, invPanelY + 1, invPanelX + invPanelW - 1, invPanelY + invPanelH - 1, COL_OVERLAY_BG);

        // "Inventory" label
        gfx.drawString(mc.font, Component.translatable("container.inventory"),
                invPanelX + PADDING, invPanelY + PADDING - 2, COL_TITLE_IDLE, false);

        // Slot backgrounds — drawn at the same screen positions vanilla will place items.
        int leftPos = accessor.sbe$getLeftPos();
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotRelY.length); i++) {
            Slot slot = playerSlots.get(i);
            int screenX = leftPos + slot.x;
            int screenY = baseTopPos + slot.y;   // slot.y already pushed in preRender
            drawSlotBackground(gfx, screenX, screenY);
        }
    }

    /** Vanilla-style sunken slot: dark top/left edges, lighter bottom/right edges. */
    private void drawSlotBackground(GuiGraphics gfx, int x, int y) {
        gfx.fill(x,                y,                 x + SLOT_SIZE, y + 1,            COL_SLOT_TL);
        gfx.fill(x,                y,                 x + 1,         y + SLOT_SIZE,    COL_SLOT_TL);
        gfx.fill(x,                y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE,    COL_SLOT_BR);
        gfx.fill(x + SLOT_SIZE - 1, y,                x + SLOT_SIZE, y + SLOT_SIZE,    COL_SLOT_BR);
        gfx.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COL_SLOT_ITEM);
    }

    private void drawScrollbar(GuiGraphics gfx) {
        Rect track = getScrollbarTrack();
        gfx.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x30FFFFFF);

        float max = maxScroll();
        if (max <= 0) return;

        float ratio = (float) innerScrollPanelHeight / Math.max(lastRenderedContentH, 1);
        int knobH   = Math.max(20, (int) (track.height * ratio));
        int knobY   = track.y + (int) ((scroll / max) * (track.height - knobH));
        gfx.fill(track.x,     knobY,     track.x + track.width,     knobY + knobH,     0xC0FFFFFF);
        gfx.fill(track.x + 1, knobY + 1, track.x + track.width - 1, knobY + knobH - 1, 0x80AAAAAA);
    }

    // ── Hover guard ───────────────────────────────────────────────────────────

    /**
     * Only hovers/tooltips a slot when it is actually visible inside the scissor viewport.
     * Without this, slots scrolled above the panel still match the mouse Y in content-space.
     */
    private boolean isSlotHovered(int slotX, int slotY, int mouseX, int contentMouseY, Rect panel) {
        int screenSlotY = slotY - (int) scroll;
        boolean inViewport = screenSlotY >= panel.y
                && screenSlotY + SLOT_SIZE <= panel.y + panel.height
                && slotX >= panel.x
                && slotX + SLOT_SIZE <= panel.x + panel.width;
        return inViewport
                && mouseX        >= slotX && mouseX        < slotX + SLOT_SIZE
                && contentMouseY >= slotY && contentMouseY < slotY + SLOT_SIZE;
    }

    // ── Page iteration ────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface PageConsumer {
        void accept(Rect rect, StoragePageSlot slot, StorageData.StorageInventory inv);
    }

    private void forEachPage(Set<StoragePageSlot> filter, PageConsumer consumer) {
        int col = 0, maxRowH = 0, totalH = 0;
        for (var entry : StorageData.INSTANCE.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;
            StorageData.StorageInventory inv = entry.getValue();

            int pageH = pageRows(entry.getKey(), inv, entry.getKey().equals(activeSlot)) * SLOT_SIZE
                    + mc.font.lineHeight + 10;
            maxRowH = Math.max(maxRowH, pageH);

            consumer.accept(
                    new Rect(overviewX + PADDING + (PAGE_WIDTH + PADDING) * col,
                            OVERVIEW_TOP + TOP_BAR_H + totalH, PAGE_WIDTH, pageH),
                    entry.getKey(), inv);

            if (++col >= pageWidthCount) {
                totalH += maxRowH + PADDING;
                col = 0;
                maxRowH = 0;
            }
        }
        lastRenderedContentH = totalH + maxRowH;
    }

    private int pageRows(StoragePageSlot slot, StorageData.StorageInventory inv, boolean isActive) {
        if (isActive) return Math.max(1, (screen.getMenu().slots.size() - 36) / 9);
        if (inv != null && inv.inventory() != null) return inv.inventory().rows();
        return 1;
    }

    // ── Hit-testing ───────────────────────────────────────────────────────────

    private Rect findPageRect(StoragePageSlot target) {
        if (target == null) return null;
        Rect[] result = {null};
        forEachPage(getFilteredPages(), (rect, slot, inv) -> {
            if (result[0] == null && slot.equals(target)) result[0] = rect;
        });
        return result[0];
    }

    private StoragePageSlot pageAt(int screenX, int screenY) {
        StoragePageSlot[] result = {null};
        forEachPage(getFilteredPages(), (rect, slot, inv) -> {
            if (result[0] == null && rect.contains(screenX, screenY + (int) scroll)) result[0] = slot;
        });
        return result[0];
    }

    // ── Input handlers ────────────────────────────────────────────────────────

    private boolean handleSearchFieldClick(MouseButtonEvent event, boolean doubleClick) {
        if (searchField == null) return false;
        if (searchField.mouseClicked(event, doubleClick)) {
            searchField.setFocused(true);
            return true;
        }
        searchField.setFocused(false);
        return false;
    }

    private boolean handleScrollbarClick(MouseButtonEvent event) {
        if (!getScrollbarTrack().contains(event.x(), event.y())) return false;
        knobGrabbed = true;
        scroll = scrollForKnobY(event.y());
        return true;
    }

    private boolean handlePageClick(MouseButtonEvent event) {
        if (!getScrollPanel().contains(event.x(), event.y())) return false;
        StoragePageSlot clicked = pageAt((int) event.x(), (int) event.y());
        if (clicked != null && !clicked.equals(activeSlot)) {
            clicked.navigateTo();
            return true;
        }
        return false;
    }

    private float scrollForKnobY(double mouseY) {
        Rect track = getScrollbarTrack();
        return clampScroll((float) ((mouseY - track.y) / track.height) * maxScroll());
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void initSearchField() {
        if (searchField == null) {
            searchField = new EditBox(mc.font, 0, 0, 140, 16, Component.literal("Search items..."));
            searchField.setMaxLength(64);
            searchField.setResponder(this::onSearchChanged);
            searchField.setBordered(true);
        }
        searchField.setX(overviewX + PADDING);
        searchField.setY(OVERVIEW_TOP + 5);
        searchField.setWidth(Math.max(80, overviewWidth - SCROLL_BAR_W - PADDING * 4));
    }

    private void onSearchChanged(String query) {
        searchQuery       = query != null ? query : "";
        cachedSearchQuery = null;
        scroll = 0f;
    }

    private Set<StoragePageSlot> getFilteredPages() {
        if (searchQuery.isBlank()) return StorageData.INSTANCE.getInventories().keySet();
        if (searchQuery.equals(cachedSearchQuery)) return cachedFilteredPages;

        cachedFilteredPages = StorageData.INSTANCE.getInventories().entrySet().stream()
                .filter(e -> e.getValue() == null
                        || e.getValue().inventory() == null
                        || e.getValue().inventory().stacks().stream()
                        .anyMatch(s -> matchesSearch(s, searchQuery)))
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        cachedSearchQuery = searchQuery;
        return cachedFilteredPages;
    }

    private boolean matchesSearch(ItemStack stack, String query) {
        if (stack.isEmpty()) return false;
        Set<String> words = new TreeSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        words.removeIf(stack.getHoverName().getString().toLowerCase()::contains);
        if (words.isEmpty()) return true;
        for (Component line : stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                mc.player, TooltipFlag.Default.NORMAL)) {
            words.removeIf(line.getString().toLowerCase()::contains);
            if (words.isEmpty()) return true;
        }
        return false;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void ensureAllSlotsRegistered() {
        for (int i = 0; i < StoragePageSlot.COUNT; i++) {
            StoragePageSlot s = new StoragePageSlot(i);
            if (!StorageData.INSTANCE.hasInventory(s)) {
                StorageData.INSTANCE.updateInventory(s, s.defaultName(), null);
            }
        }
    }

    private int[] capturePlayerSlotRelY() {
        List<Slot> slots = getPlayerSlots();
        int[] relY = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) relY[i] = slots.get(i).y;
        return relY;
    }

    private List<Slot> getPlayerSlots() {
        return screen.getMenu().slots.stream()
                .filter(s -> s.container == mc.player.getInventory())
                .toList();
    }

    private float maxScroll()          { return Math.max(0f, lastRenderedContentH - innerScrollPanelHeight); }
    private float clampScroll(float v) { return Mth.clamp(v, 0f, maxScroll()); }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
            if (hex.length() == 8) { int v = (int) Long.parseLong(hex, 16); return (v >> 8) | ((v & 0xFF) << 24); }
        } catch (NumberFormatException ignored) {}
        return 0xFFFFFFFF;
    }
}