package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.github.kd_gaming1.skyblockenhancements.gui.storage.ContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayLifecycle;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.HasContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.Rect;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractContainerScreenAccessor;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
            sbe$overlay.onInit(((Screen)(Object)this).width,
                    ((Screen)(Object)this).height);
            var searchField = sbe$overlay.getSearchField();
            if (searchField != null
                    && !((AbstractContainerScreen<?>)(Object)this).children().contains(searchField)) {
                ((ScreenAccessor) this).sbe$addWidget(searchField);
            }
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

    // ---- Slot hit-testing: redirect to overlay positions ----
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z",
            at = @At("HEAD"), cancellable = true)
    private void sbe$onIsHovering(Slot slot, double mouseX, double mouseY,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay == null) return;
        if (sbe$overlay.isPointOverSlot(slot, mouseX, mouseY)) {
            cir.setReturnValue(true);
        } else if (slot.container != Minecraft.getInstance().player.getInventory()) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "renderBackground",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void sbe$wrapRenderBg(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                                  float partialTick, int mouseX, int mouseY,
                                  Operation<Void> original) {
        original.call(screen, graphics, partialTick, mouseX, mouseY);
        if (sbe$overlay != null) {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor)(Object)this;
            int topPos = accessor.sbe$getTopPos();
            int imageHeight = accessor.sbe$getImageHeight();
            int screenWidth = ((Screen)(Object)this).width;
            int inventoryStartY = topPos + (imageHeight - 96);
            graphics.fill(0, topPos, screenWidth, inventoryStartY, 0xFF1A1A2E);
        }
    }

    // ---- Suppress vanilla slot highlight for container slots ----
    @Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void sbe$onRenderSlotHighlightBack(GuiGraphics graphics, CallbackInfo ci) {
        if (sbe$overlay != null && this.hoveredSlot != null
                && this.hoveredSlot.container != Minecraft.getInstance().player.getInventory()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void sbe$onRenderSlotHighlightFront(GuiGraphics graphics, CallbackInfo ci) {
        if (sbe$overlay != null && this.hoveredSlot != null
                && this.hoveredSlot.container != Minecraft.getInstance().player.getInventory()) {
            ci.cancel();
        }
    }

    // ---- Prevent overlay area from being treated as outside the container ----
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void sbe$onHasClickedOutside(double mouseX, double mouseY,
                                         int leftPos, int topPos,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay == null) return;
        for (Rect rect : sbe$overlay.getBounds()) {
            if (rect.contains(mouseX, mouseY)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    // ---- Cleanup on close ----
    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$onRemoved(CallbackInfo ci) {
        sbe$overlay = null;
    }
}