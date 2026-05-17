package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.config.ModSettings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fetches Auction House lowest-BIN and Bazaar prices from the ScamScreener API.
 * Data is refreshed on a configurable interval and stored in {@link PriceStore}.
 *
 * <p>All network I/O runs on a dedicated single-thread executor so the common
 * ForkJoinPool is never blocked by {@code Thread.sleep} or long HTTP calls.
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

    /** Delay between the two endpoint calls to avoid burst-rate limiting. */
    private static final long INTER_REQUEST_DELAY_MS = 1_000L;
    /** First retry after 15 s, then doubles. */
    private static final long RETRY_BASE_MS = 15_000L;
    /** Hard cap at 15 minutes. */
    private static final long MAX_RETRY_MS = 900_000L;

    /** Dedicated executor for price fetches. */
    private static final java.util.concurrent.ExecutorService FETCH_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sbe-price-fetcher");
                t.setDaemon(true);
                return t;
            });
    /** Scheduled executor for clean async delays without blocking a worker. */
    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sbe-price-delay");
                t.setDaemon(true);
                return t;
            });

    private final ModSettings settings;
    private final PriceStore store;
    private final AtomicBoolean fetching = new AtomicBoolean(false);
    private final AtomicLong nextRetryAt = new AtomicLong(0);

    private final AtomicInteger lowestBinFailures = new AtomicInteger(0);
    private final AtomicInteger bazaarFailures = new AtomicInteger(0);

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
     * Called every tick from the main mod class.
     *
     * <p>Scheduling uses <strong>only</strong> timestamps:
     * <ul>
     *   <li>{@code lastFetchTimestamp + interval} for normal refreshes</li>
     *   <li>{@code nextRetryAt} for backoff after failures</li>
     * </ul>
     * {@link PriceStore#isLastFetchFailed()} is kept purely for tooltip UI state.
     */
    public void tick() {
        if (!settings.enablePriceTooltips()) return;

        long now = System.currentTimeMillis();
        long intervalMs = settings.priceRefreshIntervalMinutes() * 60_000L;
        long lastFetch = store.getLastFetchTimestamp();

        long normalNextFetch = (lastFetch == 0) ? 0 : lastFetch + intervalMs;
        long retryAt = nextRetryAt.get();
        long nextAllowed = Math.max(normalNextFetch, retryAt);

        if (now >= nextAllowed) {
            refreshAsync();
        }
    }

    // ── Fetch logic ─────────────────────────────────────────────────────────────

    private void refreshAsync() {
        if (!fetching.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(this::fetchLowestBin, FETCH_EXECUTOR)
                .thenCompose(v -> {
                    CompletableFuture<Void> delay = new CompletableFuture<>();
                    DELAY_EXECUTOR.schedule(
                            () -> delay.complete(null),
                            INTER_REQUEST_DELAY_MS,
                            TimeUnit.MILLISECONDS);
                    return delay;
                })
                .thenRunAsync(this::fetchBazaar, FETCH_EXECUTOR)
                .whenComplete((v, ex) -> {
                    fetching.set(false);
                    if (ex != null) {
                        handleFailure(ex);
                    } else {
                        handleSuccess();
                    }
                });
    }

    private void handleSuccess() {
        store.setLastFetchTimestamp(System.currentTimeMillis());
        store.clearFetchFailed();
        lowestBinFailures.set(0);
        bazaarFailures.set(0);
        nextRetryAt.set(0);
        LOGGER.info("Price data refreshed");
    }

    private void handleFailure(Throwable ex) {
        Throwable cause = rootCause(ex);
        int failures = Math.max(lowestBinFailures.get(), bazaarFailures.get());
        long backoff = calculateBackoff(failures);
        nextRetryAt.set(System.currentTimeMillis() + backoff);
        store.setFetchFailed();

        if (cause instanceof HttpException he && he.status == 429) {
            LOGGER.warn("Rate limited by price API (429), backing off for {}s (failure #{})",
                    backoff / 1_000, failures);
        } else {
            LOGGER.error("Failed to refresh price data (api may be down)", cause);
        }
    }

    /** Exponential backoff with ±1 s jitter, capped at 15 min. */
    private long calculateBackoff(int failureCount) {
        if (failureCount <= 0) return RETRY_BASE_MS;
        int exponent = Math.min(failureCount - 1, 6);
        long exponential = RETRY_BASE_MS * (1L << exponent);
        long jitter = (long) (Math.random() * 1_000L);
        return Math.min(exponential + jitter, MAX_RETRY_MS);
    }

    private void fetchLowestBin() {
        try {
            String body = httpGet(LOWEST_BIN_URL);
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            JsonObject products = root.has("products") ? root.getAsJsonObject("products") : root;

            Map<String, Double> temp = new ConcurrentHashMap<>(products.size());
            for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
                JsonObject product = entry.getValue().getAsJsonObject();
                readDouble(product, "price").ifPresent(price -> temp.put(entry.getKey(), price));
            }

            store.updateLowestBin(temp);
            lowestBinFailures.set(0);
        } catch (Exception e) {
            lowestBinFailures.incrementAndGet();
            throw new RuntimeException(e);
        }
    }

    private void fetchBazaar() {
        try {
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
            bazaarFailures.set(0);
        } catch (Exception e) {
            bazaarFailures.incrementAndGet();
            throw new RuntimeException(e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "SkyblockEnhancements/1.0 (+https://github.com/kd-gaming1/SkyblockEnhancements)")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new HttpException(status, "HTTP " + status + " for " + url);
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

    /** Walks the cause chain to the bottom. */
    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    // ── HttpException ───────────────────────────────────────────────────────────

    private static final class HttpException extends IOException {
        final int status;

        HttpException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}