package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Stores data from the Hypixel Public API ({@code /v2/resources/skyblock/items}).
 *
 * <p>Sole source of truth for essence upgrade recipes:
 * <ul>
 *   <li>{@link #getBaseStats}    — base stat snapshot (API {@code stats}). Used for the +2%/star fallback.</li>
 *   <li>{@link #getTieredStats}  — authoritative per-star stat values (API {@code tiered_stats}).</li>
 *   <li>{@link #getUpgradeCosts} — per-star costs (API {@code upgrade_costs}).</li>
 * </ul>
 *
 * <p>All public accessors are thread-safe via volatile publish-on-write.
 */
public final class HypixelItemsRegistry {

    /** Item ID → {@code STAT_NAME (UPPER) → base value}. Populated from API {@code stats}. */
    private static volatile Map<String, Map<String, Integer>> baseStatsMap = Map.of();

    /**
     * Item ID → tiered stats.
     * Key = API stat name (e.g. {@code "DAMAGE"}),
     * value = array where {@code [i]} is the stat value at ★(i+1) (0-indexed).
     */
    private static volatile Map<String, Map<String, int[]>> tieredStatsMap = Map.of();

    /**
     * Item ID → upgrade costs per star (0-indexed: index 0 = costs to reach ★1).
     * Each inner list contains all cost entries (ESSENCE and/or ITEM) for that star.
     */
    private static volatile Map<String, List<List<HypixelUpgradeCost>>> upgradeCostsMap = Map.of();

    private HypixelItemsRegistry() {}

    // ── Loading ────────────────────────────────────────────────────────────────

    /**
     * Replaces all stored data atomically. All maps must already be immutable.
     * Called from {@link HypixelItemsDownloader} after a successful fetch or cache load.
     */
    public static void load(
            Map<String, Map<String, Integer>> baseStats,
            Map<String, Map<String, int[]>> tieredStats,
            Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts) {
        baseStatsMap    = baseStats;
        tieredStatsMap  = tieredStats;
        upgradeCostsMap = upgradeCosts;
    }

    /** Drops all loaded data. Called when the mod resets (repo invalidation). */
    public static void clear() {
        baseStatsMap    = Map.of();
        tieredStatsMap  = Map.of();
        upgradeCostsMap = Map.of();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    /**
     * Returns the flat base stat map for {@code itemId} (API {@code stats}), or {@code null}
     * if the API has no base stat data. Keys are normalised to UPPER_SNAKE.
     */
    @Nullable
    public static Map<String, Integer> getBaseStats(String itemId) {
        return baseStatsMap.get(itemId);
    }

    /**
     * Returns the per-star stat map for {@code itemId}, or {@code null} if the API has no
     * tiered stat data for it.
     */
    @Nullable
    public static Map<String, int[]> getTieredStats(String itemId) {
        return tieredStatsMap.get(itemId);
    }

    /**
     * Returns the upgrade cost list for {@code itemId}, or {@code null} if unavailable.
     * Outer index = star index (0-indexed). Each inner list = all cost entries for that star.
     */
    @Nullable
    public static List<List<HypixelUpgradeCost>> getUpgradeCosts(String itemId) {
        return upgradeCostsMap.get(itemId);
    }

    /** Returns all item IDs that have upgrade cost data (unmodifiable keyset view). */
    public static Set<String> getAllUpgradeItemIds() {
        return upgradeCostsMap.keySet();
    }

    /** Returns {@code true} if the registry has been populated from the API or cache. */
    public static boolean isLoaded() {
        return !upgradeCostsMap.isEmpty();
    }

    // ── Data record ────────────────────────────────────────────────────────────

    /**
     * A single cost entry within one star's upgrade. {@code type} is {@code "ESSENCE"} or
     * {@code "ITEM"}. Only one of {@code essenceType} / {@code itemId} will be non-null.
     */
    public record HypixelUpgradeCost(
            String type,
            @Nullable String essenceType,
            @Nullable String itemId,
            int amount) {

        /** Converts to the {@code "INTERNAL_ID:count"} slot-ref format. */
        public String toSlotRef() {
            return switch (type) {
                case "ESSENCE" -> {
                    assert essenceType != null;
                    yield "ESSENCE_" + essenceType.toUpperCase() + ":" + amount;
                }
                case "ITEM"    -> itemId + ":" + amount;
                default        -> "";
            };
        }

        public boolean isEssence() {
            return "ESSENCE".equals(type);
        }
    }
}