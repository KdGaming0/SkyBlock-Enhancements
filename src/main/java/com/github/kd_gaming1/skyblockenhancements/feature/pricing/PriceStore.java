package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;

/**
 * In-memory store for price data and tooltip-line caches.
 *
 * <p>Separated from {@link PriceDataFetcher} so the fetcher can be replaced
 * with a mock in tests while the store (and its caches) remain inspectable.
 */
public final class PriceStore {

    private final Map<String, Double> lowestBinPrices = new ConcurrentHashMap<>(4096);
    private final Map<String, Double> bazaarBuyPrices = new ConcurrentHashMap<>(1024);
    private final Map<String, Double> bazaarSellPrices = new ConcurrentHashMap<>(1024);
    private final Map<String, Double> bazaarSpreadPrices = new ConcurrentHashMap<>(1024);

    private volatile long lastFetchTimestamp;
    private volatile boolean lastFetchFailed;

    /** Tooltip-line cache keyed by SkyBlock internal item ID. */
    private final Map<String, PriceCacheEntry> tooltipCache = new ConcurrentHashMap<>(128);
    private static final long CACHE_TTL_MS = 30_000L;

    // ── Price lookups ──────────────────────────────────────────────────────────

    public Optional<Double> getLowestBin(String skyblockId) {
        if (skyblockId == null) return Optional.empty();
        Double price = lowestBinPrices.get(skyblockId);
        return price != null ? Optional.of(price) : Optional.empty();
    }

    public Optional<BazaarPrice> getBazaarPrice(String skyblockId) {
        if (skyblockId == null) return Optional.empty();
        Double buy = bazaarBuyPrices.get(skyblockId);
        Double sell = bazaarSellPrices.get(skyblockId);
        Double spread = bazaarSpreadPrices.get(skyblockId);
        if (buy == null && sell == null && spread == null) return Optional.empty();
        return Optional.of(new BazaarPrice(
                buy != null ? buy : 0.0,
                sell != null ? sell : 0.0,
                spread != null ? spread : 0.0));
    }

    public boolean hasData() {
        return lastFetchTimestamp > 0;
    }

    /** Returns true if the most recent fetch attempt failed. */
    public boolean isLastFetchFailed() {
        return lastFetchFailed;
    }

    /** Clears the failure flag before a new fetch attempt. */
    public void clearFetchFailed() {
        this.lastFetchFailed = false;
    }

    /** Sets the failure flag when a fetch fails. */
    public void setFetchFailed() {
        this.lastFetchFailed = true;
    }

    // ── Bulk updates (called by fetcher) ───────────────────────────────────────

    public void updateLowestBin(Map<String, Double> prices) {
        lowestBinPrices.clear();
        lowestBinPrices.putAll(prices);
    }

    public void updateBazaar(Map<String, Double> buyPrices, Map<String, Double> sellPrices, Map<String, Double> spreadPrices) {
        bazaarBuyPrices.clear();
        bazaarBuyPrices.putAll(buyPrices);
        bazaarSellPrices.clear();
        bazaarSellPrices.putAll(sellPrices);
        bazaarSpreadPrices.clear();
        bazaarSpreadPrices.putAll(spreadPrices);
    }

    public void setLastFetchTimestamp(long timestamp) {
        this.lastFetchTimestamp = timestamp;
    }

    public long getLastFetchTimestamp() {
        return lastFetchTimestamp;
    }

    // ── Tooltip cache ──────────────────────────────────────────────────────────

    public Optional<PriceCacheEntry> getTooltipCache(String skyblockId) {
        PriceCacheEntry entry = tooltipCache.get(skyblockId);
        if (entry == null) return Optional.empty();
        if (System.currentTimeMillis() - entry.timestamp() > CACHE_TTL_MS) {
            tooltipCache.remove(skyblockId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void putTooltipCache(String skyblockId, PriceCacheEntry entry) {
        tooltipCache.put(skyblockId, entry);
    }

    public void clearTooltipCache() {
        tooltipCache.clear();
    }

    // ── Cache entry ────────────────────────────────────────────────────────────

    public record PriceCacheEntry(List<Component> lines, long timestamp) {
    }
}
