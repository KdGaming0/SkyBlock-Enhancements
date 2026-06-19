/*
 * Part of the ping-offset mining feature inspired by PingOffsetMiner:
 * https://github.com/Revvilon/PingOffsetMiner
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.detect;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthEntry;
import com.github.kd_gaming1.skyblockenhancements.util.tool.HeldItemTracker;
import com.github.kd_gaming1.skyblockenhancements.util.tool.ToolInfo;
import com.github.kd_gaming1.skyblockenhancements.util.tool.ToolStat;

/**
 * Validates that the held tool can mine a target block.
 *
 * <p>Checks two conditions:
 * <ol>
 *   <li>The held item is a mining tool (pickaxe, drill, gauntlet, chisel)</li>
 *   <li>The tool's breaking power >= the block's required breaking power</li>
 * </ol>
 */
public final class MiningToolChecker {

    private MiningToolChecker() {}

    /** Returns true if the player is holding any mining tool. */
    public static boolean isHoldingMiningTool() {
        ToolInfo info = HeldItemTracker.getToolInfo();
        return info.isMiningTool();
    }

    /** Returns the breaking power of the held tool, or 0 if none. */
    public static int getHeldBreakingPower() {
        return HeldItemTracker.getToolInfo().getInt(ToolStat.BREAKING_POWER, 0);
    }

    /** Returns true if the held tool can mine the given block. */
    public static boolean canMine(BlockStrengthEntry entry) {
        ToolInfo info = HeldItemTracker.getToolInfo();
        if (!info.isMiningTool()) return false;
        int toolBP = info.getInt(ToolStat.BREAKING_POWER, 0);
        return toolBP >= entry.breakingPower();
    }

    /** Returns true if the held tool has sufficient BP for the given requirement. */
    public static boolean hasBreakingPower(int requiredBP) {
        ToolInfo info = HeldItemTracker.getToolInfo();
        if (!info.isMiningTool()) return false;
        return info.getInt(ToolStat.BREAKING_POWER, 0) >= requiredBP;
    }
}
