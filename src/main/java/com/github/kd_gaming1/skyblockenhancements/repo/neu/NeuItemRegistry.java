package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader;
import net.minecraft.network.chat.Component;

/**
 * Thread-safe registry of all parsed NEU items. Populated by {@link NeuRepoDownloader}.
 *
 * <p>Also maintains a secondary index mapping NPC display names to their {@link NeuItem},
 * enabling O(1) lookups in the recipe-view fallback mixin.
 *
 * <p>Downstream systems (e.g. RRV integration) can register invalidation listeners via
 * {@link #addClearListener(Runnable)} to react to registry clears without introducing
 * a reverse dependency from this package into theirs.
 */
public final class NeuItemRegistry {

    private static final Map<String, NeuItem> ITEMS = new ConcurrentHashMap<>(5000);

    /**
     * Secondary index: NPC display name Component → NeuItem. Only populated for items
     * whose internal name ends with {@code "_NPC"}.
     */
    private static final Map<Component, NeuItem> NPC_BY_DISPLAY_NAME = new ConcurrentHashMap<>(256);

    /**
     * Listeners invoked after the registry is cleared. Used by downstream systems
     * (e.g. cache invalidation) without requiring this class to import their types.
     */
    private static final List<Runnable> CLEAR_LISTENERS = new ArrayList<>();

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
        ITEMS.put(internalName, item);
        if (internalName.endsWith("_NPC") && item.displayName != null) {
            NPC_BY_DISPLAY_NAME.put(Component.literal(item.displayName), item);
        }
    }

    // ── Lookups ──────────────────────────────────────────────────────────────────

    public static NeuItem get(String internalName) {
        return ITEMS.get(internalName);
    }

    public static NeuItem getNpcByDisplayName(Component displayName) {
        return NPC_BY_DISPLAY_NAME.get(displayName);
    }

    public static Map<String, NeuItem> getAll() {
        return Collections.unmodifiableMap(ITEMS);
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
}