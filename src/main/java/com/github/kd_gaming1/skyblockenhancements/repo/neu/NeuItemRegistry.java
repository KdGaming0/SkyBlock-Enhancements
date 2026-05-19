package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry of all parsed NEU items. Populated by {@link com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader}.
 *
 * <p>Also maintains a secondary index mapping NPC display names to their {@link NeuItem},
 * enabling O(1) lookups in the recipe-view fallback resolver.
 *
 * <p>Downstream systems (e.g. RRV integration) can register invalidation listeners via
 * {@link #addClearListener(Runnable)} to react to registry clears without introducing
 * a reverse dependency from this package into theirs.
 */
public final class NeuItemRegistry {

    private static final Map<String, NeuItem> ITEMS = new ConcurrentHashMap<>(6000);

    /**
     * Secondary index: NPC display name → NeuItem. Only populated for items
     * whose internal name ends with {@code "_NPC"}.
     */
    private static final Map<String, NeuItem> NPC_BY_DISPLAY_NAME = new ConcurrentHashMap<>(256);

    /**
     * Listeners invoked after the registry is cleared. A {@link CopyOnWriteArrayList}
     * guarantees thread-safe registration and iteration without explicit synchronisation.
     */
    private static final List<Runnable> CLEAR_LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile boolean loaded = false;

    private NeuItemRegistry() {}

    // ── Listener registration ────────────────────────────────────────────────────

    /**
     * Registers a callback that fires every time {@link #clear()} is called.
     * Listeners are called in registration order on the same thread as {@code clear()}.
     */
    public static void addClearListener(Runnable listener) {
        CLEAR_LISTENERS.add(listener);
    }

    // ── Registration ─────────────────────────────────────────────────────────────

    public static void register(String internalName, NeuItem item) {
        NeuItem previous = ITEMS.put(internalName, item);
        if (previous != null) {
            // In a healthy repo this should not happen, but if it does we want to know.
            com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER
                    .warn("Duplicate NEU item '{}' — latest entry wins", internalName);
        }
        if (internalName.endsWith("_NPC") && item.displayName != null) {
            NPC_BY_DISPLAY_NAME.put(item.displayName, item);
        }
    }

    // ── Lookups ──────────────────────────────────────────────────────────────────

    public static NeuItem get(String internalName) {
        return ITEMS.get(internalName);
    }

    public static NeuItem getNpcByDisplayName(String displayName) {
        return displayName != null ? NPC_BY_DISPLAY_NAME.get(displayName) : null;
    }

    /**
     * Returns a snapshot copy of the current items map. The snapshot is immutable and
     * will not reflect subsequent registry changes, avoiding concurrent-modification issues
     * for callers that iterate over a long period.
     */
    public static Map<String, NeuItem> getAll() {
        return Map.copyOf(ITEMS);
    }

    /** Returns a snapshot list of all registered items without copying the key set. */
    public static List<NeuItem> getAllValues() {
        return List.copyOf(ITEMS.values());
    }

    public static int size() {
        return ITEMS.size();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    public static void clear() {
        ITEMS.clear();
        NPC_BY_DISPLAY_NAME.clear();
        loaded = false;
        NeuConstantsRegistry.clear();
        CLEAR_LISTENERS.forEach(Runnable::run);
    }

    public static void markLoaded() {
        loaded = true;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Nulls out the raw recipe JSON on every registered item to free memory.
     * Safe to call once recipe generation is complete.
     */
    public static void trimRecipes() {
        for (NeuItem item : ITEMS.values()) {
            item.trimRecipes();
        }
    }
}
