package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.github.kd_gaming1.skyblockenhancements.gui.storage.ContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayLifecycle;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.HasContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lightweight mixin that attaches a {@link ContainerOverlay} to storage screens.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenOverlayMixin<T extends AbstractContainerMenu> implements HasContainerOverlay {

    @Unique
    private ContainerOverlay sbe$overlay;

    @Override
    public ContainerOverlay skyBlock_Enhancements$getSbeOverlay() {
        return sbe$overlay;
    }

    @Override
    public void skyBlock_Enhancements$setSbeOverlay(ContainerOverlay overlay) {
        this.sbe$overlay = overlay;
    }

    @Shadow protected @Nullable Slot hoveredSlot;
    @Shadow protected T menu;

    // ---- Attach overlay on init ----
    @Inject(method = "init()V", at = @At("TAIL"))
    private void sbe$onInit(CallbackInfo ci) {
        if (sbe$overlay == null) {
            ContainerOverlay overlay = StorageOverlayLifecycle.createOverlay(
                    (AbstractContainerScreen<?>)(Object)this);
            if (overlay != null) {
                sbe$overlay = overlay;
            }
        }
        if (sbe$overlay != null) {
            sbe$overlay.onInit(((net.minecraft.client.gui.screens.Screen)(Object)this).width,
                    ((net.minecraft.client.gui.screens.Screen)(Object)this).height);
            var searchField = sbe$overlay.getSearchField();
            if (searchField != null
                    && !((AbstractContainerScreen<?>)(Object)this).children().contains(searchField)) {
                ((ScreenAccessor) this).sbe$addWidget(searchField);
            }
        }
    }

    // ---- Suppress vanilla background (including renderBg) when overlay is active ----
    // renderBg is ABSTRACT — we must cancel renderBackground instead, which calls it.
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onRenderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (sbe$overlay != null) {
            ci.cancel(); // Our overlay draws its own background
        }
    }

    // ---- Pre-render: compute slot positions before vanilla hit-tests ----
    @Inject(method = "render", at = @At("HEAD"))
    private void sbe$preRender(GuiGraphics graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        if (sbe$overlay != null) {
            sbe$overlay.preRender(mouseX, mouseY);
        }
    }

    // ---- Render overlay on top ----
    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderOverlay(GuiGraphics graphics, int mouseX, int mouseY,
                                   float partialTick, CallbackInfo ci) {
        if (sbe$overlay != null) {
            sbe$overlay.render(graphics, partialTick, mouseX, mouseY);
            // Re-render carried item on top of everything
            ItemStack carried = this.menu.getCarried();
            if (!carried.isEmpty()) {
                graphics.renderItem(carried, mouseX - 8, mouseY - 8);
                graphics.renderItemDecorations(
                        Minecraft.getInstance().font, carried, mouseX - 8, mouseY - 8);
            }
        }
    }

    // ---- Suppress vanilla slot rendering for container slots ----
    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;" +
            "Lnet/minecraft/world/inventory/Slot;II)V",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onRenderSlot(GuiGraphics graphics, Slot slot,
                                  int mouseX, int mouseY, CallbackInfo ci) {
        if (sbe$overlay != null && !sbe$overlay.shouldDrawForeground()
                && slot.container != Minecraft.getInstance().player.getInventory()) {
            ci.cancel();
        }
    }

    // ---- Suppress vanilla tooltips for container slots ----
    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onRenderTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (sbe$overlay != null && this.hoveredSlot != null
                && this.hoveredSlot.container != Minecraft.getInstance().player.getInventory()) {
            ci.cancel();
        }
    }

    // ---- Input delegation ----
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseReleased(MouseButtonEvent event,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseDragged(MouseButtonEvent event, double dx, double dy,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseDragged(event, dx, dy)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseScrolled(double x, double y, double scrollX, double scrollY,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseScrolled(x, y, scrollX, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onKeyPressed(KeyEvent event,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }

    // ---- Slot hit-testing restriction ----
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onIsHovering(Slot slot, double mouseX, double mouseY,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && !sbe$overlay.isPointOverSlot(slot, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    // ---- Cleanup on close ----
    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$onRemoved(CallbackInfo ci) {
        if (sbe$overlay != null) {
            StorageOverlayLifecycle.onOverlayClosed();
            sbe$overlay = null;
        }
    }
}