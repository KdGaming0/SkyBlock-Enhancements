package com.github.kd_gaming1.skyblockenhancements.util.tab;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.*;

/**
 * Public façade for all SkyBlock stats extracted from the tab list.
 *
 * <p>This is the single source of truth every feature should query. All getters
 * are O(1) map look-ups and produce no garbage on repeated calls. The internal
 * cache is updated automatically by {@link TabListMonitor}.
 *
 * <p>Two Fabric events are provided:
 * <ul>
 *   <li>{@link #STAT_CHANGED} — fired once per stat whose value changed.</li>
 *   <li>{@link #TAB_REFRESHED} — fired once after every full re-parse.</li>
 * </ul>
 */
public final class SkyblockStats {

    private SkyblockStats() {}

    // ── Mutable state (client-thread only) ────────────────────────────────────

    private static final Map<String, String> stats = new HashMap<>(32);
    private static boolean ready = false;
    private static long lastUpdateTick = 0;
    private static int parsedStatCount = 0;

    // ── Events ────────────────────────────────────────────────────────────────

    /** Fired once for each stat whose value differs from the previous parse. */
    public static final Event<StatChangeCallback> STAT_CHANGED = EventFactory.createArrayBacked(
            StatChangeCallback.class,
            callbacks -> (key, oldValue, newValue) -> {
                for (StatChangeCallback cb : callbacks) cb.onStatChanged(key, oldValue, newValue);
            }
    );

    @FunctionalInterface
    public interface StatChangeCallback {
        /**
         * @param key       the stat's canonical primary key
         * @param oldValue  previous raw value, or {@code null} if the stat is new
         * @param newValue  current raw value, or {@code null} if the stat was removed
         */
        void onStatChanged(String key, String oldValue, String newValue);
    }

    /** Fired once after the tab list is re-parsed, regardless of whether values changed. */
    public static final Event<TabRefreshedCallback> TAB_REFRESHED = EventFactory.createArrayBacked(
            TabRefreshedCallback.class,
            callbacks -> allStats -> {
                for (TabRefreshedCallback cb : callbacks) cb.onTabRefreshed(allStats);
            }
    );

    @FunctionalInterface
    public interface TabRefreshedCallback {
        /** @param allStats unmodifiable snapshot of every parsed stat */
        void onTabRefreshed(Map<String, String> allStats);
    }

    // ── Mining Speed helpers (primary use-case) ───────────────────────────────

    /** @return the current mining speed if present in the tab list */
    public static OptionalInt getMiningSpeed() {
        return getInt("mining_speed");
    }

    /** @return mining speed, or {@code 0} when unavailable */
    public static int getMiningSpeedOrZero() {
        return getMiningSpeed().orElse(0);
    }

    /** @return {@code true} if the tab list currently shows a mining speed value */
    public static boolean hasMiningSpeed() {
        return hasStat("mining_speed");
    }

    // ── Generic typed access ──────────────────────────────────────────────────

    public static OptionalInt getInt(String key) {
        String raw = stats.get(key);
        return raw != null ? TabStatParser.parseInt(raw) : OptionalInt.empty();
    }

    public static OptionalLong getLong(String key) {
        String raw = stats.get(key);
        return raw != null ? TabStatParser.parseLong(raw) : OptionalLong.empty();
    }

    public static OptionalDouble getDouble(String key) {
        String raw = stats.get(key);
        return raw != null ? TabStatParser.parseDouble(raw) : OptionalDouble.empty();
    }

    public static Optional<String> getString(String key) {
        return Optional.ofNullable(stats.get(key));
    }

    public static boolean hasStat(String key) {
        return stats.containsKey(key);
    }

    // ── Bulk access ───────────────────────────────────────────────────────────

    /** @return unmodifiable snapshot of all parsed stats (primaryKey → rawValue) */
    public static Map<String, String> getAllStats() {
        return Collections.unmodifiableMap(new HashMap<>(stats));
    }

    /** @return unmodifiable set of every stat key currently available */
    public static Set<String> getAvailableStatKeys() {
        return Collections.unmodifiableSet(new HashSet<>(stats.keySet()));
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    /** @return {@code true} after at least one successful parse has occurred */
    public static boolean isReady() {
        return ready;
    }

    /** @return the client tick at which the cache was last updated */
    public static long getLastUpdateTick() {
        return lastUpdateTick;
    }

    /** @return how many distinct stats were parsed on the last refresh */
    public static int getParsedStatCount() {
        return parsedStatCount;
    }

    // ── Internal: called by TabListMonitor ────────────────────────────────────

    static void updateFromParseResult(TabStatParser.ParseResult result) {
        Map<String, String> incoming = result.getAllStats();

        if (!ready) {
            stats.putAll(incoming);
            ready = true;
            parsedStatCount = incoming.size();
            lastUpdateTick = net.minecraft.client.Minecraft.getInstance().level != null
                    ? net.minecraft.client.Minecraft.getInstance().level.getGameTime()
                    : 0;
            TAB_REFRESHED.invoker().onTabRefreshed(getAllStats());
            return;
        }

        // Detect changes and removals.
        for (Map.Entry<String, String> e : incoming.entrySet()) {
            String key = e.getKey();
            String newVal = e.getValue();
            String oldVal = stats.put(key, newVal);
            if (oldVal == null || !oldVal.equals(newVal)) {
                STAT_CHANGED.invoker().onStatChanged(key, oldVal, newVal);
            }
        }

        Iterator<Map.Entry<String, String>> it = stats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (!incoming.containsKey(e.getKey())) {
                STAT_CHANGED.invoker().onStatChanged(e.getKey(), e.getValue(), null);
                it.remove();
            }
        }

        parsedStatCount = stats.size();
        lastUpdateTick = net.minecraft.client.Minecraft.getInstance().level != null
                ? net.minecraft.client.Minecraft.getInstance().level.getGameTime()
                : 0;
        TAB_REFRESHED.invoker().onTabRefreshed(getAllStats());
    }

    static void markStale() {
        ready = false;
        stats.clear();
        parsedStatCount = 0;
    }
}
