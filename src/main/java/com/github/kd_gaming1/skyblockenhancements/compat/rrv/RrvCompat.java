package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.fabricmc.loader.api.FabricLoader;

/** Guards all RRV integration behind a mod-presence + config check. */
public final class RrvCompat {

    private static final boolean RRV_PRESENT =
            FabricLoader.getInstance().isModLoaded("rrv");

    /**
     * Set to {@code true} for the duration of a tooltip build triggered by an RRV item-list overlay
     * slot hover.
     *
     * <p>Allows downstream features (e.g. {@link
     * com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants}) to skip
     * processing that is only useful when the player is actually inspecting an item in a container.
     */
    private static boolean overlayTooltipActive = false;

    private RrvCompat() {}

    /** Returns {@code true} only when RRV is installed AND the config toggle is on. */
    public static boolean isActive() {
        return RRV_PRESENT && SkyblockEnhancementsConfig.enableRecipeViewer;
    }

    public static boolean isRrvPresent() {
        return RRV_PRESENT;
    }

    /**
     * Marks the start of a tooltip build for an RRV item-list overlay slot. Must be paired with
     * {@link #exitOverlayTooltip()} in a {@code finally} block.
     */
    public static void enterOverlayTooltip() {
        overlayTooltipActive = true;
    }

    /** Marks the end of a tooltip build for an RRV item-list overlay slot. */
    public static void exitOverlayTooltip() {
        overlayTooltipActive = false;
    }

    /**
     * Returns {@code true} while a tooltip is being built for an item in the RRV item-list overlay,
     * as opposed to a genuine hovered container slot.
     */
    public static boolean isOverlayTooltip() {
        return overlayTooltipActive;
    }
}