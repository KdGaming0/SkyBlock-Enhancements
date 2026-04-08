package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Tracks scroll offset state for tooltip scrolling.
 */
public final class TooltipScrollState {

    private static double targetX;
    private static double targetY;
    private static double currentX;
    private static double currentY;
    private static int lastTooltipHash;

    private static final double SMOOTHING = 0.3;

    private TooltipScrollState() {}

    public static void scrollX(double amount) {
        targetX += amount;
    }

    public static void scrollY(double amount) {
        targetY += amount;
    }

    public static void update() {
        currentX += (targetX - currentX) * SMOOTHING;
        currentY += (targetY - currentY) * SMOOTHING;
    }

    public static void trackTooltip(List<ClientTooltipComponent> components) {
        int hash = computeHash(components);
        if (hash != lastTooltipHash) {
            reset();
            lastTooltipHash = hash;
        }
    }

    public static float getXOffset() {
        return (float) currentX;
    }

    public static float getYOffset() {
        return (float) currentY;
    }

    private static void reset() {
        targetX = 0;
        targetY = 0;
        currentX = 0;
        currentY = 0;
    }

    private static int computeHash(List<ClientTooltipComponent> components) {
        if (components == null || components.isEmpty()) return 0;
        int hash = components.size();
        for (ClientTooltipComponent c : components) {
            hash = 31 * hash + c.getClass().hashCode();
        }
        return hash;
    }
}