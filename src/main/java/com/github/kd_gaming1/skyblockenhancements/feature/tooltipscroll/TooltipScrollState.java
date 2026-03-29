package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Tracks scroll offset state for tooltip scrolling.
 *
 * <p>When Modern UI is active the mod still keeps the offsets here. Modern UI's
 * own scroll controller stays active until the user scrolls the mouse wheel,
 * at which point it is suppressed and our offsets take over.</p>
 */
public final class TooltipScrollState {

    private static double targetX;
    private static double targetY;
    private static double currentX;
    private static double currentY;

    /** Hash of the tooltip shown last frame – used to reset offsets on hover change. */
    private static int lastTooltipHash;

    /** Becomes {@code true} once the player has scrolled this tooltip. */
    private static boolean userScrolled;

    private static final double SMOOTHING = 0.3;

    private TooltipScrollState() {}

    /* ─────────────────────────── Input ─────────────────────────── */

    public static void scrollX(double amount) {
        userScrolled = true;
        targetX += amount;
    }

    public static void scrollY(double amount) {
        userScrolled = true;
        targetY += amount;
    }

    /* ───────────────────────── Rendering ───────────────────────── */

    /** Interpolates {@code current} toward {@code target}.  Call once each frame. */
    public static void update() {
        currentX += (targetX - currentX) * SMOOTHING;
        currentY += (targetY - currentY) * SMOOTHING;
    }

    /**
     * Called at the start of every tooltip render.
     * When the tooltip content changes we wipe the offsets and the ‘has-scrolled’ flag.
     */
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

    public static boolean hasUserScrolled() {
        return userScrolled;
    }

    /* ───────────────────────── Helpers ───────────────────────── */

    private static void reset() {
        targetX = targetY = currentX = currentY = 0;
        userScrolled = false;
    }

    /** A cheap hash based on component count + classes – enough to detect a different tooltip. */
    private static int computeHash(List<ClientTooltipComponent> components) {
        if (components == null || components.isEmpty()) {
            return 0;
        }
        int hash = components.size();
        for (ClientTooltipComponent c : components) {
            hash = 31 * hash + c.getClass().hashCode();
        }
        return hash;
    }
}