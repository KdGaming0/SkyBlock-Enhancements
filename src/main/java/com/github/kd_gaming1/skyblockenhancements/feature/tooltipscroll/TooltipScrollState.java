package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll.ClientTextTooltipAccessor;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Tracks scroll offset state for tooltip scrolling.
 */
public final class TooltipScrollState {

    private static double targetX;
    private static double targetY;
    private static double currentX;
    private static double currentY;

    /** The tooltip components from the previous frame, used for identity comparison. */
    private static List<ClientTooltipComponent> lastComponents;

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

    /**
     * Checks whether the current tooltip matches the previous one.
     * If the tooltip changed (different item hovered), resets all scroll offsets.
     */
    public static void trackTooltip(List<ClientTooltipComponent> components) {
        if (!isEqual(lastComponents, components)) {
            reset();
            lastComponents = components;
        }
    }

    public static float getXOffset() {
        return (float) currentX;
    }

    public static float getYOffset() {
        return (float) currentY;
    }

    /**
     * Resets all scroll state. Called when a screen closes or when
     * a new tooltip is detected.
     */
    public static void resetAll() {
        reset();
        lastComponents = null;
    }

    private static void reset() {
        targetX = 0;
        targetY = 0;
        currentX = 0;
        currentY = 0;
    }

    /**
     * Compares two tooltip component lists by actual text content.
     *
     * <p>Adapted from Provismet's tooltip-scroll mod (MIT license).</p>
     */
    private static boolean isEqual(List<ClientTooltipComponent> a, List<ClientTooltipComponent> b) {
        if (a == null || b == null || a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            ClientTooltipComponent compA = a.get(i);
            ClientTooltipComponent compB = b.get(i);

            boolean aIsText = compA instanceof ClientTextTooltip;
            boolean bIsText = compB instanceof ClientTextTooltip;

            if (aIsText != bIsText) return false;
            if (!aIsText) continue;

            String textA = FormattedCharSequenceReader.read(
                    ((ClientTextTooltipAccessor) compA).getText());
            String textB = FormattedCharSequenceReader.read(
                    ((ClientTextTooltipAccessor) compB).getText());

            if (!textA.equals(textB)) return false;
        }

        return true;
    }
}