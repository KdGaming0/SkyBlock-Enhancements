package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayManager;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageTitleParser;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageType;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageDashboardComponent;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StoragePageGridComponent;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageSlotComponent;
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
 * and a custom UI-Lib component tree is drawn on top. Clicks inside the active
 * page's mini grid are translated back to vanilla slot indices so the server
 * handles item movement.
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
    private static final int DASHBOARD_PADDING = 6;
    @Unique
    private static final int MINI_SLOT_SIZE = 16;
    @Unique
    private static final int MINI_SLOT_GAP = 1;
    @Unique
    private static final int DASHBOARD_MARGIN_X = 20;
    @Unique
    private static final int DASHBOARD_MARGIN_Y = 10;
    // Raw coords are kept for fallback; overlay now anchors to leftPos/topPos.

    protected StorageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$onInit(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard) return;

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
            // Skip capturing the /storage overview screen — it holds selector buttons, not real items.
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
    }

    @Unique
    private void sbe$buildDashboard() {
        int firstInvY = sbe$findFirstInventorySlotY();

        int dashboardY = DASHBOARD_MARGIN_Y;

        // Give the dashboard the full available horizontal space and let the scroll
        // container handle overflow.
        int gridWidth = 9 * MINI_SLOT_SIZE + (9 - 1) * MINI_SLOT_GAP
                + 2 * StoragePageGridComponent.BORDER_THICKNESS;
        int maxPagesPerRow = StorageDashboardComponent.PAGES_PER_ROW;
        int dashboardX = DASHBOARD_MARGIN_X;
        int dashboardWidth = this.width - 2 * DASHBOARD_MARGIN_X;

        int pagesPerRow = Math.max(1,
                (dashboardWidth + StorageDashboardComponent.PAGE_GRID_GAP)
                        / (gridWidth + StorageDashboardComponent.PAGE_GRID_GAP));
        pagesPerRow = Math.min(pagesPerRow, maxPagesPerRow);

        // Height: use ALL space above the player inventory.
        int dashboardHeight = Math.max(60, firstInvY - dashboardY - DASHBOARD_PADDING);

        int scrollW = dashboardWidth;
        int scrollH = Math.max(60, dashboardHeight - StorageDashboardComponent.TOP_BAR_HEIGHT);

        // Save the current scroll position BEFORE recreating the scroll container.
        // When navigating between pages, use the preserved scroll; otherwise keep
        // the previous scroll (or 0 if this is the first build).
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

        // Immediately restore the scroll position. It now has content so the max bounds are computed.
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
        // slot.y is a local texture coordinate; convert to screen space
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

    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderDashboard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
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
        sbe$dashboard.renderBase(graphics, mouseX, mouseY, partialTick, 0, 0);

        // Explicit render required — addWidget does NOT auto-render inside renderBase
        if (sbe$scrollContainer != null) {
            sbe$scrollContainer.render(graphics, mouseX, mouseY, partialTick);
        }

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
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        double mouseX = event.x();
        double mouseY = event.y();
        for (var child : this.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                return;
            }
        }

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll != null && scroll.isMouseOver(mouseX, mouseY)) {
            if (scroll.mouseClicked(event, false)) {
                cir.setReturnValue(true);
                return;
            }
        }

        int scrollOffset = sbe$dashboard.getScrollOffset();
        if (sbe$dashboard.isInsideActiveGrid((int) mouseX, (int) mouseY, scrollOffset)) {
            int slotIndex = sbe$dashboard.translateActiveGridClick((int) mouseX, (int) mouseY, scrollOffset);
            if (slotIndex >= 0) {
                Slot vanillaSlot = this.menu.getSlot(slotIndex);
                if (vanillaSlot != null && this.menu.containerId == sbe$openContainerId) {
                    this.slotClicked(vanillaSlot, vanillaSlot.index, event.button(), ClickType.PICKUP);
                    cir.setReturnValue(true);
                }
            }
            return;
        }

        StoragePageGridComponent clickedGrid = sbe$dashboard.findGridAt((int) mouseX, (int) mouseY);
        if (clickedGrid != null && !clickedGrid.isActive()) {
            sbe$sendNavigationCommand(clickedGrid);
            cir.setReturnValue(true);
            return;
        }

        // Prevent interaction with hidden vanilla slots behind the dashboard
        int dx = sbe$dashboard.getTotalX();
        int dy = sbe$dashboard.getTotalY();
        int dw = sbe$dashboard.getWidth();
        int dh = sbe$dashboard.getHeight();
        if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseDragged(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll != null && scroll.isMouseOver(event.x(), event.y())) {
            if (scroll.mouseDragged(event, dragX, dragY)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll != null) {
            if (scroll.mouseReleased(event)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        ScrollContainerWidget scroll = sbe$dashboard.getScrollContainer();
        if (scroll == null) return;

        int sx = sbe$dashboard.getTotalX();
        int sy = sbe$dashboard.getTotalY() + StorageDashboardComponent.TOP_BAR_HEIGHT;
        int sw = sbe$dashboard.getWidth();
        int sh = sbe$dashboard.getHeight() - StorageDashboardComponent.TOP_BAR_HEIGHT;

        if (mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + sh) {
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
