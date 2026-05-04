package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayManager;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageTitleParser;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageDashboardComponent;
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
    private static final int DASHBOARD_PADDING = 6;
    @Unique
    private static final int MINI_SLOT_SIZE = 16;
    @Unique
    private static final int MINI_SLOT_GAP = 1;

    protected StorageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$onInit(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard) return;

        StorageOverlayManager manager = StorageFeature.getManager();
        if (manager == null) return;

        Optional<StorageTitleParser.ParsedTitle> parsed = manager.classifyTitle(this.title.getString());
        if (parsed.isEmpty()) return;

        sbe$isStorageScreen = true;
        sbe$manager = manager;
        sbe$parsedTitle = parsed.get();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            List<Slot> containerSlots = new ArrayList<>();
            for (Slot slot : this.menu.slots) {
                if (slot.container != mc.player.getInventory()) {
                    containerSlots.add(slot);
                }
            }
            manager.capturePage(parsed.get(), containerSlots, mc.level.registryAccess());
        }

        sbe$buildDashboard();
        sbe$buildSearchBox();
    }

    @Unique
    private void sbe$buildDashboard() {
        int firstInvY = sbe$findFirstInventorySlotY();
        int dashboardHeight = Math.max(0, firstInvY - DASHBOARD_PADDING - this.topPos);

        sbe$dashboard = new StorageDashboardComponent(
                this.leftPos, this.topPos,
                this.imageWidth, dashboardHeight,
                sbe$manager, sbe$parsedTitle.pageId(),
                MINI_SLOT_SIZE, MINI_SLOT_GAP,
                sbe$searchQuery,
                sbe$hoverableSlots);

        sbe$dashboard.updateParentPosition(this.leftPos, this.topPos, this.imageWidth, this.height);
    }

    @Unique
    private void sbe$buildSearchBox() {
        Minecraft mc = Minecraft.getInstance();
        sbe$searchBox = new EditBoxWidget(
                mc.font,
                this.leftPos + 8,
                this.topPos + 4,
                this.imageWidth - 100,
                16,
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
        return minY == Integer.MAX_VALUE ? this.topPos + this.imageHeight - 96 : minY;
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void sbe$suppressChestSlots(GuiGraphics guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
        if (!sbe$isStorageScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (slot.container != mc.player.getInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderDashboard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!sbe$isStorageScreen || sbe$dashboard == null) return;

        sbe$dashboard.updateIfDirty();
        sbe$dashboard.renderBase(graphics, mouseX, mouseY, partialTick, this.width, this.height);

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

        if (sbe$dashboard.isInsideActiveGrid((int) mouseX, (int) mouseY)) {
            int slotIndex = sbe$dashboard.translateActiveGridClick((int) mouseX, (int) mouseY);
            if (slotIndex >= 0) {
                Slot vanillaSlot = this.menu.getSlot(slotIndex);
                if (vanillaSlot != null) {
                    this.slotClicked(vanillaSlot, vanillaSlot.index, event.button(), ClickType.PICKUP);
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$onRemoved(CallbackInfo ci) {
        if (!sbe$isStorageScreen) return;
        if (sbe$searchBox != null) {
            removeWidget(sbe$searchBox);
        }
        StorageFeature.save();
    }

    @Unique
    private StorageSlotComponent sbe$findHoveredSlot(int mouseX, int mouseY) {
        for (StorageSlotComponent slot : sbe$hoverableSlots) {
            if (slot.isHovered(mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }
}
