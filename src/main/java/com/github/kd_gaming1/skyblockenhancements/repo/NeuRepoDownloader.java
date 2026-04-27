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
     * <p>Callers should not block on these futures directly; instead, pass the session to
     * {@link com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.DataReadinessTracker}.
     */
    public DownloadSession startDownload(boolean startup) {
        CompletableFuture<Boolean> neuReady = new CompletableFuture<>();
        CompletableFuture<Boolean> hypixelReady = new CompletableFuture<>();
        DownloadSession session = new DownloadSession(neuReady, hypixelReady, Instant.now());

        CompletableFuture.runAsync(() -> {
            try {
                download(startup, session);
            } catch (Exception e) {
                LOGGER.error("Failed to download/load NEU repo data", e);
                neuReady.complete(false);
                hypixelReady.complete(false);
            }
        });

        return session;
    }

    public DownloadSession refresh() {
        return startDownload(true);
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

        // ── ETag + cache available → check if remote has changed ─────────────────
        if (cachedEtag != null && cacheExists) {
            return resolveWithEtag(cachedEtag, startup, session);
        }

        // ── No ETag but cache exists → try loading it ────────────────────────────
        if (cacheExists) {
            LOGGER.info("No ETag cached, checking cache validity...");
            if (diskCache.loadFromCache()) {
                loadMobData();
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
                diskCache.saveMeta(cachedEtag);
                session.neuReady().complete(true);
                fetchHypixelAndSignal(session, false);
                return FetchAction.USE_CACHE;
            }
            LOGGER.info("Cache outdated (version bump), re-downloading NEU repo...");
            return FetchAction.DOWNLOAD;
        }

        // Runtime check → nothing changed
        LOGGER.info("No NEU repo updates found.");
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

        // Fire Hypixel fetch in parallel with the ZIP download.
        CompletableFuture<Void> hypixelParallelFuture = CompletableFuture.runAsync(
                () -> fetchHypixelAndSignal(session, true));

        RepoZipParser.ParseResult result = RepoZipParser.downloadAndParse(HTTP, repoZipFile);

        NeuItemRegistry.clear();
        result.items().forEach(NeuItemRegistry::register);
        loadConstants(result.constants());
        NeuItemParser.resolvePetStats(result.items());
        NeuItemRegistry.markLoaded();

        // Signal NEU ready now — independently of Hypixel.
        session.neuReady().complete(true);

        LOGGER.info("Loaded {} SkyBlock items and {} constants files from NEU repo",
                result.items().size(), result.constants().size());

        // Wait for Hypixel to finish (it signals hypixelReadyFuture internally).
        try {
            hypixelParallelFuture.join();
        } catch (Exception e) {
            LOGGER.warn("Hypixel items fetch encountered an error during join", e);
            session.hypixelReady().complete(false);
        }

        RecipeDiagnostic.run();
        diskCache.saveCache(result.items(), result.constants(), result.etag());
        diskCache.saveMeta(result.etag());
    }

    // ── Mob data loading ────────────────────────────────────────────────────────

    /**
     * Populates {@link com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderRegistry}
     * and {@link com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobSkinRegistry} from
     * the saved repo ZIP. Called on cache-load paths where the full ZIP parse did not run.
     *
     * <p>If the ZIP file does not exist (first run with this code version, or user deleted it),
     * mob previews degrade to placeholders until the next fresh download saves the ZIP.
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
            session.hypixelReady().complete(false);
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
