package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Computes per-star stat snapshots for essence upgrades from Hypixel API data.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>If the item has {@code tiered_stats}, use the array — it is authoritative.
 *       {@code tiered_stats[STAT][star - 1]} is the value after {@code star} stars.</li>
 *   <li>Otherwise, scale {@code base × (1 + 0.02 × star)} — the Hypixel default for
 *       essence-star upgrades on items without custom tier tables.</li>
 *   <li>If neither data source exists, returns {@code null}.</li>
 * </ol>
 *
 * <p>Star 0 means the item in its unmodified, pre-upgrade form: base stats (case 2) or
 * the NEU lore values (case 1, which we don't own — callers display the repo base).
 */
public final class EssenceStatResolver {

    /** Scaling factor per essence star for items without explicit tiered_stats. */
    private static final double PERCENT_PER_STAR = 0.02;

    private EssenceStatResolver() {}

    /**
     * Resolves the full stat snapshot for {@code itemId} at {@code star} (0-indexed for base,
     * 1..N for upgraded tiers). The returned map preserves insertion order for consistent lore.
     *
     * @return insertion-ordered map {@code EssenceStat → value}, or {@code null} if the item
     *         has no stat data at all (rare — callers should degrade gracefully).
     */
    @Nullable
    public static Map<EssenceStat, Integer> resolve(String itemId, int star) {
        if (itemId == null || star < 0) return null;

        Map<String, int[]> tiered = HypixelItemsRegistry.getTieredStats(itemId);
        if (tiered != null && !tiered.isEmpty()) {
            return resolveFromTiered(tiered, star);
        }

        Map<String, Integer> base = HypixelItemsRegistry.getBaseStats(itemId);
        if (base != null && !base.isEmpty()) {
            return resolveFromBase(base, star);
        }

        return null;
    }

    // ── Tiered path (authoritative values) ────────────────────────────────────

    /**
     * For {@code star == 0}, returns no snapshot — tiered-stat items expose their
     * "pre-upgrade" state only through the NEU repo lore, which we don't overwrite here.
     * Callers should not build a starred output for star 0 anyway; the before-snapshot
     * for star 1 is simply "base repo lore".
     */
    @Nullable
    private static Map<EssenceStat, Integer> resolveFromTiered(Map<String, int[]> tiered, int star) {
        if (star <= 0) return null;

        Map<EssenceStat, Integer> out = new LinkedHashMap<>();
        for (var entry : tiered.entrySet()) {
            EssenceStat stat = EssenceStat.fromKey(entry.getKey());
            if (stat == null) continue; // unknown stat — skip silently

            int[] values = entry.getValue();
            int idx = Math.min(star - 1, values.length - 1); // clamp to last known tier
            out.put(stat, values[idx]);
        }
        return out.isEmpty() ? null : Collections.unmodifiableMap(out);
    }

    // ── Base + 2%/star path ───────────────────────────────────────────────────

    private static Map<EssenceStat, Integer> resolveFromBase(Map<String, Integer> base, int star) {
        Map<EssenceStat, Integer> out = new LinkedHashMap<>();
        for (var entry : base.entrySet()) {
            EssenceStat stat = EssenceStat.fromKey(entry.getKey());
            if (stat == null) continue;

            int baseValue = entry.getValue();
            int scaled = scale(baseValue, star);
            out.put(stat, scaled);
        }
        return out.isEmpty() ? null : Collections.unmodifiableMap(out);
    }

    /** {@code round(base × (1 + 0.02 × star))}. Rounds half-up to match in-game display. */
    static int scale(int base, int star) {
        if (star <= 0) return base;
        double multiplier = 1.0 + PERCENT_PER_STAR * star;
        return (int) Math.round(base * multiplier);
    }
}