package com.github.kd_gaming1.skyblockenhancements.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.debugchart.LocalSampleLogger;

import java.util.Arrays;

/**
 * Tracks network latency (ping) and server TPS from packet timing.
 *
 * <p><b>Ping</b> is read directly from Minecraft's built-in
 * {@code debugOverlay.getPingLogger()}, which is populated by
 * {@link net.minecraft.client.multiplayer.PingDebugMonitor} via
 * {@code ServerboundPingRequestPacket} / {@code ClientboundPongResponsePacket}
 * exchanges. The client embeds {@link net.minecraft.util.Util#getMillis()} in
 * the request; the server echoes it back unchanged, so
 * {@code Util.getMillis() - packet.time()} gives a true RTT with no
 * server-side clock involvement.
 *
 * <p>This approach works correctly on proxy-based servers such as Hypixel,
 * which artificially set the tab-list latency field
 * ({@code PlayerInfo.getLatency()}) to {@code 1} on game servers.
 * Because {@code PingDebugMonitor} is only ticked by vanilla when F3 network
 * charts are open, {@link com.github.kd_gaming1.skyblockenhancements.mixin.NetworkPacketMixin}
 * forces it to tick every game tick regardless of the debug overlay state.
 *
 * <p><b>TPS</b> is measured from {@code SetTime} packet intervals. The server
 * sends one packet per tick; the wall-clock time between consecutive arrivals
 * divided by the tick delta gives milliseconds-per-tick, from which TPS is
 * derived and clamped to 20.0.
 *
 * <p>Query from anywhere after calling {@link #register()} once during mod init:
 * <pre>{@code
 *   int    ping = NetworkStats.getPingMs();       // e.g. 45, or -1 if not ready
 *   double tps  = NetworkStats.getTps();           // e.g. 19.82, capped at 20.0
 *   boolean ok  = NetworkStats.hasEnoughData();    // false until both are ready
 * }</pre>
 */
public final class NetworkStats {

    private NetworkStats() {}

    // ═══════════════════════════════════════════════════════════════════════════
    //  Config
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int    TPS_WINDOW       = 100;
    private static final int    TPS_MIN_SAMPLES  = 20;
    private static final int    PING_MIN_SAMPLES = 5;
    private static final double MAX_TPS          = 20.0;
    private static final long   NANOS_PER_MS     = 1_000_000L;

    // ═══════════════════════════════════════════════════════════════════════════
    //  TPS state — SetTime packets
    // ═══════════════════════════════════════════════════════════════════════════

    private static final double[] msPerTick = new double[TPS_WINDOW];
    private static int    tpsIdx            = 0;
    private static int    tpsCount          = 0;
    private static long   lastGameTime      = -1;
    private static long   lastSetTimeNs     = -1;
    private static double cachedTps         = MAX_TPS;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Registers hooks. Idempotent. */
    public static void register() {}

    /**
     * Returns the average ping in milliseconds read from Minecraft's built-in
     * ping logger, which is fed by genuine {@code PingRequest} / {@code PongResponse}
     * round-trips and is unaffected by proxy-side tab-list latency overrides.
     *
     * @return average RTT in milliseconds, or {@code -1} if fewer than
     *         {@value PING_MIN_SAMPLES} samples are available yet
     */
    public static int getPingMs() {
        Minecraft mc = Minecraft.getInstance();

        LocalSampleLogger logger = mc.getDebugOverlay().getPingLogger();

        int size = logger.size();
        if (size < PING_MIN_SAMPLES) return -1;

        long sum = 0;
        for (int i = 0; i < size; i++) sum += logger.get(i);
        return (int) (sum / size);
    }

    /**
     * Returns the smoothed server TPS, clamped to {@value MAX_TPS}.
     * Returns {@value MAX_TPS} until enough samples have been collected.
     */
    public static double getTps() {
        return cachedTps;
    }

    /**
     * Returns {@code true} once both ping and TPS have enough samples for a
     * stable reading.
     */
    public static boolean hasEnoughData() {
        Minecraft mc = Minecraft.getInstance();
        boolean pingReady = mc.getDebugOverlay().getPingLogger().size() >= PING_MIN_SAMPLES;
        return tpsCount >= TPS_MIN_SAMPLES && pingReady;
    }

    /** Returns the number of TPS samples collected so far. */
    public static int getTpsSampleCount() { return tpsCount; }

    /**
     * Returns the number of ping samples currently held in Minecraft's
     * ping logger, or {@code 0} if the client is not available.
     */
    public static int getPingSampleCount() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getDebugOverlay().getPingLogger().size();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TPS — called by mixin on ClientboundSetTimePacket
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Records a TPS sample from a {@code ClientboundSetTimePacket} arrival.
     * Called by {@link com.github.kd_gaming1.skyblockenhancements.mixin.NetworkPacketMixin}.
     *
     * @param gameTime the {@code gameTime} field from the packet, used to
     *                 determine how many ticks elapsed since the last packet
     */
    public static void onSetTimePacket(long gameTime) {
        long nowNs = System.nanoTime();

        if (lastGameTime >= 0) {
            long tickDelta = gameTime - lastGameTime;
            long wallNs    = nowNs - lastSetTimeNs;

            if (tickDelta > 0 && wallNs > 0) {
                double msForOneTick = (wallNs / (double) NANOS_PER_MS) / tickDelta;

                // A real server tick is never faster than 50 ms (20 TPS cap).
                // Clamp to [50, 5000] to handle batching and hitches.
                msForOneTick = Math.clamp(msForOneTick, 50.0, 5000.0);

                msPerTick[tpsIdx] = msForOneTick;
                tpsIdx = (tpsIdx + 1) % TPS_WINDOW;
                if (tpsCount < TPS_WINDOW) tpsCount++;

                recomputeTps();
            }
        }

        lastGameTime  = gameTime;
        lastSetTimeNs = nowNs;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Reset
    // ═══════════════════════════════════════════════════════════════════════════

    /** Resets all TPS state. Call on server disconnect or world change. */
    public static void reset() {
        Arrays.fill(msPerTick, 0.0);
        tpsIdx        = 0;
        tpsCount      = 0;
        lastGameTime  = -1;
        lastSetTimeNs = -1;
        cachedTps     = MAX_TPS;
        // Ping state lives in debugOverlay.getPingLogger() — no local state to clear.
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Recompute
    // ═══════════════════════════════════════════════════════════════════════════

    private static void recomputeTps() {
        if (tpsCount == 0) {
            cachedTps = MAX_TPS;
            return;
        }
        double sum = 0;
        for (int i = 0; i < tpsCount; i++) sum += msPerTick[i];
        double avgMs = sum / tpsCount;
        cachedTps = Math.min(MAX_TPS, 1000.0 / avgMs);
    }
}