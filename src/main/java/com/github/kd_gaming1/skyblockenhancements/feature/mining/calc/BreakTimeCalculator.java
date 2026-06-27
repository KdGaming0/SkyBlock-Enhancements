/*
 * Break-time and ping-offset timing math adapted from Revvilon/PingOffsetMiner
 * (SpeedCalc), CC0-1.0: https://github.com/Revvilon/PingOffsetMiner
 * See THIRD_PARTY_LICENSES.md for the full attribution.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.calc;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthRegistry.Entry;

/**
 * Pure static math for mining break-time and ping-offset progress.
 *
 * <p>Side-effect-free and allocation-free; references no Minecraft classes.
 */
public final class BreakTimeCalculator {

    private BreakTimeCalculator() {}

    /** Server ticks below which a block cannot break (Hypixel soft cap). */
    private static final int MIN_BREAK_TICKS = 4;

    /**
     * Ticks required to break a block: {@code round(strength * 30 / miningSpeed)},
     * floored at {@link #MIN_BREAK_TICKS}. Uses {@link Math#round} (not ceil) to
     * match the server-side calculation documented on the Hypixel wiki.
     *
     * @return break time in ticks, or {@link Integer#MAX_VALUE} if miningSpeed <= 0
     */
    public static int calculateBreakTicks(double blockStrength, int miningSpeed) {
        if (miningSpeed <= 0) return Integer.MAX_VALUE;
        double raw = (blockStrength * 30.0) / miningSpeed;
        return (int) Math.max(MIN_BREAK_TICKS, Math.round(raw));
    }

    public static int calculateBreakTicks(Entry entry, int miningSpeed) {
        return calculateBreakTicks(entry.strength(), miningSpeed);
    }

    /**
     * Mining progress as a fraction in {@code [0.0, 1.0]}.
     *
     * <p>This is the core of ping-offset mining: it shortens the effective break
     * time by the ticks lost to latency (plus an optional margin) so the visual
     * indicator reaches 1.0 at the moment the player should release the mining
     * key, accounting for the server confirmation delay.
     *
     * @param elapsedTicks    ticks since mining started
     * @param totalBreakTicks ticks to break (from {@link #calculateBreakTicks})
     * @param pingMs          current ping in milliseconds
     * @param tps             current server TPS (capped at 20.0)
     * @param marginMs        extra margin in ms; positive = the cue shows earlier
     * @return progress fraction; 1.0 means "release the mining key now"
     */
    public static double calculateProgressPercent(int elapsedTicks, int totalBreakTicks,
                                                  double pingMs, double tps, int marginMs) {
        if (totalBreakTicks <= 0 || totalBreakTicks == Integer.MAX_VALUE) return 0.0;

        double pingTickOffset = (tps * (pingMs - marginMs)) / 1000.0;
        double adjustedBreakTicks = totalBreakTicks - pingTickOffset;
        if (adjustedBreakTicks <= 0) return 1.0;

        return Math.min(1.0, elapsedTicks / adjustedBreakTicks);
    }
}
