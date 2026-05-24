package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.util.NetworkStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PingDebugMonitor;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin targeting {@link ClientPacketListener} for two purposes:
 *
 * <ol>
 *   <li><b>TPS measurement</b> — injects into {@code handleSetTime} to feed
 *       {@link NetworkStats} with {@code ClientboundSetTimePacket} arrival
 *       timestamps. Because the server sends one packet per tick, the
 *       wall-clock interval between consecutive arrivals directly measures
 *       milliseconds-per-tick, from which TPS is derived.</li>
 *
 *   <li><b>Ping monitor forcing</b> — injects into {@code tick} to call
 *       {@code pingDebugMonitor.tick()} unconditionally. Vanilla only ticks
 *       the monitor when F3 network charts are open
 *       ({@code debugOverlay.showNetworkCharts()}), which means
 *       {@code debugOverlay.getPingLogger()} would otherwise stay empty
 *       during normal play. Forcing the tick ensures ping samples are always
 *       available for {@link NetworkStats#getPingMs()} to read.</li>
 * </ol>
 *
 * <p><b>Why not {@code PlayerInfo#getLatency()}?</b> Proxy-based servers such
 * as Hypixel hardcode the tab-list latency field to {@code 1} on game servers,
 * making it unsuitable for real ping measurement. The
 * {@code PingRequest} / {@code PongResponse} exchange used by
 * {@link PingDebugMonitor} is independent of that field and reflects true RTT.
 *
 * @see NetworkStats#onSetTimePacket(long)
 * @see NetworkStats#getPingMs()
 */
@Mixin(ClientPacketListener.class)
public class NetworkPacketMixin {

    @Final
    @Shadow private PingDebugMonitor pingDebugMonitor;

    // ═══════════════════════════════════════════════════════════════════════════
    //  TPS — SetTime packet (one per server tick)
    // ═══════════════════════════════════════════════════════════════════════════

    @Inject(
            method = "handleSetTime",
            at = @At("HEAD")
    )
    private void skyblockenhancements$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        NetworkStats.onSetTimePacket(packet.gameTime());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Ping — force PingDebugMonitor to tick every game tick
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Forces {@link PingDebugMonitor#tick()} to run every game tick, even when
     * the F3 debug network charts are not visible.
     *
     * <p>Vanilla guards the call with {@code debugOverlay.showNetworkCharts()},
     * so without this injection the ping logger would never be populated during
     * normal play. We skip the call when vanilla already ran it to avoid sending
     * two ping requests in the same tick.
     */
    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void skyblockenhancements$forcePingTick(CallbackInfo ci) {
        if (!Minecraft.getInstance().getDebugOverlay().showNetworkCharts()) {
            pingDebugMonitor.tick();
        }
    }
}