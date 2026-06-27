/*
 * Part of the ping-offset mining feature adapted from Revvilon/PingOffsetMiner,
 * CC0-1.0: https://github.com/Revvilon/PingOffsetMiner
 * See THIRD_PARTY_LICENSES.md for the full attribution.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.detect;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthRegistry.Entry;
import com.github.kd_gaming1.skyblockenhancements.util.tool.HeldItemTracker;
import com.github.kd_gaming1.skyblockenhancements.util.tool.ToolInfo;
import com.github.kd_gaming1.skyblockenhancements.util.tool.ToolStat;

/** Checks whether the held tool is a mining tool and can break a given block. */
public final class MiningToolChecker {

    private MiningToolChecker() {}

    public static boolean isHoldingMiningTool() {
        return HeldItemTracker.getToolInfo().isMiningTool();
    }

    /** True if the held tool is a mining tool with enough breaking power for the block. */
    public static boolean canMine(Entry entry) {
        ToolInfo info = HeldItemTracker.getToolInfo();
        return info.isMiningTool()
                && info.getInt(ToolStat.BREAKING_POWER, 0) >= entry.breakingPower();
    }
}
