package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayManager;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageTitleParser;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageType;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageDashboardComponent;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StoragePageGridComponent;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageSlotComponent;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Transforms Hypixel storage screens into a unified dashboard overlay.
 *
 * <p>Chest slots are hidden (render suppressed), player inventory remains vanilla,
 * and a custom UI-Lib component tree is drawn on top. Vanilla slot hit-testing is
 * redirected to the active page's mini-grid via {@link #isHovering(Slot, double, double)},
 * so pick, drag, release, double-click, and quick-craft all work natively.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class StorageScreenMixin extends Screen {

    @Shadow protected AbstractContainerMenu menu;
    @Shadow protected int topPos;
    @Shadow protected int leftPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected Slot hoveredSlot;
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int buttonNum, ClickType clickType);
    @Shadow private Slot getHoveredSlot(double x, double y) { throw new AssertionError(); }

    @Unique
    private boolean sbe$isStorageScreen = false;
    @Unique
    private StorageDashboardComponent sbe$dashboard;
    @Unique
    private StorageOverlayManager sbe$manager;
    @Unique
    private StorageTitleParser.ParsedTitle sbe$parsedTitle;
    @Unique
    private final List<StorageSlotComponent> sbe$hoverableSlots = new ArrayList<>();
    @Unique
    private String sbe$searchQuery = "";
    @Unique
    private EditBoxWidget sbe$searchBox;
    @Unique
    private ScrollContainerWidget sbe$scrollContainer;
    @Unique
    private static double sbe$preservedScrollAmount = 0.0;
    @Unique
    private static boolean sbe$navigatingBetweenPages = false;
    @Unique
    private int sbe$openContainerId = -1;
    @Unique
    private final Int2ObjectMap<StorageSlotComponent> sbe$activeSlotMap = new Int2ObjectOpenHashMap<>();

    @Unique
    private static final int DASHBOARD_PADDING = 6;
    @Unique
    private static final int MINI_SLOT_SIZE = 16;
    @Unique
    private static final int MINI_SLOT_GAP = 1;
    @Unique
    private static final int DASHBOARD_MARGIN_X = 20;
    @Unique
    private static final int DASHBOARD_MARGIN_Y = 10;

    protected StorageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$onInit(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard_CHANGEwhenRELASE) return;

        StorageOverlayManager manager = StorageFeature.getManager();
        if (manager == null) return;

        String rawTitle = this.title.getString();
        SkyblockEnhancements.LOGGER.info("[SBE Storage] Screen opened: '{}'", rawTitle);
        Optional<StorageTitleParser.ParsedTitle> parsed = manager.classifyTitle(rawTitle);
        if (parsed.isEmpty()) {
            SkyblockEnhancements.LOGGER.info("[SBE Storage] Title '{}' did not match any storage pattern.", rawTitle);
            return;
        }
        SkyblockEnhancements.LOGGER.info("[SBE Storage] Title matched: type={}, pageId={}, pageNumber={}",
                parsed.get().type(), parsed.get().pageId(), parsed.get().pageNumber());

        sbe$isStorageScreen = true;
        sbe$manager = manager;
        sbe$parsedTitle = parsed.get();
        sbe$openContainerId = this.menu.containerId;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            boolean isOverview = parsed.get().type() == StoragePageType.STORAGE
                    && "Storage".equalsIgnoreCase(parsed.get().rawTitle());
            if (!isOverview) {
                List<Slot> containerSlots = new ArrayList<>();
                for (Slot slot : this.menu.slots) {
                    if (slot.container != mc.player.getInventory()) {
                        containerSlots.add(slot);
                    }
                }
                manager.capturePage(parsed.get(), containerSlots, mc.level.registryAccess());
            }
        }

        sbe$buildDashboard();
        sbe$buildSearchBox();
        sbe$navigatingBetweenPages = false;
        sbe$rebuildActiveSlotMap();
    }

    @Unique
    private void sbe$buildDashboard() {
        int firstInvY = sbe$findFirstInventorySlotY();

        int dashboardY = DASHBOARD_MARGIN_Y;

        int gridWidth = 9 * MINI_SLOT_SIZE + (9 - 1) * MINI_SLOT_GAP
                + 2 * com.github.kd_gaming1.skyblockenhancements.gui.storage.StoragePageGridComponent.BORDER_THICKNESS;
        int maxPagesPerRow = StorageDashboardComponent.PAGES_PER_ROW;
        int dashboardX = DASHBOARD_MARGIN_X;
        int dashboardWidth = this.width - 2 * DASHBOARD_MARGIN_X;

        int pagesPerRow = Math.max(1,
                (dashboardWidth + StorageDashboardComponent.PAGE_GRID_GAP)
                        / (gridWidth + StorageDashboardComponent.PAGE_GRID_GAP));
        pagesPerRow = Math.min(pagesPerRow, maxPagesPerRow);

        int dashboardHeight = Math.max(60, firstInvY - dashboardY - DASHBOARD_PADDING);

        int scrollW = dashboardWidth;
        int scrollH = Math.max(60, dashboardHeight - StorageDashboardComponent.TOP_BAR_HEIGHT);

        double scrollToRestore = 0.0;
        if (sbe$navigatingBetweenPages) {
            scrollToRestore = sbe$preservedScrollAmount;
        } else if (sbe$scrollContainer != null) {
            scrollToRestore = sbe$scrollContainer.scrollAmount();
        }

        sbe$scrollContainer = new ScrollContainerWidget(scrollW, scrollH, 8);
        sbe$scrollContainer.setX(dashboardX);
        sbe$scrollContainer.setY(dashboardY + StorageDashboardComponent.TOP_BAR_HEIGHT);

        sbe$dashboard = new StorageDashboardComponent(
                dashboardX, dashboardY,
                dashboardWidth, dashboardHeight,
                sbe$manager, sbe$parsedTitle.pageId(),
                MINI_SLOT_SIZE, MINI_SLOT_GAP,
                sbe$searchQuery,
                sbe$hoverableSlots,
                sbe$scrollContainer,
                pagesPerRow);

        sbe$scrollContainer.setScrollAmount(scrollToRestore);
        sbe$dashboard.updateParentPosition(0, 0, this.width, this.height);
    }

    @Unique
    private void sbe$buildSearchBox() {
        Minecraft mc = Minecraft.getInstance();
        int sbX = sbe$dashboard.getTotalX() + 10;
        int sbY = sbe$dashboard.getTotalY() + 6;
        int sbW = Math.max(100, sbe$dashboard.getWidth() - 180);

        sbe$searchBox = new EditBoxWidget(
                mc.font, sbX, sbY, sbW, 16,
                Component.literal("Search items..."));
        sbe$searchBox.setMaxLength(64);
        sbe$searchBox.setResponder(text -> {
            sbe$searchQuery = text != null ? text : "";
            if (sbe$dashboard != null) {
                sbe$dashboard.setSearchQuery(sbe$searchQuery);
            }
        });
        addRenderableWidget(sbe$searchBox);
    }

    @Unique
    private int sbe$findFirstInventorySlotY() {
        Minecraft mc = Minecraft.getInstance();
        int minY = Integer.MAX_VALUE;
        for (Slot slot : this.menu.slots) {
            if (slot.container == mc.player.getInventory()) {
                minY = Math.min(minY, slot.y);
            }
        }
        return minY == Integer.MAX_VALUE ? this.topPos + this.imageHeight - 96 : this.topPos + minY;
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void sbe$suppressChestSlots(GuiGraphics guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
        if (!sbe$isStorageScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (slot.container != mc.player.getInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void sbe$suppressVanillaContainerTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (!sbe$isStorageScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (hoveredSlot != null && hoveredSlot.container != mc.player.getInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sbe$preRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            sbe$manager.resolveAllStacks(mc.level.registryAccess());
        }

        sbe$dashboard.updateIfDirty();
        sbe$dashboard.updateParentPosition(0, 0, this.width, this.height);
        sbe$dashboard.refreshActiveGrid(idx -> {
            Slot slot = this.menu.getSlot(idx);
            return slot != null ? slot.getItem() : ItemStack.EMPTY;
        });
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderDashboard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        sbe$dashboard.renderBase(graphics, mouseX, mouseY, partialTick, 0, 0);

        // Post-render coordinate sync so parent positions match the scrolled layout
        // for next frame's input handling.
        sbe$dashboard.updateParentPosition(0, 0, this.width, this.height);
        sbe$rebuildActiveSlotMap();

        // Re-render carried item on top so it isn't covered by the dashboard panel
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(Minecraft.getInstance().font, carried, mouseX - 8, mouseY - 8);
        }

        StorageSlotComponent hovered = sbe$findHoveredSlot(mouseX, mouseY);
        if (hovered != null && !hovered.getStack().isEmpty()) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, hovered.getStack(), mouseX, mouseY);
        }

        sbe$renderHoverHighlight(graphics, mouseX, mouseY);
    }

    /**
     * Redirects vanilla slot hit-testing to the active mini-grid coordinates.
     * This makes pick, drag, release, double-click and quick-craft work natively
     * on the mini-grid without manual event emulation.
     */
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onIsHoveringSlot(Slot slot, double mx, double my,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (slot.container == mc.player.getInventory()) return;

        StorageSlotComponent slotComp = sbe$activeSlotMap.get(slot.index);
        if (slotComp != null) {
            int absX = slotComp.getTotalX();
            int absY = slotComp.getTotalY();
            int size = slotComp.getWidth();
            // Vanilla uses an 18x18 hit box (-1 .. +17) around the 16x16 slot
            cir.setReturnValue(
                    mx >= absX - 1 && mx < absX + size + 1
                            && my >= absY - 1 && my < absY + size + 1);
        } else {
            // Non-active chest slots are moved off-screen conceptually
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        double mouseX = event.x();
        double mouseY = event.y();

        // 1. Let vanilla children (search box etc.) handle the click
        for (var child : this.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                return;
            }
        }

        // 2. If clicking an active mini-grid slot, let vanilla handle it
        Slot hovered = this.getHoveredSlot(mouseX, mouseY);
        if (hovered != null && hovered.container != Minecraft.getInstance().player.getInventory()) {
            return;
        }

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        boolean overScroll = scroll != null && scroll.isMouseOver(mouseX, mouseY);

        // 3. Scroll container scrollbar click
        if (overScroll) {
            if (scroll.mouseClicked(event, false)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 4. Inactive grid navigation
        if (overScroll) {
            StoragePageGridComponent clickedGrid = sbe$dashboard.findGridAt((int) mouseX, (int) mouseY);
            if (clickedGrid != null && !clickedGrid.isActive()) {
                sbe$sendNavigationCommand(clickedGrid);
                cir.setReturnValue(true);
                return;
            }
        }

        // 4. Block interaction with the dashboard background to prevent clicks
        //    falling through to hidden vanilla slots.
        if (sbe$isOverDashboardBackground(mouseX, mouseY)) {
            if (this.isDragging()) {
                this.setDragging(false);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseDragged(MouseButtonEvent event, double dragX, double dragY,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll != null && scroll.isMouseOver(event.x(), event.y())) {
            if (scroll.mouseDragged(event, dragX, dragY)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // Let vanilla handle drags over active mini-grid slots
        Slot hovered = this.getHoveredSlot(event.x(), event.y());
        if (hovered != null && hovered.container != Minecraft.getInstance().player.getInventory()) {
            return;
        }

        if (sbe$isOverDashboardBackground(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll != null) {
            if (scroll.mouseReleased(event)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // Let vanilla handle releases over active mini-grid slots or inventory
        Slot hovered = this.getHoveredSlot(event.x(), event.y());
        if (hovered != null && hovered.container != Minecraft.getInstance().player.getInventory()) {
            return;
        }

        if (sbe$isOverDashboardBackground(event.x(), event.y())) {
            if (!this.menu.getCarried().isEmpty()) {
                this.slotClicked(null, -999, event.button(), ClickType.PICKUP);
            }
            if (this.isDragging()) {
                this.setDragging(false);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll != null && scroll.isMouseOver(mouseX, mouseY)) {
            if (scroll.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$onRemoved(CallbackInfo ci) {
        if (!sbe$isStorageScreen) return;
        if (sbe$searchBox != null) {
            removeWidget(sbe$searchBox);
        }
        if (!sbe$navigatingBetweenPages) {
            sbe$preservedScrollAmount = 0.0;
        }
        StorageFeature.save();
    }

    @Unique
    private void sbe$rebuildActiveSlotMap() {
        sbe$activeSlotMap.clear();
        StoragePageGridComponent active = sbe$dashboard.findActiveGrid();
        if (active != null) {
            for (StorageSlotComponent slotComp : active.getSlotComponents()) {
                sbe$activeSlotMap.put(slotComp.getSlotIndex(), slotComp);
            }
        }
    }

    @Unique
    private boolean sbe$isOverDashboardBackground(double mouseX, double mouseY) {
        int dx = sbe$dashboard.getTotalX();
        int dy = sbe$dashboard.getTotalY();
        int dw = sbe$dashboard.getWidth();
        int dh = sbe$dashboard.getHeight();
        return mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh;
    }

    @Unique
    private StorageSlotComponent sbe$findHoveredSlot(int mouseX, int mouseY) {
        for (StorageSlotComponent slot : sbe$hoverableSlots) {
            int tx = slot.getTotalX();
            int ty = slot.getTotalY();
            if (mouseX >= tx && mouseX < tx + slot.getWidth()
                    && mouseY >= ty && mouseY < ty + slot.getHeight()) {
                return slot;
            }
        }
        return null;
    }

    @Unique
    private void sbe$renderHoverHighlight(GuiGraphics graphics, int mouseX, int mouseY) {
        Slot hovered = this.getHoveredSlot(mouseX, mouseY);
        if (hovered == null || hovered.container == Minecraft.getInstance().player.getInventory()) return;

        StorageSlotComponent slotComp = sbe$activeSlotMap.get(hovered.index);
        if (slotComp == null) return;

        int x = slotComp.getTotalX();
        int y = slotComp.getTotalY();
        int size = slotComp.getWidth();

        // 1px white border around the hovered slot
        graphics.fill(x - 1, y - 1, x + size + 1, y, 0xFFFFFFFF);
        graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFFFFFFFF);
        graphics.fill(x - 1, y, x, y + size, 0xFFFFFFFF);
        graphics.fill(x + size, y, x + size + 1, y + size, 0xFFFFFFFF);
    }

    @Unique
    private void sbe$sendNavigationCommand(StoragePageGridComponent grid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        sbe$navigatingBetweenPages = true;
        sbe$preservedScrollAmount = sbe$scrollContainer != null ? sbe$scrollContainer.scrollAmount() : 0.0;

        var snap = grid.getSnapshot();
        String cmd;
        switch (snap.type) {
            case ENDER_CHEST -> cmd = "ec " + snap.pageNumber;
            case STORAGE -> cmd = "storage";
            case BACKPACK -> cmd = "backpack " + snap.pageNumber;
            default -> {
                return;
            }
        }
        mc.getConnection().sendCommand(cmd);
    }
}
