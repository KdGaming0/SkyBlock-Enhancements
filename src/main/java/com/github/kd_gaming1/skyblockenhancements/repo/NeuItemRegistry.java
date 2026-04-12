package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockInjectionCache;
import net.minecraft.network.chat.Component;

/**
 * Thread-safe registry of all parsed NEU items. Populated by {@link NeuRepoDownloader}.
 *
 * <p>Also maintains a secondary index mapping NPC display names to their {@link NeuItem},
 * enabling O(1) lookups in the recipe-view fallback mixin.
 */
public final class NeuItemRegistry {

    private static final Map<String, NeuItem> ITEMS = new ConcurrentHashMap<>(5000);

    /**
     * Secondary index: NPC display name Component → NeuItem. Only populated for items
     * whose internal name ends with {@code "_NPC"}.
     */
    private static final Map<Component, NeuItem> NPC_BY_DISPLAY_NAME = new ConcurrentHashMap<>(256);

    private static volatile boolean loaded = false;

    private NeuItemRegistry() {}

    public static void register(String internalName, NeuItem item) {
        ITEMS.put(internalName, item);
        if (internalName.endsWith("_NPC") && item.displayName != null) {
            NPC_BY_DISPLAY_NAME.put(Component.literal(item.displayName), item);
        }
    }

    public static NeuItem get(String internalName) {
        return ITEMS.get(internalName);
    }

    public static NeuItem getNpcByDisplayName(Component displayName) {
        return NPC_BY_DISPLAY_NAME.get(displayName);
    }

    public static Map<String, NeuItem> getAll() {
        return Collections.unmodifiableMap(ITEMS);
    }

    public static void clear() {
        ITEMS.clear();
        NPC_BY_DISPLAY_NAME.clear();
        loaded = false;
        invalidateRrvCache();
        invalidateCategoryIndex();
        NeuConstantsRegistry.clear();
    }

    /**
     * Guarded call to avoid a hard dependency on RRV classes when the mod isn't present.
     */
    private static void invalidateRrvCache() {
        try {
            if (RrvCompat.isRrvPresent()) {
                SkyblockInjectionCache.invalidate();
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    private static void invalidateCategoryIndex() {
        try {
            if (RrvCompat.isRrvPresent()) {
                SkyblockCategoryFilter.invalidateIndex();
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    public static void markLoaded() {
        loaded = true;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}