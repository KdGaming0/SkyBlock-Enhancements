package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.fabricmc.loader.api.FabricLoader;

/** Guards all RRV integration behind a mod-presence + config check. */
public final class RrvCompat {

    private static final boolean RRV_PRESENT =
            FabricLoader.getInstance().isModLoaded("rrv");

    private RrvCompat() {}

    /** Returns {@code true} only when RRV is installed AND the config toggle is on. */
    public static boolean isActive() {
        return RRV_PRESENT && SkyblockEnhancementsConfig.enableRecipeViewer;
    }

    public static boolean isRrvPresent() {
        return RRV_PRESENT;
    }
}