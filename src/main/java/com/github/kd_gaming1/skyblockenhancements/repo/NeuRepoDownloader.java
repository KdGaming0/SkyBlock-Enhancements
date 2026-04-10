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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Downloads the NotEnoughUpdates-REPO as a ZIP archive, stream-parses {@code items/*.json},
 * {@code items/*.snbt}, and selected {@code constants/*.json} entries in-memory (never extracting
 * to disk), and caches the result as a single consolidated JSON file.
 *
 * <p>Uses the GitHub Git Refs API to fetch the latest commit SHA before downloading, allowing fast
 * no-op checks on subsequent launches when the repo hasn't changed.
 */
public class NeuRepoDownloader {

    /** Bump this whenever the NeuItem schema or parsing logic changes. */
    private static final int CACHE_VERSION = 8;

    private static final String REPO_ZIP_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final String COMMIT_API_URL =
            "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/git/refs/heads/master";

    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern ITEM_MODEL_PATTERN = Pattern.compile("ItemModel:\"([^\"]+)\"");
    private static final Pattern TEXTURE_VALUE_PATTERN = Pattern.compile("Value:\"([^\"]+)\"");
    private static final Pattern DISPLAY_COLOR_PATTERN = Pattern.compile("display:\\{.*?color:(\\d+)");

    /**
     * Matches the top-level {@code id: "minecraft:..."} field in a .snbt file.
     * The id is always a direct child of the root object (one level of indentation).
     */
    private static final Pattern SNBT_ID_PATTERN =
            Pattern.compile("^\\s*id:\\s*\"(minecraft:[^\"]+)\"", Pattern.MULTILINE);

    /**
     * Matches the enchantment_glint_override component when set to true (1b) in a .snbt file.
     */
    private static final Pattern SNBT_GLINT_PATTERN =
            Pattern.compile("\"minecraft:enchantment_glint_override\"\\s*:\\s*1b");

    /**
     * Constants files to extract from the ZIP during stream-parsing. File names are relative
     * to {@code constants/} inside the ZIP root.
     */
    private static final Set<String> CONSTANTS_TO_EXTRACT = Set.of(
            "parents.json",
            "essencecosts.json",
            "museum.json",
            "pets.json"
    );

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

        LOGGER.info("NEU repo SHA check: latest={}, cached={}",
                shortSha(latestSha), shortSha(cachedSha));

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

        LOGGER.info("NEU repo cache miss or SHA changed; downloading ZIP...");
        ParseResult result = downloadAndParseZip();

        LOGGER.info("Parsed NEU ZIP: {} items, {} constants files",
                result.items.size(), result.constants.size());

        NeuItemRegistry.clear();
        result.items.forEach(NeuItemRegistry::register);

        LOGGER.info("Loading constants into registry...");
        loadConstants(result.constants);

        NeuItemRegistry.markLoaded();
        LOGGER.info("Loaded {} SkyBlock items from NEU repo", result.items.size());
        RecipeDiagnostic.run();

        LOGGER.info("Saving NEU repo cache...");
        saveCache(result.items, latestSha);
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

    /** Bundled result from ZIP parsing — items and raw constants JSON. */
    private record ParseResult(
            Map<String, NeuItem> items,
            Map<String, JsonObject> constants) {}

    private ParseResult downloadAndParseZip() throws Exception {
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
        Map<String, Boolean> snbtGlints = new HashMap<>();
        // Collect raw constants JSON for post-parse loading
        Map<String, JsonObject> constants = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(resp.body()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..")) continue; // zip-slip protection

                // items/<n>.json
                int itemsIdx = name.indexOf("/items/");
                if (itemsIdx >= 0 && name.endsWith(".json")) {
                    String fileName = name.substring(itemsIdx + 7);
                    if (!fileName.contains("/")) {
                        String internalName = fileName.substring(0, fileName.length() - 5);
                        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        try {
                            NeuItem item = parseItemJson(internalName, content);
                            items.put(internalName, item);
                        } catch (JsonSyntaxException e) {
                            LOGGER.debug("Skipping malformed item JSON: {}", internalName);
                        }
                    }
                    zis.closeEntry();
                    continue;
                }

                // itemsOverlay/<dataVersion>/<n>.snbt
                int overlayIdx = name.indexOf("/itemsOverlay/");
                if (overlayIdx >= 0 && name.endsWith(".snbt")) {
                    String afterOverlay = name.substring(overlayIdx + 14);
                    int slash = afterOverlay.indexOf('/');
                    if (slash >= 0 && afterOverlay.indexOf('/', slash + 1) < 0) {
                        String fileName = afterOverlay.substring(slash + 1);
                        String internalName = fileName.substring(0, fileName.length() - 5);
                        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

                        String snbtId = parseSnbtId(content);
                        if (snbtId != null) snbtIds.put(internalName, snbtId);

                        if (SNBT_GLINT_PATTERN.matcher(content).find()) {
                            snbtGlints.put(internalName, true);
                        }
                    }
                    zis.closeEntry();
                    continue;
                }

                // constants/<name>.json — extract selected files
                int constantsIdx = name.indexOf("/constants/");
                if (constantsIdx >= 0 && name.endsWith(".json")) {
                    String fileName = name.substring(constantsIdx + 11);
                    if (CONSTANTS_TO_EXTRACT.contains(fileName)) {
                        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        try {
                            constants.put(fileName, GSON.fromJson(content, JsonObject.class));
                        } catch (JsonSyntaxException e) {
                            LOGGER.warn("Skipping malformed constants file: {}", fileName);
                        }
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

        // Apply glint flags
        snbtGlints.forEach((id, glint) -> {
            NeuItem item = items.get(id);
            if (item != null) item.enchantmentGlint = glint;
        });

        // Eagerly resolve categories now that all items are parsed
        for (NeuItem item : items.values()) {
            item.category = SkyblockItemCategory.fromNeuItem(item);
        }

        return new ParseResult(items, constants);
    }

    // ── Constants loading ───────────────────────────────────────────────────────

    /**
     * Routes extracted constants files to their respective parsers in {@link NeuConstantsRegistry}.
     */
    private void loadConstants(Map<String, JsonObject> constants) {
        NeuConstantsRegistry.clear();

        JsonObject parents = constants.get("parents.json");
        if (parents != null) {
            NeuConstantsRegistry.loadParents(parents);
        }

        JsonObject essenceCosts = constants.get("essencecosts.json");
        if (essenceCosts != null) {
            NeuConstantsRegistry.loadEssenceCosts(essenceCosts);
        }

        JsonObject museum = constants.get("museum.json");
        if (museum != null) {
            NeuConstantsRegistry.loadMuseum(museum);
        }

        JsonObject pets = constants.get("pets.json");
        if (pets != null) {
            NeuConstantsRegistry.loadPetTypes(pets);
        }
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
                StringBuilder b64 = new StringBuilder(texMatcher.group(1).replaceAll("[^A-Za-z0-9+/=]", ""));
                while (b64.length() % 4 != 0) b64.append("=");
                item.skullTexture = b64.toString();
            }

            // Extract leather armor color
            Matcher colorMatcher = DISPLAY_COLOR_PATTERN.matcher(nbtStr);
            if (colorMatcher.find()) {
                item.leatherColor = Integer.parseInt(colorMatcher.group(1));
            }
        }

        // ── NPC location & external links ────────────────────────────────────────
        item.island = str(json, "island", "");
        item.x = json.has("x") ? json.get("x").getAsInt() : 0;
        item.y = json.has("y") ? json.get("y").getAsInt() : 0;
        item.z = json.has("z") ? json.get("z").getAsInt() : 0;
        item.infoType = str(json, "infoType", "");

        if (json.has("info") && json.get("info").isJsonArray()) {
            item.info = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("info")) {
                item.info.add(e.getAsString());
            }
        } else {
            item.info = List.of();
        }

        // ── Click command & parent ───────────────────────────────────────────────
        item.clickcommand = str(json, "clickcommand", "");
        item.parent = json.has("parent") ? json.get("parent").getAsString() : null;

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

            int version = cache.has("cacheVersion") ?
                    cache.get("cacheVersion").getAsInt() : 0;
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
                // Re-resolve category on cache load (transient field)
                item.category = SkyblockItemCategory.fromNeuItem(item);
                NeuItemRegistry.register(entry.getKey(), item);
            }

            // Load constants from cache if present
            loadConstantsFromCache(cache);

            NeuItemRegistry.markLoaded();
            LOGGER.info("Loaded {} items from cache (version {})", itemsObj.size(), version);
            RecipeDiagnostic.run();
        } catch (Exception e) {
            LOGGER.error("Cache load failed — will re-download on next launch", e);
        }
    }

    /**
     * Loads constants data from the disk cache. Constants are stored alongside items
     * in the same cache file to avoid a separate download on cache-hit launches.
     */
    private void loadConstantsFromCache(JsonObject cache) {
        NeuConstantsRegistry.clear();

        if (!cache.has("constants") || !cache.get("constants").isJsonObject()) {
            LOGGER.warn("No constants object found in cache");
            return;
        }

        JsonObject constants = cache.getAsJsonObject("constants");
        LOGGER.info("Cached constants present: {}", constants.keySet());

        if (constants.has("parents")) {
            NeuConstantsRegistry.loadParents(constants.getAsJsonObject("parents"));
        }
        if (constants.has("essencecosts")) {
            NeuConstantsRegistry.loadEssenceCosts(constants.getAsJsonObject("essencecosts"));
        }
        if (constants.has("museum")) {
            NeuConstantsRegistry.loadMuseum(constants.getAsJsonObject("museum"));
        }
        if (constants.has("pets")) {
            NeuConstantsRegistry.loadPetTypes(constants.getAsJsonObject("pets"));
        }
    }


    private void saveCache(Map<String, NeuItem> items, String sha) {
        try {
            JsonObject cache = new JsonObject();
            cache.addProperty("cacheVersion", CACHE_VERSION);

            JsonObject itemsObj = new JsonObject();
            items.forEach((k, v) -> itemsObj.add(k, GSON.toJsonTree(v)));
            cache.add("items", itemsObj);

            // Persist constants so cache-hit launches don't need re-download
            saveConstantsToCache(cache);

            Files.writeString(cacheFile, GSON.toJson(cache), StandardCharsets.UTF_8);
            saveMeta(sha);
            LOGGER.info("Saved items cache ({} items, version {})", items.size(), CACHE_VERSION);
        } catch (Exception e) {
            LOGGER.error("Failed to save items cache", e);
        }
    }

    /**
     * Serializes loaded constants data into the cache JSON using the raw JSON objects
     * retained by {@link NeuConstantsRegistry} during parsing.
     */
    private void saveConstantsToCache(JsonObject cache) {
        JsonObject constants = NeuConstantsRegistry.getRawConstantsForCache();
        if (!constants.isEmpty()) {
            cache.add("constants", constants);
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