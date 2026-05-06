package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;
import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsDownloader;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.loader.api.FabricLoader;

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
 *
 * <p>The raw ZIP is saved to disk during fresh downloads so that mob render data
 * (JSONs and skin PNGs) can be re-parsed on cache-only startups without re-downloading.
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
    private final Path repoZipFile;
    private final RepoDiskCache diskCache;

    /** Guards against concurrent downloads racing on shared registries and file writes. */
    private final AtomicBoolean downloadInProgress = new AtomicBoolean(false);

    /**
     * In-memory copy of the last known cache timestamp. Updated whenever meta is written
     * or read, so {@link #needsRefreshMinutes} avoids blocking file I/O on the client thread.
     */
    private volatile long cachedTimestamp = -1;

    public NeuRepoDownloader() {
        this.cacheDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("repo");
        this.hypixelCacheFile = cacheDir.resolve("hypixel-items-cache.json");
        this.repoZipFile = cacheDir.resolve("neu_repo.zip");
        this.diskCache = new RepoDiskCache(
                cacheDir.resolve("items-cache.json"),
                cacheDir.resolve("repo-meta.json"));
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Kicks off an async download + parse and returns an immutable {@link DownloadSession}
     * containing the futures that signal when NEU and Hypixel data are ready.
     *
     * <p>If a download is already running, the existing session is returned so callers
     * share the same result instead of spawning duplicate work.
     *
     * <p>Callers should not block on these futures directly; instead, pass the session to
     * {@link com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.DataReadinessTracker}.
     */
    public DownloadSession startDownload(boolean startup) {
        if (!downloadInProgress.compareAndSet(false, true)) {
            LOGGER.info("Download already in progress — returning existing session.");
            return currentSession;
        }

        CompletableFuture<Boolean> neuReady = new CompletableFuture<>();
        CompletableFuture<Boolean> hypixelReady = new CompletableFuture<>();
        DownloadSession session = new DownloadSession(neuReady, hypixelReady, Instant.now());
        this.currentSession = session;

        CompletableFuture.runAsync(() -> {
            try {
                download(startup, session);
            } catch (Exception e) {
                LOGGER.error("Failed to download/load NEU repo data", e);
                completeIfPending(neuReady, false);
                completeIfPending(hypixelReady, false);
            } finally {
                downloadInProgress.set(false);
            }
        });

        return session;
    }

    public DownloadSession refresh() {
        return startDownload(true);
    }

    /**
     * Returns {@code true} when the cached data is older than {@code minutes} or no cache exists.
     * This method is safe to call from the client thread — it uses an in-memory timestamp
     * and only falls back to disk when the memory cache has not been populated yet.
     */
    public boolean needsRefreshMinutes(int minutes) {
        long ts = cachedTimestamp;
        if (ts < 0) {
            try {
                String meta = diskCache.readMeta("timestamp");
                if (meta == null) return true;
                ts = Long.parseLong(meta);
                cachedTimestamp = ts;
            } catch (Exception e) {
                return true;
            }
        }
        long elapsed = System.currentTimeMillis() - ts;
        return elapsed > (long) minutes * 60_000L;
    }

    // ── Pipeline orchestration ──────────────────────────────────────────────────

    private DownloadSession currentSession;

    private void download(boolean startup, DownloadSession session) throws Exception {
        Files.createDirectories(cacheDir);

        FetchAction action = resolveFetchAction(startup, session);

        if (action == FetchAction.USE_CACHE) {
            return;
        }

        performFreshDownload(session);
    }

    // ── Fetch strategy resolution ───────────────────────────────────────────────

    private enum FetchAction { USE_CACHE, DOWNLOAD }

    /**
     * Determines whether to use the disk cache or perform a fresh download.
     * Handles ETag validation, cache fallback on network failure, and cache-only paths.
     *
     * @return {@link FetchAction#USE_CACHE} if the cache was loaded, {@link FetchAction#DOWNLOAD} otherwise
     */
    private FetchAction resolveFetchAction(boolean startup, DownloadSession session) throws IOException {
        String cachedEtag = diskCache.readMeta("etag");
        boolean cacheExists = diskCache.cacheExists();

        if (cachedEtag != null && cacheExists) {
            return resolveWithEtag(cachedEtag, startup, session);
        }

        if (cacheExists) {
            LOGGER.info("No ETag cached, checking cache validity...");
            if (diskCache.loadFromCache()) {
                loadMobData();
                updateCachedTimestamp();
                diskCache.saveMeta(null);
                session.neuReady().complete(true);
                fetchHypixelAndSignal(session, false);
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
    private FetchAction resolveWithEtag(String cachedEtag, boolean startup, DownloadSession session) {
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
                return handleUnchangedRepo(cachedEtag, startup, session);
            }

            LOGGER.info("NEU repo changed, downloading...");
            return FetchAction.DOWNLOAD;

        } catch (Exception e) {
            LOGGER.warn("HEAD check failed — falling back to cached data if available");
            return handleHeadCheckFailure(startup, session);
        }
    }

    /**
     * Called when the remote repo hasn't changed (HTTP 304).
     */
    private FetchAction handleUnchangedRepo(String cachedEtag, boolean startup, DownloadSession session) throws IOException {
        LOGGER.info("NEU repo unchanged (ETag matched)");

        if (startup) {
            LOGGER.info("Loading from cache...");
            if (diskCache.loadFromCache()) {
                loadMobData();
                updateCachedTimestamp();
                diskCache.saveMeta(cachedEtag);
                session.neuReady().complete(true);
                fetchHypixelAndSignal(session, false);
                return FetchAction.USE_CACHE;
            }
            LOGGER.info("Cache outdated (version bump), re-downloading NEU repo...");
            return FetchAction.DOWNLOAD;
        }

        LOGGER.info("No NEU repo updates found.");
        updateCachedTimestamp();
        diskCache.saveMeta(cachedEtag);
        session.neuReady().complete(true);
        fetchHypixelAndSignal(session, false);
        return FetchAction.USE_CACHE;
    }

    /**
     * Called when the HEAD request fails (network error, timeout, etc.).
     */
    private FetchAction handleHeadCheckFailure(boolean startup, DownloadSession session) {
        if (startup && diskCache.loadFromCache()) {
            loadMobData();
            updateCachedTimestamp();
            session.neuReady().complete(true);
            fetchHypixelAndSignal(session, false);
            return FetchAction.USE_CACHE;
        }

        LOGGER.warn("Cached data unusable or runtime check — re-downloading NEU repo...");
        return FetchAction.DOWNLOAD;
    }

    // ── Fresh download path ─────────────────────────────────────────────────────

    /**
     * Downloads the ZIP, parses everything, populates registries, and saves the cache.
     *
     * <p>NEU and Hypixel fetches run in parallel. {@link DownloadSession#neuReady()} is completed as
     * soon as {@link NeuItemRegistry#markLoaded()} returns, before waiting on the Hypixel
     * future — so both signals reflect actual data availability, not combined completion.
     */
    private void performFreshDownload(DownloadSession session) throws Exception {
        ItemStackBuilder.clearCache();

        CompletableFuture<Void> hypixelParallelFuture = CompletableFuture.runAsync(
                () -> fetchHypixelAndSignal(session, true));

        RepoZipParser.ParseResult result = RepoZipParser.downloadAndParse(HTTP, repoZipFile);

        NeuItemRegistry.clear();
        result.items().forEach(NeuItemRegistry::register);
        loadConstants(result.constants());
        NeuItemParser.resolvePetStats(result.items());
        NeuItemRegistry.markLoaded();

        session.neuReady().complete(true);

        loadMobData();

        LOGGER.info("Loaded {} SkyBlock items and {} constants files from NEU repo",
                result.items().size(), result.constants().size());

        hypixelParallelFuture.join();

        RecipeDiagnostic.run();
        diskCache.saveCache(result.items(), result.constants(), result.etag());
        updateCachedTimestamp();
        diskCache.saveMeta(result.etag());
    }

    // ── Mob data loading ────────────────────────────────────────────────────────

    /**
     * Populates {@link com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderRegistry}
     * and {@link com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobSkinRegistry} from
     * the saved repo ZIP. Called on cache-load paths where the full ZIP parse did not run.
     */
    private void loadMobData() {
        RepoZipParser.parseMobsFromFile(repoZipFile);
    }

    // ── Hypixel items ───────────────────────────────────────────────────────────

    /**
     * Fetches (or loads from cache) Hypixel items and completes {@link DownloadSession#hypixelReady()}.
     * Must never throw — always completes the future, success or failure.
     */
    private void fetchHypixelAndSignal(DownloadSession session, boolean forceRefresh) {
        try {
            if (forceRefresh) {
                HypixelItemsRegistry.clear();
            }
            HypixelItemsDownloader.fetchAndCache(HTTP, hypixelCacheFile, forceRefresh);
            boolean loaded = HypixelItemsRegistry.isLoaded();
            session.hypixelReady().complete(loaded);
            if (!loaded) {
                LOGGER.warn("Hypixel items registry empty after fetch — signalling failure.");
            }
        } catch (Exception e) {
            LOGGER.warn("Hypixel items fetch failed", e);
            completeIfPending(session.hypixelReady(), false);
        }
    }

    // ── Constants loading ───────────────────────────────────────────────────────

    private void loadConstants(Map<String, JsonObject> constants) {
        NeuConstantsRegistry.clear();

        loadConstant(constants, "parents.json", NeuConstantsRegistry::loadParents);
        loadConstant(constants, "essencecosts.json", NeuConstantsRegistry::loadEssenceCosts);
        loadConstant(constants, "museum.json", NeuConstantsRegistry::loadMuseum);
        loadConstant(constants, "pets.json", NeuConstantsRegistry::loadPetTypes);
        loadConstant(constants, "petnums.json", NeuConstantsRegistry::loadPetNums);
        loadConstant(constants, "reforges.json", NeuConstantsRegistry::loadReforges);
        loadConstant(constants, "reforgestones.json", NeuConstantsRegistry::loadReforgeStones);
    }

    private static void loadConstant(Map<String, JsonObject> constants, String key,
                                     java.util.function.Consumer<JsonObject> loader) {
        JsonObject value = constants.get(key);
        if (value != null) loader.accept(value);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void updateCachedTimestamp() {
        try {
            String ts = diskCache.readMeta("timestamp");
            if (ts != null) cachedTimestamp = Long.parseLong(ts);
        } catch (Exception ignored) {
            // Best-effort; next call will retry.
        }
    }

    private static void completeIfPending(CompletableFuture<Boolean> future, boolean value) {
        if (!future.isDone()) {
            future.complete(value);
        }
    }
}
