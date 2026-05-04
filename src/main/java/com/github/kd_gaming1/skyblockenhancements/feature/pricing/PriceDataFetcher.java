package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.config.ModSettings;
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
 * Fetches Auction House lowest-BIN and Bazaar prices from the ScamScreener API.
 * Data is refreshed on a configurable interval and stored in {@link PriceStore}.
 *
 * <p>Thread-safe: HTTP fetches run on the common pool; maps in the store are concurrent.
 */
public final class PriceDataFetcher {

    private static final String LOWEST_BIN_URL =
            "https://scamscreener.creepans.net/api/v2/lowestbin";
    private static final String BAZAAR_URL =
            "https://scamscreener.creepans.net/api/v1/bazaar";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private final ModSettings settings;
    private final PriceStore store;
    private final AtomicBoolean fetching = new AtomicBoolean(false);

    public PriceDataFetcher(ModSettings settings, PriceStore store) {
        this.settings = settings;
        this.store = store;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Kicks off the initial background fetch if the feature is enabled. */
    public void start() {
        if (settings.enablePriceTooltips()) {
            refreshAsync();
        }
    }

    /**
     * Called every tick from the main mod class. Triggers a background refresh
     * when the configured interval has elapsed.
     */
    public void tick() {
        if (!settings.enablePriceTooltips()) return;

        long intervalMs = settings.priceRefreshIntervalMinutes() * 60_000L;
        if (System.currentTimeMillis() - store.getLastFetchTimestamp() > intervalMs) {
            refreshAsync();
        }
    }

    // ── Fetch logic ─────────────────────────────────────────────────────────────

    private void refreshAsync() {
        if (!fetching.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                fetchLowestBin();
                fetchBazaar();
                store.setLastFetchTimestamp(System.currentTimeMillis());
                LOGGER.info("Price data refreshed");
            } catch (Exception e) {
                LOGGER.error("Failed to refresh price data", e);
            } finally {
                fetching.set(false);
            }
        });
    }

    private void fetchLowestBin() throws Exception {
        String body = httpGet(LOWEST_BIN_URL);
        JsonObject root = GSON.fromJson(body, JsonObject.class);

        JsonObject products = root.has("products") ? root.getAsJsonObject("products") : root;

        Map<String, Double> temp = new ConcurrentHashMap<>(products.size());
        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            JsonObject product = entry.getValue().getAsJsonObject();
            readDouble(product, "price").ifPresent(price -> temp.put(entry.getKey(), price));
        }

        store.updateLowestBin(temp);
    }

    private void fetchBazaar() throws Exception {
        String body = httpGet(BAZAAR_URL);
        JsonObject root = GSON.fromJson(body, JsonObject.class);

        JsonObject products = root.has("products") ? root.getAsJsonObject("products") : root;

        Map<String, Double> tempBuy = new ConcurrentHashMap<>(products.size());
        Map<String, Double> tempSell = new ConcurrentHashMap<>(products.size());
        Map<String, Double> tempSpread = new ConcurrentHashMap<>(products.size());

        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject product = entry.getValue().getAsJsonObject();

            readDouble(product, "buy").ifPresent(p -> tempBuy.put(entry.getKey(), p));
            readDouble(product, "sell").ifPresent(p -> tempSell.put(entry.getKey(), p));
            readDouble(product, "spread").ifPresent(p -> tempSpread.put(entry.getKey(), p));
        }

        store.updateBazaar(tempBuy, tempSell, tempSpread);
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
}
