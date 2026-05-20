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
import org.lwjgl.glfw.GLFW;

public class StorageOverlayGui extends ContainerOverlay {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SLOT_SIZE     = 18;
    private static final int PAGE_WIDTH    = SLOT_SIZE * 9 + 4;
    private static final int PADDING       = 8;
    private static final int TOP_BAR_H     = 26;
    private static final int SCROLL_BAR_W  = 6;
    private static final int MIN_OVERLAY_H = 60;
    private static final int OVERVIEW_TOP  = SLOT_SIZE;
    private static final int INV_SLOTS_TOP = 18; // vertical space reserved for the "Inventory" label

    /**
     * Distance from the bottom of the screen to the bottom edge of the inventory section.
     * Increase to move the inventory section upward.
     */
    private static final int BOTTOM_PADDING = 20;

    // ── Colours — Dark Glass theme ────────────────────────────────────────────
    /** Main backdrop: deep blue-black, 94% opaque — the world peeks through slightly. */
    private static final int COL_OVERLAY_BG      = 0xF0080C18;
    /** Outer frame border: solid mid-dark navy. */
    private static final int COL_PANEL_BORDER     = 0xFF1A3060;
    /** Inactive page card: very dark navy, distinct from the backdrop. */
    private static final int COL_PAGE_BG          = 0xFF0C1020;
    /** Active page card: slightly lighter navy so the live page reads clearly. */
    private static final int COL_PAGE_BG_ACTIVE   = 0xFF0F1A36;
    /** Slot inner fill: dark inset blue-black — makes items pop. */
    private static final int COL_SLOT_ITEM        = 0xFF131828;
    /** Slot top/left bevel: near-black for the sunken effect. */
    private static final int COL_SLOT_TL          = 0xFF07090F;
    /** Slot bottom/right bevel: medium navy for a subtle raised edge. */
    private static final int COL_SLOT_BR          = 0xFF1E2840;
    /** Slot hover overlay: white wash (37% opaque). */
    private static final int COL_SLOT_HOVER       = 0x60FFFFFF;
    /** Active page title: bright cool blue. */
    private static final int COL_TITLE_ACTIVE     = 0xFF7AB4FF;
    /** Idle page title: muted blue-gray. */
    private static final int COL_TITLE_IDLE       = 0xFFA0AABB;
    /** "Not yet opened" placeholder: faded blue-gray. */
    private static final int COL_PLACEHOLDER      = 0xFF505868;

    // ── References ────────────────────────────────────────────────────────────
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerScreenAccessor accessor;
    private final StoragePageSlot activeSlot;
    private final Minecraft mc;

    /**
     * Vanilla's topPos at init time. All screen-space Y calculations anchor here.
     * Never mutated — we push slots via slot.y instead.
     */
    private int baseTopPos;

    /**
     * Captured slot.y values (relative to topPos) before any push, preserving
     * the vanilla hotbar gap between the main grid and hotbar rows.
     */
    private int[] originalPlayerSlotRelY;

    /** Pixels added to each player slot.y so vanilla renders items inside our panel. */
    private int playerPush;

    // ── Layout state ──────────────────────────────────────────────────────────
    private int pageWidthCount;
    private int overviewX, overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;
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
        this.screen     = screen;
        this.accessor   = (AbstractContainerScreenAccessor) screen;
        this.activeSlot = activeSlot;
        this.mc         = Minecraft.getInstance();
    }

    // ── ContainerOverlay ──────────────────────────────────────────────────────

    @Override
    public void onInit(int screenWidth, int screenHeight) {
        ensureAllSlotsRegistered();
        baseTopPos             = accessor.sbe$getTopPos();
        originalPlayerSlotRelY = capturePlayerSlotRelY();
        recalculateMeasurements();
        scroll = clampScroll(scroll);
        initSearchField();
    }

    /**
     * Repositions chest slots onto the overlay and pushes player slots into the
     * inventory section. Vanilla renders items at (leftPos+slot.x, topPos+slot.y),
     * so adjusting slot.y is sufficient — no topPos mutation required.
     */
    @Override
    public void preRender(int mouseX, int mouseY) {
        pushPlayerSlots();
        repositionChestSlots();
    }

    @Override
    public void render(GuiGraphics gfx, float delta, int mouseX, int mouseY) {
        drawPanel(gfx);
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
                new Rect(invPanelX - 1, invPanelY, invPanelW + 2, invPanelH + 1));
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
        if (searchField == null || !searchField.isFocused()) return false;
        if (searchField.keyPressed(event)) return true;
        // Consume all remaining keys while the field is focused so vanilla hotkeys
        // (e.g. the inventory key "E") don't fire. Escape is exempt so the screen
        // can still be closed normally.
        return event.key() != GLFW.GLFW_KEY_ESCAPE;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return searchField != null && searchField.isFocused() && searchField.charTyped(event);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void recalculateMeasurements() {
        pageWidthCount = Math.clamp(
                (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING),
                1, SkyblockEnhancementsConfig.storageOverlayColumns);

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth         = innerScrollPanelWidth + SCROLL_BAR_W + PADDING * 3;
        overviewX             = screen.width / 2 - overviewWidth / 2;

        invPanelX = accessor.sbe$getLeftPos();
        invPanelW = accessor.sbe$getImageWidth();

        int slotsH = originalPlayerSlotRelY[originalPlayerSlotRelY.length - 1]
                - originalPlayerSlotRelY[0] + SLOT_SIZE;
        invPanelH = INV_SLOTS_TOP + slotsH + 4;
        invPanelY = screen.height - BOTTOM_PADDING - invPanelH;

        overviewHeight         = Math.max(MIN_OVERLAY_H, invPanelY - OVERVIEW_TOP);
        innerScrollPanelHeight = overviewHeight - TOP_BAR_H - PADDING * 2;

        int targetFirstSlotScreenY  = invPanelY + INV_SLOTS_TOP;
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
        Rect panel      = getScrollPanel();
        Rect activeRect = findPageRect(activeSlot);
        int leftPos     = accessor.sbe$getLeftPos();

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

    /**
     * Draws a stepped/T-shaped panel: the top section spans the full overlay width,
     * then steps inward at invPanelY to match the inventory width below.
     * The bottom border of the top section doubles as the horizontal step cap,
     * so no separate divider fill is needed.
     */
    private void drawPanel(GuiGraphics gfx) {
        // Top section
        gfx.fill(overviewX,     OVERVIEW_TOP,     overviewX + overviewWidth,     invPanelY + 1, COL_PANEL_BORDER);
        gfx.fill(overviewX + 1, OVERVIEW_TOP + 1, overviewX + overviewWidth - 1, invPanelY,     COL_OVERLAY_BG);

        // Inventory section — left/right/bottom borders only; top is shared with the step above
        int iL = invPanelX - 1;
        int iR = invPanelX + invPanelW + 1;
        int iB = invPanelY + invPanelH + 1;
        gfx.fill(iL,     invPanelY, iR,     iB,     COL_PANEL_BORDER);
        gfx.fill(iL + 1, invPanelY, iR - 1, iB - 1, COL_OVERLAY_BG);
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
        int bgColor = isActive ? COL_PAGE_BG_ACTIVE : COL_PAGE_BG;
        gfx.fill(rect.x,     cardTop,     rect.x + PAGE_WIDTH,     rect.y + cardH,     borderColor);
        gfx.fill(rect.x + 1, cardTop + 1, rect.x + PAGE_WIDTH - 1, rect.y + cardH - 1, bgColor);

        String title = inv != null && inv.title() != null ? inv.title() : pageSlot.defaultName();
        gfx.drawString(mc.font, title, rect.x + 4, rect.y + 2,
                isActive ? COL_TITLE_ACTIVE : COL_TITLE_IDLE, true);

        if (inv == null || inv.inventory() == null) {
            gfx.drawCenteredString(mc.font, "Not yet opened",
                    rect.x + PAGE_WIDTH / 2, rect.y + cardH / 2, COL_PLACEHOLDER);
            return;
        }

        drawSlotBackgrounds(gfx, rect, rows, isActive);

        if (!isActive) {
            drawFakeItems(gfx, rect, inv.inventory().stacks(), rows, panel, mouseX, mouseY);
        }
    }

    /**
     * @param vanillaRendered true for the active page, where vanilla places items at (x, y).
     *                        false for cached pages, where we render fake items at (x+1, y+1).
     *                        The background must be offset so the inner fill aligns with where
     *                        items and hover highlights actually land.
     */
    private void drawSlotBackgrounds(GuiGraphics gfx, Rect pageRect, int rows, boolean vanillaRendered) {
        for (int i = 0; i < rows * 9; i++) {
            int x = pageRect.x + 2 + (i % 9) * SLOT_SIZE;
            int y = pageRect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_SIZE;
            drawSlotBackground(gfx, vanillaRendered ? x - 1 : x, vanillaRendered ? y - 1 : y);
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

    private void drawInventoryPanel(GuiGraphics gfx) {
        gfx.drawString(mc.font, Component.translatable("container.inventory"),
                invPanelX + PADDING, invPanelY + PADDING - 2, COL_TITLE_IDLE, false);

        // Vanilla places items at (leftPos + slot.x, topPos + slot.y), which is the 16x16 item
        // area. Pass (screenX - 1, screenY - 1) so the 1px border is drawn outside that area,
        // keeping the inner fill aligned with where items and the hover highlight land.
        int leftPos = accessor.sbe$getLeftPos();
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotRelY.length); i++) {
            Slot slot = playerSlots.get(i);
            int screenX = leftPos + slot.x;
            int screenY = baseTopPos + slot.y;
            drawSlotBackground(gfx, screenX - 1, screenY - 1);
        }
    }

    /**
     * Draws an 18×18 sunken slot with (x, y) as the outer top-left corner.
     * The inner 16×16 fill — where items and hover highlights land — starts at (x+1, y+1).
     */
    private void drawSlotBackground(GuiGraphics gfx, int x, int y) {
        gfx.fill(x,                 y,                 x + SLOT_SIZE,     y + 1,             COL_SLOT_TL);
        gfx.fill(x,                 y,                 x + 1,             y + SLOT_SIZE,     COL_SLOT_TL);
        gfx.fill(x,                 y + SLOT_SIZE - 1, x + SLOT_SIZE,     y + SLOT_SIZE,     COL_SLOT_BR);
        gfx.fill(x + SLOT_SIZE - 1, y,                 x + SLOT_SIZE,     y + SLOT_SIZE,     COL_SLOT_BR);
        gfx.fill(x + 1,             y + 1,             x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COL_SLOT_ITEM);
    }

    private void drawScrollbar(GuiGraphics gfx) {
        Rect track = getScrollbarTrack();
        gfx.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x30253560);

        float max = maxScroll();
        if (max <= 0) return;

        float ratio = (float) innerScrollPanelHeight / Math.max(lastRenderedContentH, 1);
        int knobH   = Math.max(20, (int) (track.height * ratio));
        int knobY   = track.y + (int) ((scroll / max) * (track.height - knobH));
        gfx.fill(track.x,     knobY,     track.x + track.width,     knobY + knobH,     0xC04878CC);
        gfx.fill(track.x + 1, knobY + 1, track.x + track.width - 1, knobY + knobH - 1, 0x80304E90);
    }

    // ── Hover guard ───────────────────────────────────────────────────────────

    /**
     * Guards fake-item hover so slots that have scrolled outside the viewport
     * don't match the mouse position in content-space.
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
            screen.setFocused(searchField);
            return true;
        }
        if (screen.getFocused() == searchField) {
            screen.setFocused(null);
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