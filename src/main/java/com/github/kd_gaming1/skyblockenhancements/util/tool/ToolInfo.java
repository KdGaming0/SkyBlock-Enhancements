package com.github.kd_gaming1.skyblockenhancements.util.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Immutable, zero-allocation snapshot of a detected tool.
 *
 * <p>Uses bitmasking and primitive arrays instead of Maps or Optionals.
 * Stat queries are O(1) and allocate absolutely zero memory.
 */
public final class ToolInfo {

    public static final ToolInfo NONE = new ToolInfo(ToolType.UNKNOWN, "", "", "", "", 0, new double[0], 0L);

    private final ToolType toolType;
    private final String skyblockId;
    private final String displayName;
    private final String uuid;
    private final String reforge;
    private final int toolLevel;

    // Fast-path optimizations
    private final double[] statValues;
    private final long presentStatsMask;

    ToolInfo(ToolType toolType, String skyblockId, String displayName, String uuid, String reforge, int toolLevel, double[] statValues, long presentStatsMask) {
        this.toolType = toolType;
        this.skyblockId = skyblockId;
        this.displayName = displayName;
        this.uuid = uuid;
        this.reforge = reforge;
        this.toolLevel = toolLevel;
        this.statValues = statValues;
        this.presentStatsMask = presentStatsMask;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    public ToolType getToolType() { return toolType; }
    public String getSkyblockId() { return skyblockId; }
    public String getDisplayName() { return displayName; }
    public boolean isUnknown() { return toolType == ToolType.UNKNOWN; }
    public boolean isKnown() { return !isUnknown(); }
    public String getUuid() { return uuid; }
    public String getReforge() { return reforge; }
    public int getToolLevel() { return toolLevel; }

    // ── High-Performance Data Access (Zero-Allocation) ────────────────────────

    /** Returns the raw bitmask of present stats. Fast existence checks. */
    public long getPresentStatsMask() {
        return presentStatsMask;
    }

    public boolean hasStat(ToolStat stat) {
        return stat.ordinal() < statValues.length && (presentStatsMask & (1L << stat.ordinal())) != 0;
    }

    public int getInt(ToolStat stat, int defaultValue) {
        int ord = stat.ordinal();
        if (ord < statValues.length && (presentStatsMask & (1L << ord)) != 0) {
            return (int) Math.round(statValues[ord]);
        }
        return defaultValue;
    }

    public double getDouble(ToolStat stat, double defaultValue) {
        int ord = stat.ordinal();
        if (ord < statValues.length && (presentStatsMask & (1L << ord)) != 0) {
            return statValues[ord];
        }
        return defaultValue;
    }

    // ── Safe API ──────────────────────────────────────────────────────────────

    public OptionalInt getInt(ToolStat stat) {
        int ord = stat.ordinal();
        if (ord < statValues.length && (presentStatsMask & (1L << ord)) != 0) {
            return OptionalInt.of((int) Math.round(statValues[ord]));
        }
        return OptionalInt.empty();
    }

    public OptionalDouble getDouble(ToolStat stat) {
        int ord = stat.ordinal();
        if (ord < statValues.length && (presentStatsMask & (1L << ord)) != 0) {
            return OptionalDouble.of(statValues[ord]);
        }
        return OptionalDouble.empty();
    }

    // ── Lazy Bulk Access ──────────────────────────────────────────────────────

    /**
     * Lazily reconstructs a map representing stats (used mostly for debugging).
     * Avoid using in hot rendering paths.
     */
    public Map<String, String> getAllStats() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ToolStat stat : ToolStat.VALUES) {
            if (hasStat(stat)) {
                double val = statValues[stat.ordinal()];
                // Strip trailing zeros for integers naturally
                String display = val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
                map.put(stat.key(), display);
            }
        }
        return map;
    }

    public int getStatCount() {
        return Long.bitCount(presentStatsMask);
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    public boolean isMiningTool() {
        return toolType == ToolType.PICKAXE
                || toolType == ToolType.DRILL
                || toolType == ToolType.GAUNTLET
                || toolType == ToolType.CHISEL;
    }

    public boolean isFarmingTool() {
        return toolType == ToolType.HOE
                || toolType == ToolType.FARMING_AXE
                || toolType == ToolType.WATERING_CAN;
    }
}