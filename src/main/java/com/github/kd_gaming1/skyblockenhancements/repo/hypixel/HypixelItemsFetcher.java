package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry.HypixelUpgradeCost;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
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
 * <p>Uses streaming {@link JsonReader} instead of materialising the entire response as a Gson
 * tree, cutting peak heap usage by roughly half for a several-megabyte payload.
 *
 * <p>Endpoint is public and requires no API key.
 */
public final class HypixelItemsFetcher {

    private static final String ENDPOINT = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int STREAM_BUFFER_SIZE = 1 << 16;

    /** Expected item count — used to pre-size maps and avoid rehashing. */
    private static final int EXPECTED_ITEM_COUNT = 4000;

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

            try (InputStream body = response.body();
                 JsonReader reader = new JsonReader(
                         new InputStreamReader(body, StandardCharsets.UTF_8))) {
                return parseResponse(reader);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch Hypixel items API: {}", e.getMessage());
            return null;
        }
    }

    // ── JSON → snapshot (streaming) ─────────────────────────────────────────────

    @Nullable
    private static HypixelItemsSnapshot parseResponse(JsonReader reader) throws IOException {
        Map<String, Map<String, Integer>> baseStats = new HashMap<>(EXPECTED_ITEM_COUNT);
        Map<String, Map<String, int[]>> tieredStats = new HashMap<>(512);
        Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts = new HashMap<>(EXPECTED_ITEM_COUNT);

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("items".equals(name)) {
                parseItemsArray(reader, baseStats, tieredStats, upgradeCosts);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new HypixelItemsSnapshot(
                Collections.unmodifiableMap(baseStats),
                Collections.unmodifiableMap(tieredStats),
                Collections.unmodifiableMap(upgradeCosts));
    }

    private static void parseItemsArray(JsonReader reader,
                                        Map<String, Map<String, Integer>> baseStats,
                                        Map<String, Map<String, int[]>> tieredStats,
                                        Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts)
            throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            parseItem(reader, baseStats, tieredStats, upgradeCosts);
        }
        reader.endArray();
    }

    private static void parseItem(JsonReader reader,
                                  Map<String, Map<String, Integer>> baseStats,
                                  Map<String, Map<String, int[]>> tieredStats,
                                  Map<String, List<List<HypixelUpgradeCost>>> upgradeCosts)
            throws IOException {
        String id = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "id" -> id = reader.nextString();
                case "stats" -> {
                    if (id != null) parseBaseStats(reader, id, baseStats);
                    else reader.skipValue();
                }
                case "tiered_stats" -> {
                    if (id != null) parseTieredStats(reader, id, tieredStats);
                    else reader.skipValue();
                }
                case "upgrade_costs" -> {
                    if (id != null) parseUpgradeCosts(reader, id, upgradeCosts);
                    else reader.skipValue();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static void parseBaseStats(JsonReader reader, String id,
                                       Map<String, Map<String, Integer>> out) throws IOException {
        Map<String, Integer> stats = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (reader.peek() == JsonToken.NUMBER) {
                try {
                    stats.put(key.toUpperCase(Locale.ROOT), reader.nextInt());
                } catch (NumberFormatException e) {
                    reader.skipValue();
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (!stats.isEmpty()) out.put(id, stats);
    }

    private static void parseTieredStats(JsonReader reader, String id,
                                         Map<String, Map<String, int[]>> out) throws IOException {
        Map<String, int[]> stats = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            List<Integer> values = readIntArray(reader);
            if (values.size() >= 2) {
                int[] arr = new int[values.size()];
                for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
                stats.put(key, arr);
            }
        }
        reader.endObject();
        if (!stats.isEmpty()) out.put(id, Collections.unmodifiableMap(stats));
    }

    private static List<Integer> readIntArray(JsonReader reader) throws IOException {
        List<Integer> list = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.NUMBER) {
                try {
                    list.add(reader.nextInt());
                } catch (NumberFormatException e) {
                    reader.skipValue();
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endArray();
        return list;
    }

    private static void parseUpgradeCosts(JsonReader reader, String id,
                                          Map<String, List<List<HypixelUpgradeCost>>> out)
            throws IOException {
        List<List<HypixelUpgradeCost>> perStar = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            perStar.add(readCostArray(reader));
        }
        reader.endArray();
        if (!perStar.isEmpty()) out.put(id, Collections.unmodifiableList(perStar));
    }

    private static List<HypixelUpgradeCost> readCostArray(JsonReader reader) throws IOException {
        List<HypixelUpgradeCost> costs = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            HypixelUpgradeCost cost = parseCost(reader);
            if (cost != null) costs.add(cost);
        }
        reader.endArray();
        return costs;
    }

    @Nullable
    private static HypixelUpgradeCost parseCost(JsonReader reader) throws IOException {
        String type = null;
        String essenceType = null;
        String itemId = null;
        Integer amount = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "type" -> type = reader.nextString();
                case "essence_type" -> essenceType = reader.nextString();
                case "item_id" -> itemId = reader.nextString();
                case "amount" -> {
                    if (reader.peek() == JsonToken.NUMBER) {
                        try {
                            amount = reader.nextInt();
                        } catch (NumberFormatException e) {
                            reader.skipValue();
                        }
                    } else {
                        reader.skipValue();
                    }
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (type == null || amount == null) return null;

        return switch (type) {
            case "ESSENCE" -> essenceType != null
                    ? new HypixelUpgradeCost(type, essenceType, null, amount)
                    : null;
            case "ITEM" -> itemId != null
                    ? new HypixelUpgradeCost(type, null, itemId, amount)
                    : null;
            default -> null;
        };
    }
}
