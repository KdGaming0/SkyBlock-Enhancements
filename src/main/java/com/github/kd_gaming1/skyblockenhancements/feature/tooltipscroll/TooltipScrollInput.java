package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Routes mouse-wheel input between tooltips and the screen below them. */
public final class TooltipScrollInput {

    private TooltipScrollInput() {}

    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.beforeExtract(screen).register(
                    (currentScreen, graphics, mouseX, mouseY, tickProgress) -> TooltipScrollState.beginFrame());
            ScreenEvents.remove(screen).register(currentScreen -> TooltipScrollState.resetAll());

            ScreenMouseEvents.allowMouseScroll(screen).register(TooltipScrollInput::allowScreenScroll);
            ScreenMouseEvents.afterMouseScroll(screen).register(TooltipScrollInput::afterScreenScroll);
        });
    }

    /**
     * Left Alt gives an active tooltip exclusive ownership before the screen can scroll.
     */
    private static boolean allowScreenScroll(
            Screen screen,
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount) {
        if (!shouldScrollTooltip(screen, mouseX, mouseY, verticalAmount) || !isKeyDown(GLFW.GLFW_KEY_LEFT_ALT)) {
            return true;
        }

        scrollTooltip(verticalAmount);
        return false;
    }

    /**
     * When the screen did not use the wheel, preserve the normal tooltip-scrolling
     * behavior without requiring Left Alt.
     */
    private static boolean afterScreenScroll(
            Screen screen,
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            boolean consumed) {
        if (consumed || !shouldScrollTooltip(screen, mouseX, mouseY, verticalAmount)) {
            return false;
        }

        scrollTooltip(verticalAmount);
        return true;
    }

    private static boolean shouldScrollTooltip(
            Screen screen, double mouseX, double mouseY, double verticalAmount) {
        return SkyblockEnhancementsConfig.enableTooltipScroll
                && verticalAmount != 0
                && TooltipScrollState.isTooltipActive(screen, mouseX, mouseY);
    }

    private static void scrollTooltip(double verticalAmount) {
        double effective = SkyblockEnhancementsConfig.invertTooltipScroll ? -verticalAmount : verticalAmount;
        double amount = effective * SkyblockEnhancementsConfig.tooltipScrollSpeed;
        boolean goHorizontal = SkyblockEnhancementsConfig.enableHorizontalScroll
                && isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT);

        if (goHorizontal) {
            TooltipScrollState.scrollX(amount);
        } else {
            TooltipScrollState.scrollY(amount);
        }
    }

    private static boolean isKeyDown(int key) {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }
}
