package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe registry of all garden mutation layouts.
 *
 * <p>Loaded from the built-in resource {@code assets/skyblock_enhancements/skyblock_data/mutations.json}.
 * Cleared whenever the NEU repo is refreshed via a {@code NeuItemRegistry} clear listener.
 */
public final class GardenMutationRegistry {

    private static final String RESOURCE_PATH =
            "assets/skyblock_enhancements/skyblock_data/mutations.json";

    /** Volatile so reads are thread-safe without synchronization. */
    private static volatile Map<String, GardenMutationLayout> layouts = Map.of();

    private GardenMutationRegistry() {}

    /**
     * Loads mutation data from the built-in JSON resource.
     * Safe to call multiple times; replaces the previous snapshot.
     */
    public static void load() {
        Map<String, GardenMutationLayout> parsed = new ConcurrentHashMap<>(64);

        try (InputStream in = GardenMutationRegistry.class.getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("Garden mutation resource not found: {}", RESOURCE_PATH);
                layouts = Map.of();
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            GardenMutationLayout.setGlobalCropSizes(
                    GardenMutationLayout.parseCropSizes(root.getAsJsonObject("cropSizes"))
            );
            JsonObject mutations = root.getAsJsonObject("mutations");
            if (mutations == null) {
                LOGGER.warn("Garden mutation JSON missing 'mutations' object");
                layouts = Map.of();
                return;
            }

            for (String key : mutations.keySet()) {
                try {
                    JsonObject obj = mutations.getAsJsonObject(key);
                    parsed.put(key, GardenMutationLayout.fromJson(key, obj));
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse mutation '{}': {}", key, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load garden mutations", e);
            layouts = Map.of();
            return;
        }

        layouts = Collections.unmodifiableMap(parsed);
        LOGGER.info("Loaded {} garden mutation layouts", parsed.size());
    }

    /** Returns the layout for the given mutation ID, or {@code null}. */
    @Nullable
    public static GardenMutationLayout get(String mutationId) {
        return layouts.get(mutationId);
    }

    /** Returns all loaded layouts. */
    public static Collection<GardenMutationLayout> all() {
        return layouts.values();
    }

    /** Returns {@code true} if any layouts are loaded. */
    public static boolean isLoaded() {
        return !layouts.isEmpty();
    }

    /** Clears the registry. Called automatically on NEU repo reload. */
    public static void clear() {
        layouts = Map.of();
    }
}
