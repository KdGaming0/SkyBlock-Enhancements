package com.github.kd_gaming1.skyblockenhancements.repo.neu.mob;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory registry of {@link MobRenderDefinition}s keyed by NEU render-ref string
 * (e.g. {@code "@neurepo:mobs/alligator.json"}).
 *
 * <p>Populated during repo parsing by {@link com.github.kd_gaming1.skyblockenhancements.repo.RepoZipParser}.
 * Consumed at recipe-display time by the drops resolver.
 *
 * <p>Registry is a plain map, not a full loader — parse failures are silent and simply leave
 * the ref unresolved. Callers must treat an absent ref as "fall back to plain entity lookup".
 */
public final class MobRenderRegistry {

    private static final Map<String, MobRenderDefinition> ENTRIES = new ConcurrentHashMap<>(256);

    private MobRenderRegistry() {}

    public static void register(String ref, MobRenderDefinition def) {
        if (ref == null || def == null) return;
        ENTRIES.put(ref, def);
    }

    @Nullable
    public static MobRenderDefinition get(String ref) {
        return ref != null ? ENTRIES.get(ref) : null;
    }

    public static Map<String, MobRenderDefinition> view() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static int size() {
        return ENTRIES.size();
    }
}