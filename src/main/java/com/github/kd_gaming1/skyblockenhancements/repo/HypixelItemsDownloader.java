package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.HypixelItemsRegistry.HypixelUpgradeCost;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Fetches and disk-caches the Hypixel SkyBlock items resource endpoint.
 *
 * <p>The endpoint ({@code /v2/resources/skyblock/items}) is public and requires no API key.
 * It provides {@code stats}, {@code tiered_stats}, and {@code upgrade_costs} per item.
 *
 * <h3>Cache strategy</h3>
 * <ul>
 *   <li>When the NEU repo is re-downloaded (fresh data), pass {@code forceRefresh = true}.</li>
 *   <li>Otherwise a 24-hour TTL prevents unnecessary re-fetches.</li>
 * </ul>
 */
public final class HypixelItemsDownloader {

    private static final String ITEMS_URL =
            "https://api.hypixel.net/v2/resources/skyblock/items";

    /** Skip re-fetching if the cache file was written within this period. */
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1_000L;

    private static final Gson GSON = new GsonBuilder().create();

    private HypixelItemsDownloader() {}

    // ── Public entry point ─────────────────────────────────────────────────────

    public static void fetchAndCache(HttpClient http, Path cacheFile, boolean forceRefresh) {
        if (!forceRefresh && isCacheFresh(cacheFile)) {
            if (loadFromCache(cacheFile)) {
                LOGGER.info("Loaded Hypixel items from cache ({} items with upgrade costs)",
                        HypixelItemsRegistry.getAllUpgradeItemIds().size());
                return;
            }
            LOGGER.info("Hypixel items cache invalid — re-fetching");
        }

        ParsedApiData data = fetch(http);
        if (data == null) {
            if (loadFromCache(cacheFile)) {
                LOGGER.warn("Hypixel API fetch failed; using stale cache for essence upgrades");
            } else {
                LOGGER.warn("Hypixel API unavailable and no cache — essence stat upgrades won't show");
            }
            return;
        }

        HypixelItemsRegistry.load(data.baseStats(), data.tieredStats(), data.upgradeCosts());
        saveToCache(data, cacheFile);
        LOGGER.info("Fetched Hypixel items API: {} with base stats, {} with tiered_stats, {} with upgrade_costs",
                data.baseStats().size(), data.tieredStats().size(), data.upgradeCosts().size());
    }

    // ── HTTP fetch ─────────────────────────────────────────────────────────────

    @Nullable
    private static ParsedApiData fetch(HttpClient http) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ITEMS_URL))
                    .header("User-Agent", "SkyblockEnhancements")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Hypixel items API returned HTTP {}", response.statusCode());
                return null;
            }

            try (InputStream body = new BufferedInputStream(response.body(), 1 << 16)) {
                JsonObject root = GSON.fromJson(
                        new InputStreamReader(body, StandardCharsets.UTF_8), JsonObject.class);
                return parseApiResponse(root);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch Hypixel items API: {}", e.getMessage());
            return null;
        }
    }

    // ── JSON parsing ───────────────────────────────────────────────────────────

    @Nullable
    private static ParsedApiData parseApiResponse(JsonObject root) {
        if (!root.has("items") || !root.get("items").isJsonArray()) {
            LOGGER.warn("Hypixel items response missing 'items' array");
            return null;
        }

        Map<String, Map<String, Integer>>          baseStats    = new HashMap<>(512);
        Map<String, Map<String, int[]>>            tieredStats  = new HashMap<>(128);
        Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts = new HashMap<>(512);

        for (JsonElement element : root.getAsJsonArray("items")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();

            String id = item.has("id") ? item.get("id").getAsString() : null;
            if (id == null || id.isEmpty()) continue;

            parseBaseStats   (id, item, baseStats);
            parseTieredStats (id, item, tieredStats);
            parseUpgradeCosts(id, item, upgradeCosts);
        }

        return new ParsedApiData(
                Collections.unmodifiableMap(baseStats),
                Collections.unmodifiableMap(tieredStats),
                Collections.unmodifiableMap(upgradeCosts));
    }

    /** API {@code stats} uses lower_snake keys (e.g. {@code "damage"}); normalise to UPPER. */
    private static void parseBaseStats(String id, JsonObject item,
                                       Map<String, Map<String, Integer>> out) {
        if (!item.has("stats") || !item.get("stats").isJsonObject()) return;

        Map<String, Integer> stats = new HashMap<>();
        for (var entry : item.getAsJsonObject("stats").entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            try {
                int value = entry.getValue().getAsInt();
                stats.put(entry.getKey().toUpperCase(Locale.ROOT), value);
            } catch (NumberFormatException ignored) {
                // non-numeric stat (shouldn't happen but don't crash)
            }
        }
        if (!stats.isEmpty()) out.put(id, Collections.unmodifiableMap(stats));
    }

    private static void parseTieredStats(String id, JsonObject item,
                                         Map<String, Map<String, int[]>> out) {

        if (!item.has("tiered_stats") || !item.get("tiered_stats").isJsonObject()) return;

        Map<String, int[]> stats = new HashMap<>();
        for (var entry : item.getAsJsonObject("tiered_stats").entrySet()) {
            if (!entry.getValue().isJsonArray()) continue;
            JsonArray arr = entry.getValue().getAsJsonArray();

            // Skip single-value arrays: the stat doesn't change across tiers
            if (arr.size() < 2) continue;

            int[] values = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) values[i] = arr.get(i).getAsInt();
            stats.put(entry.getKey(), values);
        }

        if (!stats.isEmpty()) out.put(id, Collections.unmodifiableMap(stats));
    }

    private static void parseUpgradeCosts(String id, JsonObject item,
                                          Map<String, List<List<HypixelUpgradeCost>>> out) {

        if (!item.has("upgrade_costs") || !item.get("upgrade_costs").isJsonArray()) return;

        JsonArray costsArray = item.getAsJsonArray("upgrade_costs");
        List<List<HypixelUpgradeCost>> perStar = new ArrayList<>(costsArray.size());

        for (JsonElement starEl : costsArray) {
            if (!starEl.isJsonArray()) { perStar.add(List.of()); continue; }

            List<HypixelUpgradeCost> starCosts = new ArrayList<>();
            for (JsonElement costEl : starEl.getAsJsonArray()) {
                HypixelUpgradeCost cost = parseSingleCost(costEl);
                if (cost != null) starCosts.add(cost);
            }
            perStar.add(Collections.unmodifiableList(starCosts));
        }

        if (!perStar.isEmpty()) out.put(id, Collections.unmodifiableList(perStar));
    }

    @Nullable
    private static HypixelUpgradeCost parseSingleCost(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();

        String type   = obj.has("type")   ? obj.get("type").getAsString()   : null;
        int    amount = obj.has("amount") ? obj.get("amount").getAsInt()    : 0;
        if (type == null || amount <= 0) return null;

        return switch (type) {
            case "ESSENCE" -> obj.has("essence_type")
                    ? new HypixelUpgradeCost(type, obj.get("essence_type").getAsString(), null, amount)
                    : null;
            case "ITEM" -> obj.has("item_id")
                    ? new HypixelUpgradeCost(type, null, obj.get("item_id").getAsString(), amount)
                    : null;
            default -> null;
        };
    }

    // ── Disk cache ─────────────────────────────────────────────────────────────

    private static boolean isCacheFresh(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) return false;
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            return age < CACHE_TTL_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean loadFromCache(Path cacheFile) {
        if (!Files.exists(cacheFile)) return false;
        try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            int version = 0;
            if (root.has("cacheVersion") && root.get("cacheVersion").isJsonPrimitive()) {
                try {
                    version = root.get("cacheVersion").getAsInt();
                } catch (Exception ignored) {
                    LOGGER.warn("Failed to parse Hypixel items cache version");
                }
            }
            if (version < RepoDiskCache.CACHE_VERSION) {
                LOGGER.info("Hypixel items cache version {} is outdated (current: {}), will re-fetch",
                        version, RepoDiskCache.CACHE_VERSION);
                return false;
            }

            Map<String, Map<String, Integer>>            baseStats    = readBaseStatsCache(root);
            Map<String, Map<String, int[]>>              tieredStats  = readTieredStatsCache(root);
            Map<String, List<List<HypixelUpgradeCost>>>  upgradeCosts = readUpgradeCostsCache(root);

            HypixelItemsRegistry.load(
                    Collections.unmodifiableMap(baseStats),
                    Collections.unmodifiableMap(tieredStats),
                    Collections.unmodifiableMap(upgradeCosts));
            return true;

        } catch (Exception e) {
            LOGGER.warn("Failed to load Hypixel items cache: {}", e.getMessage());
            return false;
        }
    }

    private static Map<String, Map<String, Integer>> readBaseStatsCache(JsonObject root) {
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

    private static Map<String, Map<String, int[]>> readTieredStatsCache(JsonObject root) {
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

    private static Map<String, List<List<HypixelUpgradeCost>>> readUpgradeCostsCache(JsonObject root) {
        Map<String, List<List<HypixelUpgradeCost>>> out = new HashMap<>();
        if (!root.has("upgradeCosts") || !root.get("upgradeCosts").isJsonObject()) return out;
        for (var itemEntry : root.getAsJsonObject("upgradeCosts").entrySet()) {
            if (!itemEntry.getValue().isJsonArray()) continue;
            List<List<HypixelUpgradeCost>> perStar = new ArrayList<>();
            for (JsonElement starEl : itemEntry.getValue().getAsJsonArray()) {
                if (!starEl.isJsonArray()) { perStar.add(List.of()); continue; }
                List<HypixelUpgradeCost> starCosts = new ArrayList<>();
                for (JsonElement costEl : starEl.getAsJsonArray()) {
                    HypixelUpgradeCost cost = parseSingleCost(costEl);
                    if (cost != null) starCosts.add(cost);
                }
                perStar.add(Collections.unmodifiableList(starCosts));
            }
            out.put(itemEntry.getKey(), Collections.unmodifiableList(perStar));
        }
        return out;
    }

    private static void saveToCache(ParsedApiData data, Path cacheFile) {
        try {
            JsonObject root = new JsonObject();

            root.addProperty("cacheVersion", RepoDiskCache.CACHE_VERSION);

            // baseStats: { "ITEM_ID": { "DAMAGE": 100, ... }, ... }
            JsonObject baseStatsRoot = new JsonObject();
            for (var itemEntry : data.baseStats().entrySet()) {
                JsonObject statsObj = new JsonObject();
                for (var statEntry : itemEntry.getValue().entrySet()) {
                    statsObj.addProperty(statEntry.getKey(), statEntry.getValue());
                }
                baseStatsRoot.add(itemEntry.getKey(), statsObj);
            }
            root.add("baseStats", baseStatsRoot);

            // tieredStats: { "ITEM_ID": { "STAT_NAME": [v1, v2, ...], ... }, ... }
            JsonObject tieredStatsRoot = new JsonObject();
            for (var itemEntry : data.tieredStats().entrySet()) {
                JsonObject statsObj = new JsonObject();
                for (var statEntry : itemEntry.getValue().entrySet()) {
                    JsonArray arr = new JsonArray();
                    for (int v : statEntry.getValue()) arr.add(v);
                    statsObj.add(statEntry.getKey(), arr);
                }
                tieredStatsRoot.add(itemEntry.getKey(), statsObj);
            }
            root.add("tieredStats", tieredStatsRoot);

            // upgradeCosts: { "ITEM_ID": [ [ {cost}, ... ], ... ], ... }
            JsonObject costsRoot = new JsonObject();
            for (var itemEntry : data.upgradeCosts().entrySet()) {
                JsonArray starsArr = new JsonArray();
                for (List<HypixelUpgradeCost> starCosts : itemEntry.getValue()) {
                    JsonArray starArr = new JsonArray();
                    for (HypixelUpgradeCost cost : starCosts) {
                        JsonObject costObj = new JsonObject();
                        costObj.addProperty("type", cost.type());
                        if (cost.essenceType() != null)
                            costObj.addProperty("essence_type", cost.essenceType());
                        if (cost.itemId() != null)
                            costObj.addProperty("item_id", cost.itemId());
                        costObj.addProperty("amount", cost.amount());
                        starArr.add(costObj);
                    }
                    starsArr.add(starArr);
                }
                costsRoot.add(itemEntry.getKey(), starsArr);
            }
            root.add("upgradeCosts", costsRoot);

            Files.writeString(cacheFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to save Hypixel items cache: {}", e.getMessage());
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private record ParsedApiData(
            Map<String, Map<String, Integer>> baseStats,
            Map<String, Map<String, int[]>> tieredStats,
            Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts) {}
}