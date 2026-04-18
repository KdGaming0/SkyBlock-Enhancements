package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry.HypixelUpgradeCost;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Fetches {@code /v2/resources/skyblock/items} from the Hypixel public API and parses the
 * response into a {@link HypixelItemsSnapshot}.
 *
 * <p>Endpoint is public and requires no API key.
 */
public final class HypixelItemsFetcher {

    private static final String ENDPOINT = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int STREAM_BUFFER_SIZE = 1 << 16;
    private static final Gson GSON = new GsonBuilder().create();

    private HypixelItemsFetcher() {}

    /**
     * Fetches and parses the Hypixel items endpoint. Returns {@code null} on any network or
     * parse failure — callers should fall back to the disk cache.
     */
    @Nullable
    public static HypixelItemsSnapshot fetch(HttpClient http) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("User-Agent", "SkyblockEnhancements")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = http.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Hypixel items API returned HTTP {}", response.statusCode());
                return null;
            }

            try (InputStream body = new BufferedInputStream(response.body(), STREAM_BUFFER_SIZE)) {
                JsonObject root = GSON.fromJson(
                        new InputStreamReader(body, StandardCharsets.UTF_8), JsonObject.class);
                return parseResponse(root);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch Hypixel items API: {}", e.getMessage());
            return null;
        }
    }

    // ── JSON → snapshot ─────────────────────────────────────────────────────────

    @Nullable
    private static HypixelItemsSnapshot parseResponse(JsonObject root) {
        if (!root.has("items") || !root.get("items").isJsonArray()) {
            LOGGER.warn("Hypixel items response missing 'items' array");
            return null;
        }

        Map<String, Map<String, Integer>> baseStats = new HashMap<>(512);
        Map<String, Map<String, int[]>> tieredStats = new HashMap<>(128);
        Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts = new HashMap<>(512);

        for (JsonElement element : root.getAsJsonArray("items")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();

            String id = item.has("id") ? item.get("id").getAsString() : null;
            if (id == null || id.isEmpty()) continue;

            parseBaseStats(id, item, baseStats);
            parseTieredStats(id, item, tieredStats);
            parseUpgradeCosts(id, item, upgradeCosts);
        }

        return new HypixelItemsSnapshot(
                Collections.unmodifiableMap(baseStats),
                Collections.unmodifiableMap(tieredStats),
                Collections.unmodifiableMap(upgradeCosts));
    }

    /** API {@code stats} uses lower_snake keys — normalise to UPPER_SNAKE. */
    private static void parseBaseStats(String id, JsonObject item,
                                       Map<String, Map<String, Integer>> out) {
        if (!item.has("stats") || !item.get("stats").isJsonObject()) return;

        Map<String, Integer> stats = new HashMap<>();
        for (var entry : item.getAsJsonObject("stats").entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            try {
                stats.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsInt());
            } catch (NumberFormatException ignored) {
                // non-numeric stat — skip
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

            // Skip single-value arrays: the stat doesn't change across tiers.
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

        List<List<HypixelUpgradeCost>> perStar = new ArrayList<>();
        for (JsonElement starEl : item.getAsJsonArray("upgrade_costs")) {
            if (!starEl.isJsonArray()) { perStar.add(List.of()); continue; }
            List<HypixelUpgradeCost> starCosts = new ArrayList<>();
            for (JsonElement costEl : starEl.getAsJsonArray()) {
                HypixelUpgradeCost cost = parseCost(costEl);
                if (cost != null) starCosts.add(cost);
            }
            perStar.add(Collections.unmodifiableList(starCosts));
        }
        if (!perStar.isEmpty()) out.put(id, Collections.unmodifiableList(perStar));
    }

    @Nullable
    static HypixelUpgradeCost parseCost(JsonElement el) {
        if (!el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();
        if (!obj.has("type") || !obj.has("amount")) return null;

        String type = obj.get("type").getAsString();
        int amount = obj.get("amount").getAsInt();

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
}