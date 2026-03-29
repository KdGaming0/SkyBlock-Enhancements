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
 * Captures mouse scroll events and feeds {@link TooltipScrollState} (both axes).
 *
 * <p>The scroll offset is applied via matrix translation in the {@code GuiGraphics} mixin,
 * which works for both vanilla and Modern UI tooltips. Modern UI's internal scroll system
 * is suppressed every frame by the render mixin so it doesn't conflict.
 *
 * <p>Holding Left Shift switches to horizontal scrolling.
 */
@Mixin(MouseHandler.class)
public class TooltipScrollMouseMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"))
    private void onScrollWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableTooltipScroll || yOffset == 0) {
            return;
        }

        double effective = SkyblockEnhancementsConfig.invertTooltipScroll ? -yOffset : yOffset;
        int speed = SkyblockEnhancementsConfig.tooltipScrollSpeed;

        boolean horizontal = SkyblockEnhancementsConfig.enableHorizontalScroll
                && GLFW.glfwGetKey(
                Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT)
                == GLFW.GLFW_PRESS;

        if (horizontal) {
            TooltipScrollState.scrollX(effective * speed);
        } else {
            TooltipScrollState.scrollY(effective * speed);
        }
    }
}