package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;
import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
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

import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Downloads the NotEnoughUpdates-REPO as a ZIP archive, stream-parses {@code items/*.json},
 * {@code items/*.snbt}, and selected {@code constants/*.json} entries in-memory (never extracting
 * to disk), and caches the result as a single consolidated JSON file.
 *
 * <p>Uses the GitHub Git Refs API to fetch the latest commit SHA before downloading, allowing fast
 * no-op checks on subsequent launches when the repo hasn't changed.
 *
 * <p>Both {@link SkyblockItemCategory} and {@link SkyblockRarity} are resolved once per item
 * immediately after parsing, before {@link NeuItemRegistry#markLoaded()} is called. No
 * downstream code needs to resolve these — they are always non-null for items with lore.
 */
public class NeuRepoDownloader {

    /** Bump this whenever the NeuItem schema or parsing logic changes. */
    private static final int CACHE_VERSION = 9;

    private static final String REPO_ZIP_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern ITEM_MODEL_PATTERN = Pattern.compile("ItemModel:\"([^\"]+)\"");
    private static final Pattern TEXTURE_VALUE_PATTERN = Pattern.compile("Value:\"([^\"]+)\"");
    private static final Pattern DISPLAY_COLOR_PATTERN = Pattern.compile("display:\\{.*?color:(\\d+)");

    private static final Pattern SNBT_ID_PATTERN =
            Pattern.compile("^\\s*id:\\s*\"(minecraft:[^\"]+)\"", Pattern.MULTILINE);

    private static final Pattern SNBT_GLINT_PATTERN =
            Pattern.compile("\"minecraft:enchantment_glint_override\"\\s*:\\s*1b");

    private static final Set<String> CONSTANTS_TO_EXTRACT = Set.of(
            "parents.json",
            "essencecosts.json",
            "museum.json",
            "pets.json",
            "petnums.json"
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
    public CompletableFuture<Void> downloadAsync(boolean startup) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        download(startup);
                    } catch (Exception e) {
                        LOGGER.error("Failed to download/load NEU repo data", e);
                    }
                });
    }

    private void download(boolean startup) throws Exception {
        Files.createDirectories(cacheDir);

        String cachedEtag = readMeta("etag");
        boolean cacheExists = Files.exists(cacheFile);

        // ── If we have ETag + cache, check for updates ────────────────────────────
        if (cachedEtag != null && cacheExists) {
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
                    LOGGER.info("NEU repo unchanged (ETag matched)");

                    if (startup) {
                        LOGGER.info("Loading from cache...");
                        if (loadFromCache()) {
                            saveMeta(cachedEtag);
                            return;
                        }
                        LOGGER.info("Cache outdated (version bump), re-downloading NEU repo...");
                    } else {
                        // Runtime check → nothing changed, do nothing
                        LOGGER.info("No NEU repo updates found.");
                        return;
                    }

                } else {
                    LOGGER.info("NEU repo changed, downloading...");
                }

            } catch (Exception e) {
                LOGGER.warn("HEAD check failed — falling back to cached data if available");

                if (startup && loadFromCache()) {
                    return;
                }

                LOGGER.warn("Cached data unusable or runtime check — re-downloading NEU repo...");
            }

        } else if (cacheExists) {
            // No ETag but cache exists
            LOGGER.info("No ETag cached, checking cache validity...");
            if (loadFromCache()) {
                saveMeta(null);
                return;
            }
            LOGGER.info("Cache invalid, re-downloading NEU repo...");
        } else {
            LOGGER.info("No cache found, downloading NEU repo...");
        }

        // ── Download + parse ──────────────────────────────────────────────────────
        ItemStackBuilder.clearCache();
        ParseResult result = downloadAndParseZip();

        NeuItemRegistry.clear();
        result.items.forEach(NeuItemRegistry::register);
        loadConstants(result.constants);

        // Resolve pet stat placeholders
        resolvePetStats(result.items);

        NeuItemRegistry.markLoaded();

        LOGGER.info("Loaded {} SkyBlock items and {} constants files from NEU repo",
                result.items.size(), result.constants.size());

        RecipeDiagnostic.run();

        saveCache(result.items, result.constants, result.etag);
        saveMeta(result.etag);
    }

    // ── ZIP download + stream-parse ─────────────────────────────────────────────

    private record ParseResult(
            Map<String, NeuItem> items,
            Map<String, JsonObject> constants,
            String etag) {}

    private ParseResult downloadAndParseZip() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
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

        String etag = resp.headers().firstValue("ETag").orElse(null);

        Map<String, NeuItem> items = new HashMap<>(5000);
        Map<String, String> snbtIds = new HashMap<>();
        Map<String, Boolean> snbtGlints = new HashMap<>();
        Map<String, JsonObject> constants = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(resp.body(), 1 << 16), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..")) continue;

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

        // Apply snbt overrides
        snbtIds.forEach((id, modernId) -> {
            NeuItem item = items.get(id);
            if (item != null) item.snbtItemId = modernId;
        });

        snbtGlints.forEach((id, glint) -> {
            NeuItem item = items.get(id);
            if (item != null) item.enchantmentGlint = glint;
        });

        // Resolve category and rarity once per item
        resolveAllCategoryAndRarity(items);

        return new ParseResult(items, constants, etag);
    }

    // ── Category + rarity resolution ────────────────────────────────────────────

    /**
     * Resolves both {@link SkyblockItemCategory} and {@link SkyblockRarity} on every
     * item, once, at parse time. Downstream consumers can treat these fields as stable
     * (though still nullable for items that genuinely have no category or rarity).
     */
    private static void resolveAllCategoryAndRarity(Map<String, NeuItem> items) {
        for (NeuItem item : items.values()) {
            item.category = SkyblockItemCategory.fromNeuItem(item);
            item.rarity = SkyblockItemCategory.extractRarity(item);
        }
    }

    // ── Pet stat placeholder resolution ─────────────────────────────────────────

    /**
     * Resolves {@code {STAT_NAME}} and {@code {N}} placeholders in pet lore using
     * level-100 values from {@code petnums.json}. Must be called after constants are
     * loaded (so {@link PetStatResolver} has data) and after category resolution
     * (so we can identify pets).
     */
    private static void resolvePetStats(Map<String, NeuItem> items) {
        if (!PetStatResolver.isLoaded()) {
            LOGGER.warn("petnums not loaded — pet stat placeholders will remain unresolved");
            return;
        }

        int resolved = 0;
        for (NeuItem item : items.values()) {
            if (item.category != SkyblockItemCategory.PET) continue;
            PetStatResolver.resolve(item);
            resolved++;
        }
        LOGGER.info("Resolved stat placeholders on {} pet items", resolved);
    }

    // ── Constants loading ───────────────────────────────────────────────────────

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

        JsonObject petNums = constants.get("petnums.json");
        if (petNums != null) {
            NeuConstantsRegistry.loadPetNums(petNums);
        }
    }

    // ── SNBT parsing ────────────────────────────────────────────────────────────

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

        if (json.has("lore") && json.get("lore").isJsonArray()) {
            item.lore = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("lore")) {
                item.lore.add(e.getAsString());
            }
        } else {
            item.lore = List.of();
        }

        if (json.has("nbttag")) {
            String nbtStr = json.get("nbttag").getAsString();
            Matcher m = ITEM_MODEL_PATTERN.matcher(nbtStr);
            item.itemModel = m.find() ? m.group(1) : null;

            Matcher texMatcher = TEXTURE_VALUE_PATTERN.matcher(nbtStr);
            if (texMatcher.find()) {
                StringBuilder b64 = new StringBuilder(texMatcher.group(1).replaceAll("[^A-Za-z0-9+/=]", ""));
                while (b64.length() % 4 != 0) b64.append("=");
                item.skullTexture = b64.toString();
            }

            Matcher colorMatcher = DISPLAY_COLOR_PATTERN.matcher(nbtStr);
            if (colorMatcher.find()) {
                item.leatherColor = Integer.parseInt(colorMatcher.group(1));
            }
        }

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

        item.clickcommand = str(json, "clickcommand", "");
        item.parent = json.has("parent") ? json.get("parent").getAsString() : null;

        // Requirement metadata
        item.crafttext = str(json, "crafttext", "");
        item.slayerReq = str(json, "slayer_req", "");

        if (json.has("recipe") && json.get("recipe").isJsonObject()) {
            item.recipe = new LinkedHashMap<>();
            for (var e : json.getAsJsonObject("recipe").entrySet()) {
                item.recipe.put(e.getKey(), e.getValue().getAsString());
            }
        }

        if (json.has("recipes") && json.get("recipes").isJsonArray()) {
            item.recipes = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("recipes")) {
                item.recipes.add(e.getAsJsonObject().deepCopy());
            }
        }

        return item;
    }

    // ── Disk cache ──────────────────────────────────────────────────────────────

    /**
     * Loads items from the disk cache using streaming JSON parsing.
     * Resolves category and rarity at load time — same as the download path.
     */
    private boolean loadFromCache() {
        try (BufferedReader br = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(br)) {

            reader.beginObject();

            int version = 0;
            int itemCount = 0;
            boolean itemsLoaded = false;
            boolean constantsLoaded = false;

            NeuItemRegistry.clear();

            while (reader.hasNext()) {
                String fieldName = reader.nextName();
                switch (fieldName) {
                    case "cacheVersion" -> version = reader.nextInt();

                    case "items" -> {
                        if (version < CACHE_VERSION) {
                            LOGGER.info("Cache version {} is outdated (current: {}), will re-download",
                                    version, CACHE_VERSION);
                            Files.deleteIfExists(metaFile);
                            return false;
                        }
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String internalName = reader.nextName();
                            NeuItem item = GSON.fromJson(reader, NeuItem.class);
                            // Resolve category and rarity at parse time — single point of resolution.
                            item.category = SkyblockItemCategory.fromNeuItem(item);
                            item.rarity = SkyblockItemCategory.extractRarity(item);
                            NeuItemRegistry.register(internalName, item);
                            itemCount++;
                        }
                        reader.endObject();
                        itemsLoaded = true;
                    }

                    case "constants" -> {
                        JsonObject constants = GSON.fromJson(reader, JsonObject.class);
                        loadConstantsFromCacheObject(constants);
                        constantsLoaded = true;
                    }

                    default -> reader.skipValue();
                }
            }

            reader.endObject();

            if (version < CACHE_VERSION) {
                LOGGER.info("Cache version {} is outdated (current: {}), will re-download",
                        version, CACHE_VERSION);
                Files.deleteIfExists(metaFile);
                return false;
            }

            if (!itemsLoaded) {
                LOGGER.warn("Cache file contained no items section — will re-download");
                return false;
            }

            if (!constantsLoaded) {
                LOGGER.warn("No constants found in cache — constants will be missing until next re-download");
            }

            NeuItemRegistry.markLoaded();
            LOGGER.info("Loaded {} SkyBlock items from cache (version {})", itemCount, version);
            RecipeDiagnostic.run();
            return true;

        } catch (Exception e) {
            LOGGER.error("Cache load failed — will re-download", e);
            return false;
        }
    }

    private void loadConstantsFromCacheObject(JsonObject constants) {
        NeuConstantsRegistry.clear();

        if (constants == null) {
            LOGGER.warn("No constants found in cache — constants will be missing until next re-download");
            return;
        }

        if (constants.has("parents"))      NeuConstantsRegistry.loadParents(constants.getAsJsonObject("parents"));
        if (constants.has("essencecosts")) NeuConstantsRegistry.loadEssenceCosts(constants.getAsJsonObject("essencecosts"));
        if (constants.has("museum"))       NeuConstantsRegistry.loadMuseum(constants.getAsJsonObject("museum"));
        if (constants.has("pets"))         NeuConstantsRegistry.loadPetTypes(constants.getAsJsonObject("pets"));
        if (constants.has("petnums"))      NeuConstantsRegistry.loadPetNums(constants.getAsJsonObject("petnums"));
    }


    private void saveCache(Map<String, NeuItem> items, Map<String, JsonObject> constants, String etag) throws Exception {
        try (var writer = new java.io.BufferedWriter(
                Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8))) {
            com.google.gson.stream.JsonWriter jw = GSON.newJsonWriter(writer);
            jw.beginObject();
            jw.name("cacheVersion").value(CACHE_VERSION);

            jw.name("items");
            jw.beginObject();
            for (var entry : items.entrySet()) {
                jw.name(entry.getKey());
                GSON.toJson(entry.getValue(), NeuItem.class, jw);
            }
            jw.endObject();

            jw.name("constants");
            jw.beginObject();
            for (var entry : constants.entrySet()) {
                jw.name(entry.getKey().replace(".json", ""));
                GSON.toJson(entry.getValue(), JsonObject.class, jw);
            }
            jw.endObject();

            jw.name("etag").value(etag != null ? etag : "");
            jw.name("timestamp").value(System.currentTimeMillis());
            jw.endObject();
            jw.flush();
        }
    }

    // ── Meta (SHA + timestamp) ──────────────────────────────────────────────────

    private void saveMeta(String etag) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("etag", etag != null ? etag : "");
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

    public CompletableFuture<Void> refresh() {
        return downloadAsync(true);
    }

    public boolean needsRefreshMinutes(int minutes) {
        try {
            String ts = readMeta("timestamp");
            if (ts == null) return true;
            long elapsed = System.currentTimeMillis() - Long.parseLong(ts);
            return elapsed > (long) minutes * 60_000L;
        } catch (Exception e) {
            return true;
        }
    }

    private static String str(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }
}