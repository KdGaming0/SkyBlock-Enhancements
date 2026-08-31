package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll.ClientTextTooltipAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public final class TooltipScrollState {

    private static double targetX;
    private static double targetY;
    private static double currentX;
    private static double currentY;

    /** Whether the most recently extracted screen frame contained a tooltip. */
    private static boolean tooltipActive;

    // ── Hover identity (replaces lastComponents) ─────────────────────────────
    private static String lastScreenClass;
    private static int lastMouseX = Integer.MIN_VALUE;
    private static int lastMouseY = Integer.MIN_VALUE;
    private static String lastFirstLine;

    private static final double SMOOTHING = 0.3;
    /** How many pixels the mouse can move before we consider it a new slot/item. */
    private static final int POSITION_THRESHOLD = 20;

    private TooltipScrollState() {}

    /** Clears the activity marker before the screen extracts its next frame. */
    public static void beginFrame() {
        tooltipActive = false;
    }

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
     * Determines whether we're still hovering the same item/element.
     * Resets scroll only when the screen, position, or item name changes.
     */
    public static void trackTooltip(List<ClientTooltipComponent> components, int mouseX, int mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        String screenClass = screen != null ? screen.getClass().getName() : "null";

        // Extract the first text line — usually the item name, which is stable.
        String firstLine = "";
        if (!components.isEmpty() && components.get(0) instanceof ClientTextTooltip textComp) {
            firstLine = FormattedCharSequenceReader.read(
                    ((ClientTextTooltipAccessor) textComp).getText());
        }

        boolean sameScreen = screenClass.equals(lastScreenClass);
        boolean samePosition = lastMouseX != Integer.MIN_VALUE
                && Math.abs(mouseX - lastMouseX) < POSITION_THRESHOLD
                && Math.abs(mouseY - lastMouseY) < POSITION_THRESHOLD;
        boolean sameItem = firstLine.equals(lastFirstLine);

        // Only reset when we actually moved to a different item/screen.
        if (!sameScreen || !samePosition || !sameItem) {
            reset();
        }

        lastScreenClass = screenClass;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastFirstLine = firstLine;
        tooltipActive = !components.isEmpty();
    }

    /**
     * Returns whether wheel input still belongs to the tooltip rendered in the
     * most recently completed frame.
     */
    public static boolean isTooltipActive(Screen screen, double mouseX, double mouseY) {
        return tooltipActive
                && screen != null
                && screen.getClass().getName().equals(lastScreenClass)
                && Math.abs(mouseX - lastMouseX) < POSITION_THRESHOLD
                && Math.abs(mouseY - lastMouseY) < POSITION_THRESHOLD;
    }

    public static float getXOffset() {
        return (float) currentX;
    }

    public static float getYOffset() {
        return (float) currentY;
    }

    public static void resetAll() {
        reset();
        tooltipActive = false;
        lastScreenClass = null;
        lastMouseX = Integer.MIN_VALUE;
        lastMouseY = Integer.MIN_VALUE;
        lastFirstLine = null;
    }

    private static void reset() {
        targetX = 0;
        targetY = 0;
        currentX = 0;
        currentY = 0;
    }
}
