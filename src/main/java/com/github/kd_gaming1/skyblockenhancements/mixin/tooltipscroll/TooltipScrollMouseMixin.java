package com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll.TooltipScrollState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures mouse scroll events and feeds them into {@link TooltipScrollState}.
 *
 * <p>Holding Left Shift redirects vertical scroll to the horizontal axis.</p>
 */
@Mixin(MouseHandler.class)
public class TooltipScrollMouseMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"))
    private void onScrollWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableTooltipScroll || yOffset == 0) return;

        double effective = SkyblockEnhancementsConfig.invertTooltipScroll ? -yOffset : yOffset;
        int speed = SkyblockEnhancementsConfig.tooltipScrollSpeed;

        boolean shiftHeld = GLFW.glfwGetKey(
                Minecraft.getInstance().getWindow().handle(),
                GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

        boolean goHorizontal = SkyblockEnhancementsConfig.enableHorizontalScroll && shiftHeld;

        if (goHorizontal) {
            TooltipScrollState.scrollX(effective * speed);
        } else {
            TooltipScrollState.scrollY(effective * speed);
        }
    }
}