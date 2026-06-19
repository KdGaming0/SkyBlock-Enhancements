/*
 * Part of the ping-offset mining feature inspired by PingOffsetMiner:
 * https://github.com/Revvilon/PingOffsetMiner
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.calc;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthEntry;
import net.minecraft.core.BlockPos;

/**
 * Immutable snapshot of a single mining operation.
 *
 * <p>Created when the player starts mining a block and never mutated.
 * All timing values are in server ticks. Uses packed long for BlockPos
 * to avoid object retention.
 */
public final class MiningSession {

    private final long blockPosPacked;
    private final BlockStrengthEntry blockEntry;
    private final int miningSpeed;
    private final int breakingPower;
    private final int totalBreakTicks;
    private final long startClientTick;
    private final double predictedBreakTick;

    public MiningSession(long blockPosPacked, BlockStrengthEntry blockEntry,
                         int miningSpeed, int breakingPower, int totalBreakTicks,
                         long startClientTick, double predictedBreakTick) {
        if (blockEntry != null && breakingPower < blockEntry.breakingPower()) {
            throw new IllegalArgumentException(
                    "Breaking power " + breakingPower + " insufficient for block requiring "
                            + blockEntry.breakingPower());
        }
        this.blockPosPacked = blockPosPacked;
        this.blockEntry = blockEntry;
        this.miningSpeed = miningSpeed;
        this.breakingPower = breakingPower;
        this.totalBreakTicks = totalBreakTicks;
        this.startClientTick = startClientTick;
        this.predictedBreakTick = predictedBreakTick;
    }

    public long getBlockPosPacked() {
        return blockPosPacked;
    }

    public BlockPos getBlockPos() {
        return BlockPos.of(blockPosPacked);
    }

    public BlockStrengthEntry getBlockEntry() {
        return blockEntry;
    }

    public int getMiningSpeed() {
        return miningSpeed;
    }

    public int getBreakingPower() {
        return breakingPower;
    }

    public int getTotalBreakTicks() {
        return totalBreakTicks;
    }

    public long getStartClientTick() {
        return startClientTick;
    }

    public double getPredictedBreakTick() {
        return predictedBreakTick;
    }
}
