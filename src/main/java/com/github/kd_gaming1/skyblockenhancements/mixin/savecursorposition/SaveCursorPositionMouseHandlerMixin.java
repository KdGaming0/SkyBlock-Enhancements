/*
 * Based on code from Firmament:
 * https://github.com/FirmamentMC/Firmament
 *
 * This file contains significant portions adapted from Firmament's
 * Save Cursor Position implementation.
 *
 * The original code is licensed under the GNU General Public License v3.0.
 *
 * Modifications:
 * - Translated from Kotlin to Java
 */

package com.github.kd_gaming1.skyblockenhancements.mixin.savecursorposition;

import com.github.kd_gaming1.skyblockenhancements.feature.savecursorposition.SaveCursorPosition;
import com.github.kd_gaming1.skyblockenhancements.feature.savecursorposition.SaveCursorPosition.CursorPosition;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class SaveCursorPositionMouseHandlerMixin {

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    /** Captures the cursor position before vanilla centres it in grabMouse(). */
    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void onGrabMouseHead(CallbackInfo ci) {
        SaveCursorPosition.saveCursorOriginal(this.xpos, this.ypos);
    }

    /** Captures the screen centre after vanilla centres the cursor in grabMouse(). */
    @Inject(method = "grabMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;", ordinal = 2))
    private void onGrabMouseAfterCenter(CallbackInfo ci) {
        SaveCursorPosition.saveCursorMiddle(this.xpos, this.ypos);
    }

    /**
     * Replaces the centre position with the saved cursor position before releaseMouse()
     * asks GLFW to move the cursor. Updating xpos/ypos here keeps Minecraft's internal
     * mouse state in sync, so the first rendered frame shows the correct hover/tooltip.
     *
     * <p>We also call {@code InputConstants.grabOrReleaseMouse} ourselves: when the cursor
     * mode changes from disabled to normal, the first GLFW cursor-position update may not
     * visibly move the cursor on some window systems. Calling it here (before vanilla's
     * call) ensures the visible cursor lands on the restored position immediately.
     */
    @Inject(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;", ordinal = 2))
    private void onReleaseMouse(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Screen newScreen = mc.screen;
        CursorPosition position = SaveCursorPosition.loadCursor(this.xpos, this.ypos, newScreen);
        if (position != null) {
            this.xpos = position.x();
            this.ypos = position.y();
            InputConstants.grabOrReleaseMouse(mc.getWindow(), InputConstants.CURSOR_NORMAL, position.x(), position.y());
        }
    }
}
