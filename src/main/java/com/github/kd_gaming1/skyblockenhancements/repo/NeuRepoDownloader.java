package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;
import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.loader.api.FabricLoader;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsDownloader;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;

/**
 * Orchestrates the NEU repo download, parse, and cache pipeline. Each stage is
 * delegated to a focused class:
 *
 * <ul>
 *   <li>{@link RepoZipParser} — downloads and stream-parses the ZIP archive</li>
 *   <li>{@link NeuItemParser} — converts JSON/SNBT entries into {@link NeuItem} POJOs</li>
 *   <li>{@link RepoDiskCache} — serializes/deserializes the consolidated disk cache</li>
 * </ul>
 *
 * <p>This class handles only the high-level flow: should we use the cache or download?
 * After data is available, it populates {@link NeuItemRegistry} and {@link NeuConstantsRegistry}.
 */
public class NeuRepoDownloader {

    private static final String REPO_ZIP_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private final Path cacheDir;
    private final Path hypixelCacheFile;
    private final RepoDiskCache diskCache;

    /**
     * Completed with {@code true} when the NEU item cache is fully loaded into
     * {@link NeuItemRegistry}, {@code false} if it failed. Reset on each {@link #downloadAsync} call.
     */
    private volatile CompletableFuture<Boolean> neuReadyFuture = new CompletableFuture<>();

    /**
     * Completed with {@code true} when {@link HypixelItemsRegistry} is loaded,
     * {@code false} if it failed. Reset on each {@link #downloadAsync} call.
     */
    private volatile CompletableFuture<Boolean> hypixelReadyFuture = new CompletableFuture<>();

    public NeuRepoDownloader() {
        this.cacheDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("repo");
        this.hypixelCacheFile = cacheDir.resolve("hypixel-items-cache.json");
        this.diskCache = new RepoDiskCache(
                cacheDir.resolve("items-cache.json"),
                cacheDir.resolve("repo-meta.json"));
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Kicks off an async download + parse. Resets both readiness futures before starting.
     * Safe to fire-and-forget; callers that need completion signals use
     * {@link #getNeuReadyFuture()} and {@link #getHypixelReadyFuture()}.
     */
    public CompletableFuture<Void> downloadAsync(boolean startup) {
        // Reset futures so callers always get fresh signals for this run.
        neuReadyFuture = new CompletableFuture<>();
        hypixelReadyFuture = new CompletableFuture<>();

        return CompletableFuture.runAsync(() -> {
            try {
                download(startup);
            } catch (Exception e) {
                LOGGER.error("Failed to download/load NEU repo data", e);
                // Ensure futures are always completed — never leave callers hanging.
                neuReadyFuture.complete(false);
                hypixelReadyFuture.complete(false);
            }
        });
    }

    public CompletableFuture<Void> refresh() {
        return downloadAsync(true);
    }

    /** Returns the future that completes when NEU item data is ready (or failed). */
    public CompletableFuture<Boolean> getNeuReadyFuture() {
        return neuReadyFuture;
    }

    /** Returns the future that completes when Hypixel item data is ready (or failed). */
    public CompletableFuture<Boolean> getHypixelReadyFuture() {
        return hypixelReadyFuture;
    }

    public boolean needsRefreshMinutes(int minutes) {
        try {
            String ts = diskCache.readMeta("timestamp");
            if (ts == null) return true;
            long elapsed = System.currentTimeMillis() - Long.parseLong(ts);
            return elapsed > (long) minutes * 60_000L;
        } catch (Exception e) {
            return true;
        }
    }

    // ── Pipeline orchestration ──────────────────────────────────────────────────

    private void download(boolean startup) throws Exception {
        Files.createDirectories(cacheDir);

        FetchAction action = resolveFetchAction(startup);

        if (action == FetchAction.USE_CACHE) {
            return;
        }

        performFreshDownload();
    }

    // ── Fetch strategy resolution ───────────────────────────────────────────────

    private enum FetchAction { USE_CACHE, DOWNLOAD }

    /**
     * Determines whether to use the disk cache or perform a fresh download.
     * Handles ETag validation, cache fallback on network failure, and cache-only paths.
     *
     * @return {@link FetchAction#USE_CACHE} if the cache was loaded, {@link FetchAction#DOWNLOAD} otherwise
     */
    private FetchAction resolveFetchAction(boolean startup) throws IOException {
        String cachedEtag = diskCache.readMeta("etag");
        boolean cacheExists = diskCache.cacheExists();

        // ── ETag + cache available → check if remote has changed ─────────────────
        if (cachedEtag != null && cacheExists) {
            return resolveWithEtag(cachedEtag, startup);
        }

        // ── No ETag but cache exists → try loading it ────────────────────────────
        if (cacheExists) {
            LOGGER.info("No ETag cached, checking cache validity...");
            if (diskCache.loadFromCache()) {
                diskCache.saveMeta(null);
                neuReadyFuture.complete(true);
                fetchHypixelAndSignal(false);
                return FetchAction.USE_CACHE;
            }
            LOGGER.info("Cache invalid, re-downloading NEU repo...");
        } else {
            LOGGER.info("No cache found, downloading NEU repo...");
        }

        return FetchAction.DOWNLOAD;
    }

    /**
     * Handles the ETag-based freshness check. Sends a HEAD request with If-None-Match.
     */
    private FetchAction resolveWithEtag(String cachedEtag, boolean startup) {
        HttpRequest headReq = HttpRequest.newBuilder()
                .uri(URI.create(REPO_ZIP_URL))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", "SkyblockEnhancements")
                .header("If-None-Match", cachedEtag)
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<Void> headResp =
                    HTTP.send(headReq, HttpResponse.BodyHandlers.discarding());

            if (headResp.statusCode() == 304) {
                return handleUnchangedRepo(cachedEtag, startup);
            }

            LOGGER.info("NEU repo changed, downloading...");
            return FetchAction.DOWNLOAD;

        } catch (Exception e) {
            LOGGER.warn("HEAD check failed — falling back to cached data if available");
            return handleHeadCheckFailure(startup);
        }
    }

    /**
     * Called when the remote repo hasn't changed (HTTP 304).
     */
    private FetchAction handleUnchangedRepo(String cachedEtag, boolean startup) throws IOException {
        LOGGER.info("NEU repo unchanged (ETag matched)");

        if (startup) {
            LOGGER.info("Loading from cache...");
            if (diskCache.loadFromCache()) {
                diskCache.saveMeta(cachedEtag);
                neuReadyFuture.complete(true);
                fetchHypixelAndSignal(false);
                return FetchAction.USE_CACHE;
            }
            LOGGER.info("Cache outdated (version bump), re-downloading NEU repo...");
            return FetchAction.DOWNLOAD;
        }

        // Runtime check → nothing changed
        LOGGER.info("No NEU repo updates found.");
        diskCache.saveMeta(cachedEtag);
        neuReadyFuture.complete(true);
        fetchHypixelAndSignal(false);
        return FetchAction.USE_CACHE;
    }

    /**
     * Called when the HEAD request fails (network error, timeout, etc.).
     */
    private FetchAction handleHeadCheckFailure(boolean startup) {
        if (startup && diskCache.loadFromCache()) {
            neuReadyFuture.complete(true);
            fetchHypixelAndSignal(false);
            return FetchAction.USE_CACHE;
        }

        LOGGER.warn("Cached data unusable or runtime check — re-downloading NEU repo...");
        return FetchAction.DOWNLOAD;
    }

    // ── Fresh download path ─────────────────────────────────────────────────────

    /**
     * Downloads the ZIP, parses everything, populates registries, and saves the cache.
     *
     * <p>NEU and Hypixel fetches run in parallel. {@link #neuReadyFuture} is completed as
     * soon as {@link NeuItemRegistry#markLoaded()} returns, before waiting on the Hypixel
     * future — so both signals reflect actual data availability, not combined completion.
     */
    private void performFreshDownload() throws Exception {
        ItemStackBuilder.clearCache();

        // Fire Hypixel fetch in parallel with the ZIP download.
        CompletableFuture<Void> hypixelParallelFuture = CompletableFuture.runAsync(
                () -> fetchHypixelAndSignal(true));

        RepoZipParser.ParseResult result = RepoZipParser.downloadAndParse(HTTP);

        NeuItemRegistry.clear();
        result.items().forEach(NeuItemRegistry::register);
        loadConstants(result.constants());
        NeuItemParser.resolvePetStats(result.items());
        NeuItemRegistry.markLoaded();

        // Signal NEU ready now — independently of Hypixel.
        neuReadyFuture.complete(true);

        LOGGER.info("Loaded {} SkyBlock items and {} constants files from NEU repo",
                result.items().size(), result.constants().size());

        // Wait for Hypixel to finish (it signals hypixelReadyFuture internally).
        try {
            hypixelParallelFuture.join();
        } catch (Exception e) {
            LOGGER.warn("Hypixel items fetch encountered an error during join", e);
            hypixelReadyFuture.complete(false);
        }

        RecipeDiagnostic.run();
        diskCache.saveCache(result.items(), result.constants(), result.etag());
        diskCache.saveMeta(result.etag());
    }

    /**
     * Fetches (or loads from cache) Hypixel items and completes {@link #hypixelReadyFuture}.
     * Must never throw — always completes the future, success or failure.
     */
    private void fetchHypixelAndSignal(boolean forceRefresh) {
        try {
            if (forceRefresh) {
                HypixelItemsRegistry.clear();
            }
            HypixelItemsDownloader.fetchAndCache(HTTP, hypixelCacheFile, forceRefresh);
            boolean loaded = HypixelItemsRegistry.isLoaded();
            hypixelReadyFuture.complete(loaded);
            if (!loaded) {
                LOGGER.warn("Hypixel items registry empty after fetch — signalling failure.");
            }
        } catch (Exception e) {
            LOGGER.warn("Hypixel items fetch failed", e);
            hypixelReadyFuture.complete(false);
        }
    }

    // ── Constants loading ───────────────────────────────────────────────────────

    private void loadConstants(Map<String, JsonObject> constants) {
        NeuConstantsRegistry.clear();

        JsonObject parents = constants.get("parents.json");
        if (parents != null) NeuConstantsRegistry.loadParents(parents);

        JsonObject essenceCosts = constants.get("essencecosts.json");
        if (essenceCosts != null) NeuConstantsRegistry.loadEssenceCosts(essenceCosts);

        JsonObject museum = constants.get("museum.json");
        if (museum != null) NeuConstantsRegistry.loadMuseum(museum);

        JsonObject pets = constants.get("pets.json");
        if (pets != null) NeuConstantsRegistry.loadPetTypes(pets);

        JsonObject petNums = constants.get("petnums.json");
        if (petNums != null) NeuConstantsRegistry.loadPetNums(petNums);
    }
}