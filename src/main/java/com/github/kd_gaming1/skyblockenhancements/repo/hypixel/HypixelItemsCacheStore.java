package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.RepoDiskCache;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry.HypixelUpgradeCost;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes the Hypixel items snapshot to disk. The on-disk format shares its schema
 * version with {@link RepoDiskCache}, so bumping the NEU cache version also invalidates the
 * Hypixel cache.
 */
public final class HypixelItemsCacheStore {

    /** Skip re-fetching when the cache file was written within this period. */
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1_000L;

    private static final Gson GSON = new GsonBuilder().create();

    private HypixelItemsCacheStore() {}

    /** Returns {@code true} when the cache file exists and is newer than the TTL. */
    public static boolean isFresh(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) return false;
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            return age < CACHE_TTL_MS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loads the cached snapshot, or {@code null} if the file is missing, malformed, or its
     * schema version is older than {@link RepoDiskCache#CACHE_VERSION}.
     */
    @Nullable
    public static HypixelItemsSnapshot tryLoad(Path cacheFile) {
        if (!Files.exists(cacheFile)) return null;

        try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            int version = readCacheVersion(root);
            if (version < RepoDiskCache.CACHE_VERSION) {
                LOGGER.info("Hypixel items cache version {} is outdated (current: {}), will re-fetch",
                        version, RepoDiskCache.CACHE_VERSION);
                return null;
            }

            return new HypixelItemsSnapshot(
                    Collections.unmodifiableMap(readBaseStats(root)),
                    Collections.unmodifiableMap(readTieredStats(root)),
                    Collections.unmodifiableMap(readUpgradeCosts(root)));
        } catch (Exception e) {
            LOGGER.warn("Failed to load Hypixel items cache: {}", e.getMessage());
            return null;
        }
    }

    /** Writes the snapshot atomically (per-file write). Swallows I/O errors with a warn log. */
    public static void save(Path cacheFile, HypixelItemsSnapshot data) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("cacheVersion", RepoDiskCache.CACHE_VERSION);
            root.add("baseStats",    writeBaseStats(data.baseStats()));
            root.add("tieredStats",  writeTieredStats(data.tieredStats()));
            root.add("upgradeCosts", writeUpgradeCosts(data.upgradeCosts()));

            Files.writeString(cacheFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to save Hypixel items cache: {}", e.getMessage());
        }
    }

    // ── Read helpers ────────────────────────────────────────────────────────────

    private static int readCacheVersion(JsonObject root) {
        if (!root.has("cacheVersion") || !root.get("cacheVersion").isJsonPrimitive()) return 0;
        try {
            return root.get("cacheVersion").getAsInt();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Hypixel items cache version");
            return 0;
        }
    }

    private static Map<String, Map<String, Integer>> readBaseStats(JsonObject root) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        if (!root.has("baseStats") || !root.get("baseStats").isJsonObject()) return out;

        for (var itemEntry : root.getAsJsonObject("baseStats").entrySet()) {
            if (!itemEntry.getValue().isJsonObject()) continue;
            Map<String, Integer> stats = new HashMap<>();
            for (var statEntry : itemEntry.getValue().getAsJsonObject().entrySet()) {
                if (!statEntry.getValue().isJsonPrimitive()) continue;
                stats.put(statEntry.getKey(), statEntry.getValue().getAsInt());
            }
            if (!stats.isEmpty()) out.put(itemEntry.getKey(), Collections.unmodifiableMap(stats));
        }
        return out;
    }

    private static Map<String, Map<String, int[]>> readTieredStats(JsonObject root) {
        Map<String, Map<String, int[]>> out = new HashMap<>();
        if (!root.has("tieredStats") || !root.get("tieredStats").isJsonObject()) return out;

        for (var itemEntry : root.getAsJsonObject("tieredStats").entrySet()) {
            if (!itemEntry.getValue().isJsonObject()) continue;
            Map<String, int[]> stats = new HashMap<>();
            for (var statEntry : itemEntry.getValue().getAsJsonObject().entrySet()) {
                if (!statEntry.getValue().isJsonArray()) continue;
                JsonArray arr = statEntry.getValue().getAsJsonArray();
                int[] values = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) values[i] = arr.get(i).getAsInt();
                stats.put(statEntry.getKey(), values);
            }
            if (!stats.isEmpty()) out.put(itemEntry.getKey(), Collections.unmodifiableMap(stats));
        }
        return out;
    }

    private static Map<String, List<List<HypixelUpgradeCost>>> readUpgradeCosts(JsonObject root) {
        Map<String, List<List<HypixelUpgradeCost>>> out = new HashMap<>();
        if (!root.has("upgradeCosts") || !root.get("upgradeCosts").isJsonObject()) return out;

        for (var itemEntry : root.getAsJsonObject("upgradeCosts").entrySet()) {
            if (!itemEntry.getValue().isJsonArray()) continue;
            List<List<HypixelUpgradeCost>> perStar = new ArrayList<>();
            for (JsonElement starEl : itemEntry.getValue().getAsJsonArray()) {
                if (!starEl.isJsonArray()) { perStar.add(List.of()); continue; }
                List<HypixelUpgradeCost> starCosts = new ArrayList<>();
                for (JsonElement costEl : starEl.getAsJsonArray()) {
                    HypixelUpgradeCost cost = HypixelItemsFetcher.parseCost(costEl);
                    if (cost != null) starCosts.add(cost);
                }
                perStar.add(Collections.unmodifiableList(starCosts));
            }
            out.put(itemEntry.getKey(), Collections.unmodifiableList(perStar));
        }
        return out;
    }

    // ── Write helpers ───────────────────────────────────────────────────────────

    private static JsonObject writeBaseStats(Map<String, Map<String, Integer>> data) {
        JsonObject obj = new JsonObject();
        for (var itemEntry : data.entrySet()) {
            JsonObject stats = new JsonObject();
            for (var statEntry : itemEntry.getValue().entrySet()) {
                stats.addProperty(statEntry.getKey(), statEntry.getValue());
            }
            obj.add(itemEntry.getKey(), stats);
        }
        return obj;
    }

    private static JsonObject writeTieredStats(Map<String, Map<String, int[]>> data) {
        JsonObject obj = new JsonObject();
        for (var itemEntry : data.entrySet()) {
            JsonObject stats = new JsonObject();
            for (var statEntry : itemEntry.getValue().entrySet()) {
                JsonArray arr = new JsonArray();
                for (int v : statEntry.getValue()) arr.add(v);
                stats.add(statEntry.getKey(), arr);
            }
            obj.add(itemEntry.getKey(), stats);
        }
        return obj;
    }

    private static JsonObject writeUpgradeCosts(Map<String, List<List<HypixelUpgradeCost>>> data) {
        JsonObject obj = new JsonObject();
        for (var itemEntry : data.entrySet()) {
            JsonArray stars = new JsonArray();
            for (List<HypixelUpgradeCost> starCosts : itemEntry.getValue()) {
                JsonArray starArr = new JsonArray();
                for (HypixelUpgradeCost cost : starCosts) {
                    JsonObject c = new JsonObject();
                    c.addProperty("type", cost.type());
                    if (cost.essenceType() != null) c.addProperty("essence_type", cost.essenceType());
                    if (cost.itemId() != null)      c.addProperty("item_id", cost.itemId());
                    c.addProperty("amount", cost.amount());
                    starArr.add(c);
                }
                stars.add(starArr);
            }
            obj.add(itemEntry.getKey(), stars);
        }
        return obj;
    }
}