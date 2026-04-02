package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry of all parsed NEU items. Populated by {@link NeuRepoDownloader}. */
public final class NeuItemRegistry {

    private static final Map<String, NeuItem> ITEMS = new ConcurrentHashMap<>(5000);
    private static volatile boolean loaded = false;

    private NeuItemRegistry() {}

    public static void register(String internalName, NeuItem item) {
        ITEMS.put(internalName, item);
    }

    public static NeuItem get(String internalName) {
        return ITEMS.get(internalName);
    }

    public static Map<String, NeuItem> getAll() {
        return Collections.unmodifiableMap(ITEMS);
    }

    public static void clear() {
        ITEMS.clear();
        loaded = false;
    }

    public static void markLoaded() {
        loaded = true;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}