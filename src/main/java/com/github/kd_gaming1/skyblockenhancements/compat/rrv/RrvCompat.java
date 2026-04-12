package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Guards all RRV integration behind a mod-presence + config check.
 *
 * <p>The {@code overlayTooltipActive} flag is volatile to prevent stale reads if
 * tooltip rendering happens on a slightly different frame path.
 */
public final class RrvCompat {

    private static final boolean RRV_PRESENT =
            FabricLoader.getInstance().isModLoaded("rrv");

    /**
     * Set to {@code true} for the duration of a tooltip build triggered by an RRV
     * item-list overlay slot hover. Volatile for visibility across rendering paths.
     */
    private static volatile boolean overlayTooltipActive = false;

    private RrvCompat() {}

    /** Returns {@code true} only when RRV is installed AND the config toggle is on. */
    public static boolean isActive() {
        return RRV_PRESENT && SkyblockEnhancementsConfig.enableRecipeViewer;
    }

    public static boolean isRrvPresent() {
        return RRV_PRESENT;
    }

    /**
     * Marks the start of a tooltip build for an RRV item-list overlay slot.
     * Must be paired with {@link #exitOverlayTooltip()} in a {@code finally} block.
     */
    public static void enterOverlayTooltip() {
        overlayTooltipActive = true;
    }

    /**
     * Marks the end of a tooltip build for an RRV item-list overlay slot.
     * Must be called in a {@code finally} block paired with {@link #enterOverlayTooltip()}.
     */
    public static void exitOverlayTooltip() {
        assert overlayTooltipActive : "exitOverlayTooltip called without matching enterOverlayTooltip";
        overlayTooltipActive = false;
    }

    /**
     * Returns {@code true} while a tooltip is being built for an item in the RRV
     * item-list overlay, as opposed to a genuine hovered container slot.
     */
    public static boolean isOverlayTooltip() {
        return overlayTooltipActive;
    }
}