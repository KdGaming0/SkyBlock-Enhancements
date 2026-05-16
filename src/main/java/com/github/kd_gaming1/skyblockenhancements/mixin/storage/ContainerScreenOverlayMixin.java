package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.github.kd_gaming1.skyblockenhancements.gui.storage.ContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayLifecycle;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.HasContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "init()V", at = @At("TAIL"))
    private void sbe$onInit(CallbackInfo ci) {
        if (sbe$overlay == null) {
            ContainerOverlay overlay = StorageOverlayLifecycle.createOverlay((AbstractContainerScreen<?>)(Object)this);
            if (overlay != null) {
                sbe$overlay = overlay;
            }
        }
        if (sbe$overlay != null) {
            sbe$overlay.onInit(((Screen)(Object)this).width, ((Screen)(Object)this).height);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sbe$preRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (sbe$overlay != null) {
            sbe$overlay.preRender(mouseX, mouseY);
        }
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void sbe$renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (sbe$overlay != null) {
            sbe$overlay.render(graphics, partialTick, mouseX, mouseY);
        }
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void sbe$onRenderLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (sbe$overlay != null) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
            int yOffset = screen.getMenu().slots.size() > 36 ? screen.getMenu().slots.get(screen.getMenu().slots.size() - 36).y - 12 : 0;
            graphics.drawString(Minecraft.getInstance().font, Component.translatable("container.inventory"), 8, yOffset, 4210752, false);
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseClicked(event, doubleClick)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseReleased(event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseDragged(event, dx, dy)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseScrolled(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.mouseScrolled(x, y, scrollX, scrollY)) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay != null && sbe$overlay.keyPressed(event)) cir.setReturnValue(true);
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void sbe$onHasClickedOutside(double mouseX, double mouseY, int leftPos, int topPos, CallbackInfoReturnable<Boolean> cir) {
        if (sbe$overlay == null) return;
        for (Rect rect : sbe$overlay.getBounds()) {
            if (rect.contains(mouseX, mouseY)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$onRemoved(CallbackInfo ci) {
        sbe$overlay = null;
    }
}