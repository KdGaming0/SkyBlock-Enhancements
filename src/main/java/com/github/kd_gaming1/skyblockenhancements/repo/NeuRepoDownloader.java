package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;
import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Downloads the NotEnoughUpdates-REPO as a ZIP archive, stream-parses {@code items/*.json} and
 * {@code items/*.snbt} entries in-memory (never extracting to disk), and caches the result as a
 * single consolidated JSON file.
 *
 * <p>Uses the GitHub Git Refs API to fetch the latest commit SHA before downloading, allowing fast
 * no-op checks on subsequent launches when the repo hasn't changed.
 */
public class NeuRepoDownloader {

    /** Bump this whenever the NeuItem schema or parsing logic changes. */
    private static final int CACHE_VERSION = 4;

    private static final String REPO_ZIP_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final String COMMIT_API_URL =
            "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/git/refs/heads/master";

    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern ITEM_MODEL_PATTERN = Pattern.compile("ItemModel:\"([^\"]+)\"");
    private static final Pattern TEXTURE_VALUE_PATTERN = Pattern.compile("Value:\"([^\"]+)\"");
    private static final Pattern DISPLAY_COLOR_PATTERN =
            Pattern.compile("display:\\{.*?color:(\\d+)");
    /**
     * Matches the top-level {@code id: "minecraft:..."} field in a .snbt file.
     * The id is always a direct child of the root object (one level of indentation).
     */
    private static final Pattern SNBT_ID_PATTERN =
            Pattern.compile("^\\s*id:\\s*\"(minecraft:[^\"]+)\"", Pattern.MULTILINE);

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private final Path cacheDir;
    private final Path cacheFile;
    private final Path metaFile;

    public NeuRepoDownloader() {
        this.cacheDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("repo");
        this.cacheFile = cacheDir.resolve("items-cache.json");
        this.metaFile = cacheDir.resolve("repo-meta.json");
    }

    /** Kicks off an async download + parse. Safe to fire-and-forget. */
    public CompletableFuture<Void> downloadAsync() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        download();
                    } catch (Exception e) {
                        LOGGER.error("Failed to download/load NEU repo data", e);
                    }
                });
    }

    private void download() throws Exception {
        Files.createDirectories(cacheDir);

        String latestSha = fetchLatestSha();
        String cachedSha = readMeta("sha");

        if (latestSha != null && latestSha.equals(cachedSha) && Files.exists(cacheFile)) {
            LOGGER.info("NEU repo up-to-date (SHA {}), loading from cache", shortSha(cachedSha));
            loadFromCache();
            return;
        }

        if (latestSha == null && Files.exists(cacheFile)) {
            LOGGER.warn("Could not verify latest SHA; using cached NEU items");
            loadFromCache();
            return;
        }

        LOGGER.info("Downloading NEU repo ZIP...");
        Map<String, NeuItem> items = downloadAndParseZip();
        NeuItemRegistry.clear();
        items.forEach(NeuItemRegistry::register);
        NeuItemRegistry.markLoaded();
        LOGGER.info("Loaded {} SkyBlock items from NEU repo", items.size());
        RecipeDiagnostic.run();

        saveCache(items, latestSha);
    }

    // ── GitHub API ──────────────────────────────────────────────────────────────

    private String fetchLatestSha() {
        try {
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create(COMMIT_API_URL))
                            .header("User-Agent", "SkyblockEnhancements")
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();

            HttpResponse<String> resp =
                    HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
                return json.getAsJsonObject("object").get("sha").getAsString();
            }
        } catch (Exception e) {
            LOGGER.warn("Could not check repo SHA — will re-download", e);
        }
        return null;
    }

    // ── ZIP download + stream-parse ─────────────────────────────────────────────

    private Map<String, NeuItem> downloadAndParseZip() throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(REPO_ZIP_URL))
                        .header("User-Agent", "SkyblockEnhancements")
                        .header("Accept", "application/zip")
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build();

        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " downloading repo ZIP");
        }

        Map<String, NeuItem> items = new HashMap<>(5000);
        // Collect snbt overrides separately; apply after all JSONs are parsed.
        Map<String, String> snbtIds = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(resp.body()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..")) continue; // zip-slip protection

                // items/<NAME>.json
                int itemsIdx = name.indexOf("/items/");
                if (itemsIdx >= 0 && name.endsWith(".json")) {
                    String fileName = name.substring(itemsIdx + 7);
                    if (!fileName.contains("/")) {
                        String internalName = fileName.substring(0, fileName.length() - 5);
                        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        try {
                            NeuItem item = parseItemJson(internalName, content);
                            if (item != null) items.put(internalName, item);
                        } catch (JsonSyntaxException e) {
                            LOGGER.debug("Skipping malformed item JSON: {}", internalName);
                        }
                    }
                    zis.closeEntry();
                    continue;
                }

                // itemsOverlay/<dataVersion>/<NAME>.snbt
                int overlayIdx = name.indexOf("/itemsOverlay/");
                if (overlayIdx >= 0 && name.endsWith(".snbt")) {
                    String afterOverlay = name.substring(overlayIdx + 14); // strip ".../itemsOverlay/"
                    // afterOverlay = "<version>/<NAME>.snbt" — exactly one slash separating them
                    int slash = afterOverlay.indexOf('/');
                    if (slash >= 0 && afterOverlay.indexOf('/', slash + 1) < 0) {
                        String fileName = afterOverlay.substring(slash + 1);
                        String internalName = fileName.substring(0, fileName.length() - 5);
                        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        String snbtId = parseSnbtId(content);
                        if (snbtId != null) snbtIds.put(internalName, snbtId);
                    }
                }

                zis.closeEntry();
            }
        }

        // Apply snbt item ID overrides
        snbtIds.forEach((id, modernId) -> {
            NeuItem item = items.get(id);
            if (item != null) item.snbtItemId = modernId;
        });

        return items;
    }

    // ── SNBT parsing ────────────────────────────────────────────────────────────

    /**
     * Extracts the top-level {@code id} from a .snbt file. Only returns values starting with
     * {@code "minecraft:"} to avoid picking up nested id fields (e.g. inside custom_data).
     */
    private static String parseSnbtId(String snbt) {
        Matcher m = SNBT_ID_PATTERN.matcher(snbt);
        return m.find() ? m.group(1) : null;
    }

    // ── JSON → NeuItem ──────────────────────────────────────────────────────────

    private NeuItem parseItemJson(String internalName, String raw) {
        JsonObject json = GSON.fromJson(raw, JsonObject.class);

        NeuItem item = new NeuItem();
        item.internalName = internalName;
        item.itemId = str(json, "itemid", "minecraft:barrier");
        item.displayName = str(json, "displayname", internalName);
        item.damage = json.has("damage") ? json.get("damage").getAsInt() : 0;

        // Lore
        if (json.has("lore") && json.get("lore").isJsonArray()) {
            item.lore = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("lore")) {
                item.lore.add(e.getAsString());
            }
        } else {
            item.lore = List.of();
        }

        // Extract ItemModel from SNBT-like nbttag
        if (json.has("nbttag")) {
            String nbtStr = json.get("nbttag").getAsString();
            Matcher m = ITEM_MODEL_PATTERN.matcher(nbtStr);
            item.itemModel = m.find() ? m.group(1) : null;

            // Extract the skull texture
            Matcher texMatcher = TEXTURE_VALUE_PATTERN.matcher(nbtStr);
            if (texMatcher.find()) {
                String b64 = texMatcher.group(1).replaceAll("[^A-Za-z0-9+/=]", "");
                while (b64.length() % 4 != 0) b64 += "=";
                item.skullTexture = b64;
            }

            // Extract leather armor color
            Matcher colorMatcher = DISPLAY_COLOR_PATTERN.matcher(nbtStr);
            if (colorMatcher.find()) {
                item.leatherColor = Integer.parseInt(colorMatcher.group(1));
            }
        }

        // Legacy crafting recipe (A1–C3)
        if (json.has("recipe") && json.get("recipe").isJsonObject()) {
            item.recipe = new LinkedHashMap<>();
            for (var e : json.getAsJsonObject("recipe").entrySet()) {
                item.recipe.put(e.getKey(), e.getValue().getAsString());
            }
        }

        // Modern recipes array
        if (json.has("recipes") && json.get("recipes").isJsonArray()) {
            item.recipes = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("recipes")) {
                item.recipes.add(e.getAsJsonObject().deepCopy());
            }
        }

        return item;
    }

    // ── Disk cache ──────────────────────────────────────────────────────────────

    private void loadFromCache() {
        try {
            String content = Files.readString(cacheFile, StandardCharsets.UTF_8);
            JsonObject cache = GSON.fromJson(content, JsonObject.class);

            int version = cache.has("cacheVersion") ? cache.get("cacheVersion").getAsInt() : 0;
            if (version < CACHE_VERSION) {
                LOGGER.info(
                        "Cache version {} is outdated (current: {}), will re-download", version, CACHE_VERSION);
                Files.deleteIfExists(metaFile);
                return;
            }

            JsonObject itemsObj = cache.getAsJsonObject("items");
            NeuItemRegistry.clear();
            for (var entry : itemsObj.entrySet()) {
                NeuItem item = GSON.fromJson(entry.getValue(), NeuItem.class);
                NeuItemRegistry.register(entry.getKey(), item);
            }
            NeuItemRegistry.markLoaded();
            LOGGER.info("Loaded {} items from cache (version {})", itemsObj.size(), version);
            RecipeDiagnostic.run();
        } catch (Exception e) {
            LOGGER.error("Cache load failed — will re-download on next launch", e);
        }
    }

    private void saveCache(Map<String, NeuItem> items, String sha) {
        try {
            JsonObject cache = new JsonObject();
            cache.addProperty("cacheVersion", CACHE_VERSION);
            JsonObject itemsObj = new JsonObject();
            items.forEach((k, v) -> itemsObj.add(k, GSON.toJsonTree(v)));
            cache.add("items", itemsObj);
            Files.writeString(cacheFile, GSON.toJson(cache), StandardCharsets.UTF_8);
            saveMeta(sha);
            LOGGER.info("Saved items cache ({} items, version {})", items.size(), CACHE_VERSION);
        } catch (Exception e) {
            LOGGER.error("Failed to save items cache", e);
        }
    }

    // ── Meta (SHA + timestamp) ──────────────────────────────────────────────────

    private void saveMeta(String sha) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("sha", sha != null ? sha : "");
        meta.addProperty("timestamp", System.currentTimeMillis());
        Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    private String readMeta(String key) {
        try {
            if (Files.exists(metaFile)) {
                JsonObject meta =
                        GSON.fromJson(Files.readString(metaFile, StandardCharsets.UTF_8), JsonObject.class);
                return meta.has(key) ? meta.get(key).getAsString() : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── Public helpers ──────────────────────────────────────────────────────────

    /** Forces a re-download by clearing the cached SHA. */
    public CompletableFuture<Void> refresh() {
        try {
            Files.deleteIfExists(metaFile);
        } catch (IOException ignored) {
        }
        return downloadAsync();
    }

    /** Returns {@code true} if the cache is older than {@code hours}. */
    public boolean needsRefresh(int hours) {
        try {
            String ts = readMeta("timestamp");
            if (ts == null) return true;
            long elapsed = System.currentTimeMillis() - Long.parseLong(ts);
            return elapsed > (long) hours * 3_600_000L;
        } catch (Exception e) {
            return true;
        }
    }

    private static String str(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }

    private static String shortSha(String sha) {
        return sha != null && sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}