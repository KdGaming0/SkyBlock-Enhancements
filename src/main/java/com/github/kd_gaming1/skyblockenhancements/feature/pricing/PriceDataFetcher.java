package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches and caches Auction House lowest-BIN prices and Bazaar prices from the
 * ScamScreener API. Data is refreshed on a configurable interval and all lookups
 * are O(1) from in-memory maps.
 *
 * <p>Thread-safe: HTTP fetches run on the common pool; maps are concurrent.
 */
public final class PriceDataFetcher {

    private static final String LOWEST_BIN_URL =
            "https://scamscreener.creepans.net/api/v1/lowestbin";
    private static final String BAZAAR_URL =
            "https://scamscreener.creepans.net/api/v1/bazaar";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    /** Lowest BIN prices: skyblock internal ID → price in coins. */
    private static final Map<String, Double> lowestBinPrices = new ConcurrentHashMap<>(4096);

    /** Bazaar buy prices (instant-buy / buy order): product ID → price per unit. */
    private static final Map<String, Double> bazaarBuyPrices = new ConcurrentHashMap<>(1024);

    /** Bazaar sell prices (instant-sell / sell order): product ID → price per unit. */
    private static final Map<String, Double> bazaarSellPrices = new ConcurrentHashMap<>(1024);

    private static volatile long lastFetchTimestamp;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    private PriceDataFetcher() {}

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Kicks off the initial background fetch if the feature is enabled.
     * If disabled at startup, {@link #tick()} will trigger the first fetch
     * once the user enables price tooltips in config.
     */
    public static void init() {
        if (SkyblockEnhancementsConfig.enablePriceTooltips) {
            refreshAsync();
        }
    }

    /**
     * Called every tick from the main mod class. Triggers a background refresh
     * when the configured interval has elapsed.
     */
    public static void tick() {
        if (!SkyblockEnhancementsConfig.enablePriceTooltips) return;

        long intervalMs = SkyblockEnhancementsConfig.priceRefreshIntervalMinutes * 60_000L;
        if (System.currentTimeMillis() - lastFetchTimestamp > intervalMs) {
            refreshAsync();
        }
    }

    /** Returns the lowest BIN price for the given skyblock item ID, if available. */
    public static Optional<Double> getLowestBin(String skyblockId) {
        if (skyblockId == null) return Optional.empty();
        Double price = lowestBinPrices.get(skyblockId);
        return price != null ? Optional.of(price) : Optional.empty();
    }

    /** Returns bazaar pricing for the given product ID, if available. */
    public static Optional<BazaarPrice> getBazaarPrice(String skyblockId) {
        if (skyblockId == null) return Optional.empty();
        Double buy = bazaarBuyPrices.get(skyblockId);
        Double sell = bazaarSellPrices.get(skyblockId);
        if (buy == null && sell == null) return Optional.empty();
        return Optional.of(new BazaarPrice(
                buy != null ? buy : 0.0,
                sell != null ? sell : 0.0));
    }

    /** Returns {@code true} once at least one successful fetch has completed. */
    public static boolean hasData() {
        return lastFetchTimestamp > 0;
    }

    // ── Fetch logic ─────────────────────────────────────────────────────────────

    private static void refreshAsync() {
        if (!fetching.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                fetchLowestBin();
                fetchBazaar();
                lastFetchTimestamp = System.currentTimeMillis();
                LOGGER.info("Price data refreshed (AH: {} items, BZ: {} products)",
                        lowestBinPrices.size(), bazaarBuyPrices.size());
            } catch (Exception e) {
                LOGGER.error("Failed to refresh price data", e);
            } finally {
                fetching.set(false);
            }
        });
    }

    /**
     * Parses the lowest-BIN response: a flat JSON object mapping item IDs to prices.
     * Example: {@code {"ASPECT_OF_THE_END": 1234567.0, ...}}
     */
    private static void fetchLowestBin() throws Exception {
        String body = httpGet(LOWEST_BIN_URL);
        JsonObject json = GSON.fromJson(body, JsonObject.class);

        Map<String, Double> temp = new ConcurrentHashMap<>(json.size());
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                temp.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }

        lowestBinPrices.clear();
        lowestBinPrices.putAll(temp);
    }

    /**
     * Parses the bazaar response. Format:
     * {@code {"lastUpdated": ..., "products": {"PRODUCT_ID": {"buy": X, "sell": Y, ...}, ...}}}
     */
    private static void fetchBazaar() throws Exception {
        String body = httpGet(BAZAAR_URL);
        JsonObject root = GSON.fromJson(body, JsonObject.class);

        JsonObject products = root.has("products") ? root.getAsJsonObject("products") : root;

        Map<String, Double> tempBuy = new ConcurrentHashMap<>(products.size());
        Map<String, Double> tempSell = new ConcurrentHashMap<>(products.size());

        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject product = entry.getValue().getAsJsonObject();

            readDouble(product, "buy").ifPresent(p -> tempBuy.put(entry.getKey(), p));
            readDouble(product, "sell").ifPresent(p -> tempSell.put(entry.getKey(), p));
        }

        bazaarBuyPrices.clear();
        bazaarBuyPrices.putAll(tempBuy);
        bazaarSellPrices.clear();
        bazaarSellPrices.putAll(tempSell);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "SkyblockEnhancements")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new java.io.IOException("HTTP " + status + " for " + url);
        }
        return resp.body();
    }

    private static Optional<Double> readDouble(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return Optional.of(el.getAsDouble());
        }
        return Optional.empty();
    }

    /** Immutable pair of bazaar buy and sell prices. */
    public record BazaarPrice(double buyPrice, double sellPrice) {}
}