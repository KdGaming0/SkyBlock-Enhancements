package com.github.kd_gaming1.skyblockenhancements.feature.mining;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.render.MiningOverlayRenderer;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.track.MiningProgressTracker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Main feature class for Ping Offset Mining.
 *
 * <p>Orchestrates all subsystems: detection, tracking, calculation, and rendering.
 *
 * <p>Call {@link #register()} once during mod initialization.
 */
public final class PingOffsetMiningFeature {

    private PingOffsetMiningFeature() {}

    private static boolean registered = false;

    /** Registers all Ping Offset Mining subsystems. Idempotent. */
    public static void register() {
        if (registered) return;
        registered = true;

        MiningProgressTracker.register();
        MiningOverlayRenderer.getInstance().register();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MiningProgressTracker.reset();
            MiningOverlayRenderer.getInstance().clear();
        });
    }
}
