/*
 * This feature is inspired by Revvilon/PingOffsetMiner:
 * https://github.com/Revvilon/PingOffsetMiner
 *
 * Some mining-overlay and ping-offset timing code were adapted from that project.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.calc;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthEntry;

/**
 * Pure static math engine for mining break-time calculations.
 *
 * <p>All methods are side-effect-free, allocation-free, and easily unit tested.
 * No Minecraft classes are referenced — inputs are primitives and data objects only.
 */
public final class BreakTimeCalculator {

    private BreakTimeCalculator() {}

    /**
     * Calculates the number of ticks required to break a block.
     *
     * <p>Formula: {@code round(blockStrength * 30.0 / miningSpeed)}
     * Soft cap: minimum 4 ticks (blocks cannot break faster than 4 server ticks)
     *
     * <p>Uses {@link Math#round} (not ceil) to match the Hypixel server-side
     * calculation as documented on the
     * <a href="https://hypixelskyblock.minecraft.wiki/w/Mining_Speed">Mining Speed wiki</a>
     * and the reference implementation.
     *
     * @param blockStrength the block's strength value (from BlockStrengthEntry)
     * @param miningSpeed   the player's current mining speed (from SkyblockStats)
     * @return break time in ticks, or {@link Integer#MAX_VALUE} if miningSpeed <= 0
     */
    public static int calculateBreakTicks(double blockStrength, int miningSpeed) {
        if (miningSpeed <= 0) return Integer.MAX_VALUE;
        double raw = (blockStrength * 30.0) / miningSpeed;
        return (int) Math.max(4.0, Math.round(raw));
    }

    /**
     * Convenience overload that extracts strength from a BlockStrengthEntry.
     */
    public static int calculateBreakTicks(BlockStrengthEntry entry, int miningSpeed) {
        return calculateBreakTicks(entry.strength(), miningSpeed);
    }

    /**
     * Calculates the break time in seconds (for display purposes).
     */
    public static double calculateBreakSeconds(double blockStrength, int miningSpeed) {
        return calculateBreakTicks(blockStrength, miningSpeed) / 20.0;
    }

    /**
     * Calculates mining progress as a fraction [0.0, 1.0+].
     *
     * <p>This is the KEY function for ping offset mining. It adjusts the
     * effective break time by subtracting the ping offset, so the visual
     * indicator reaches 1.0 at the moment the player should release the
     * mining key (accounting for server confirmation delay).
     *
     * @param elapsedTicks    ticks since mining started
     * @param totalBreakTicks total ticks to break (from calculateBreakTicks)
     * @param pingMs          current ping in milliseconds
     * @param tps             current server TPS (capped at 20.0)
     * @return progress fraction; 1.0 means "release mining key now"
     */
    public static double calculateProgressPercent(int elapsedTicks, int totalBreakTicks,
                                                  double pingMs, double tps) {
        return calculateProgressPercent(elapsedTicks, totalBreakTicks, pingMs, tps, 0);
    }

    /**
     * Calculates mining progress as a fraction [0.0, 1.0+].
     *
     * <p>This is the KEY function for ping offset mining. It adjusts the
     * effective break time by subtracting the ping offset and optional margin,
     * so the visual indicator reaches 1.0 at the moment the player should
     * release the mining key (accounting for server confirmation delay).
     *
     * @param elapsedTicks    ticks since mining started
     * @param totalBreakTicks total ticks to break (from calculateBreakTicks)
     * @param pingMs          current ping in milliseconds
     * @param tps             current server TPS (capped at 20.0)
     * @param marginMs        additional margin in ms (from config); positive =
     *                        green shows earlier. Zero for exact ping-only.
     * @return progress fraction; 1.0 means "release mining key now"
     */
    public static double calculateProgressPercent(int elapsedTicks, int totalBreakTicks,
                                                  double pingMs, double tps, int marginMs) {
        if (totalBreakTicks <= 0 || totalBreakTicks == Integer.MAX_VALUE) return 0.0;

        double pingTickOffset = (tps * (pingMs - marginMs)) / 1000.0;
        double adjustedBreakTicks = totalBreakTicks - pingTickOffset;
        if (adjustedBreakTicks <= 0) return 1.0;

        return Math.min(1.0, elapsedTicks / adjustedBreakTicks);
    }

    /**
     * Calculates the mining speed needed to soft-cap (4-tick floor) a block.
     * Formula: (20/3) * blockStrength
     */
    public static double calculateSoftcapSpeed(double blockStrength) {
        return (20.0 / 3.0) * blockStrength;
    }

    /**
     * Calculates the mining speed needed to instant-mine a block.
     * Normal blocks: 30 * strength. Ores: 60 * strength.
     */
    public static double calculateInstantMineSpeed(double blockStrength, boolean isOre) {
        return isOre ? blockStrength * 60.0 : blockStrength * 30.0;
    }

    /**
     * Returns true if the player can instant-mine the block at their current speed.
     */
    public static boolean canInstantMine(double blockStrength, boolean isOre, int miningSpeed) {
        return miningSpeed >= calculateInstantMineSpeed(blockStrength, isOre);
    }

    /**
     * Returns true if the block is at the soft-cap floor (4 ticks).
     */
    public static boolean isAtSoftcap(double blockStrength, int miningSpeed) {
        if (miningSpeed <= 0) return false;
        double raw = (blockStrength * 30.0) / miningSpeed;
        return Math.round(raw) <= 4.0;
    }
}
