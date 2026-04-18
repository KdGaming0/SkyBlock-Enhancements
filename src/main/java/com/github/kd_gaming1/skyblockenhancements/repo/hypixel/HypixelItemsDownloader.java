package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import java.net.http.HttpClient;
import java.nio.file.Path;

/**
 * Orchestrates the Hypixel items fetch pipeline: disk cache → HTTP fetch → registry publish.
 *
 * <p>HTTP/JSON work lives in {@link HypixelItemsFetcher} and disk I/O in
 * {@link HypixelItemsCacheStore}. This class sequences them and decides when to fall back to
 * stale cache.
 */
public final class HypixelItemsDownloader {

    private HypixelItemsDownloader() {}

    /**
     * Ensures {@link HypixelItemsRegistry} is populated.
     *
     * <p>Strategy:
     * <ol>
     *   <li>If the cache is fresh and {@code forceRefresh} is off, load it and return.</li>
     *   <li>Otherwise fetch from the API. On success, write the cache and publish.</li>
     *   <li>On fetch failure, fall back to stale cache; if that also fails, log and return.</li>
     * </ol>
     */
    public static void fetchAndCache(HttpClient http, Path cacheFile, boolean forceRefresh) {
        if (!forceRefresh && HypixelItemsCacheStore.isFresh(cacheFile)
                && publishFromCache(cacheFile, /*stale=*/ false)) {
            return;
        }

        HypixelItemsSnapshot fetched = HypixelItemsFetcher.fetch(http);
        if (fetched != null) {
            publish(fetched);
            HypixelItemsCacheStore.save(cacheFile, fetched);
            LOGGER.info(
                    "Fetched Hypixel items API: {} with base stats, {} with tiered_stats, {} with upgrade_costs",
                    fetched.baseStats().size(),
                    fetched.tieredStats().size(),
                    fetched.upgradeCosts().size());
            return;
        }

        if (!publishFromCache(cacheFile, /*stale=*/ true)) {
            LOGGER.warn("Hypixel API unavailable and no cache — essence stat upgrades won't show");
        }
    }

    /** Loads from disk and publishes; returns {@code true} on success. */
    private static boolean publishFromCache(Path cacheFile, boolean stale) {
        HypixelItemsSnapshot cached = HypixelItemsCacheStore.tryLoad(cacheFile);
        if (cached == null) return false;
        publish(cached);
        if (stale) {
            LOGGER.warn("Hypixel API fetch failed; using stale cache for essence upgrades");
        } else {
            LOGGER.info("Loaded Hypixel items from cache ({} items with upgrade costs)",
                    cached.upgradeCosts().size());
        }
        return true;
    }

    private static void publish(HypixelItemsSnapshot snap) {
        HypixelItemsRegistry.load(snap.baseStats(), snap.tieredStats(), snap.upgradeCosts());
    }
}