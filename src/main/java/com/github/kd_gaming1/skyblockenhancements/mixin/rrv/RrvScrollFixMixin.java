package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.common.overlay.OverlayManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compensates for owdding-lib's AbstractContainerScreenMixin cancelling
 * {@code mouseScrolled} before RRV's overlay gets a chance to handle it.
 *
 * <p>owdding-lib runs at default priority (1000) and calls
 * {@code cir.setReturnValue(...)}, which cancels the callback chain. RRV's
 * fix (priority = 900) is only available in a newer version. This mixin runs
 * last (priority = 1100) and ensures RRV's scroll logic always executes,
 * regardless of cancellation by earlier mixins.
 *
 * <p>TODO: Remove once RRV is updated to the version containing the priority fix.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 900)
public class RrvScrollFixMixin {

    @Inject(method = "mouseScrolled", at = @At("TAIL"), cancellable = true)
    private void sbe$fixRrvScroll(
            double mouseX, double mouseY,
            double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        // Re-run RRV's scroll handling unconditionally. If the overlay consumed
        // the scroll, override whatever owdding-lib set as the return value.
        if (OverlayManager.INSTANCE.scrollMouse(mouseX, mouseY, scrollX, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}