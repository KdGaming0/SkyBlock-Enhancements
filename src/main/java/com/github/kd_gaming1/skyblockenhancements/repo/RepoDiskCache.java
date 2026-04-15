package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the consolidated disk cache for NEU repo data. Handles serialization,
 * deserialization, version validation, and ETag/timestamp metadata.
 *
 * <p>The cache format is a single JSON file containing:
 * <ul>
 *   <li>{@code cacheVersion} — schema version for invalidation on parser changes</li>
 *   <li>{@code items} — all parsed {@link NeuItem} entries</li>
 *   <li>{@code constants} — raw JSON objects for parents, essence costs, etc.</li>
 *   <li>{@code etag} / {@code timestamp} — freshness metadata</li>
 * </ul>
 */
public final class RepoDiskCache {

    /** Bump this whenever the NeuItem schema or parsing logic changes. */
    static final int CACHE_VERSION = 9;

    private static final Gson GSON = new GsonBuilder().create();

    private final Path cacheFile;
    private final Path metaFile;

    public RepoDiskCache(Path cacheFile, Path metaFile) {
        this.cacheFile = cacheFile;
        this.metaFile = metaFile;
    }

    // ── Cache loading ───────────────────────────────────────────────────────────

    /**
     * Loads items and constants from the disk cache using streaming JSON parsing.
     * Resolves category and rarity at load time — same as the download path.
     *
     * <p>Clears and re-populates {@link NeuItemRegistry} and {@link NeuConstantsRegistry}.
     *
     * @return {@code true} if the cache was valid and loaded successfully
     */
    public boolean loadFromCache() {
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

    // ── Cache saving ────────────────────────────────────────────────────────────

    /**
     * Serializes items and constants to the disk cache as a single consolidated JSON file.
     */
    public void saveCache(Map<String, NeuItem> items, Map<String, JsonObject> constants,
                          @Nullable String etag) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
            JsonWriter jw = GSON.newJsonWriter(writer);
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

    // ── Constants from cache ────────────────────────────────────────────────────

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

    // ── Meta (ETag + timestamp) ─────────────────────────────────────────────────

    /**
     * Persists ETag and timestamp metadata.
     */
    public void saveMeta(@Nullable String etag) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("etag", etag != null ? etag : "");
        meta.addProperty("timestamp", System.currentTimeMillis());
        Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    /**
     * Reads a value from the metadata file.
     *
     * @return the value, or {@code null} if the key is absent or the file doesn't exist
     */
    @Nullable
    public String readMeta(String key) {
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

    /**
     * Returns {@code true} if the cache file exists on disk.
     */
    public boolean cacheExists() {
        return Files.exists(cacheFile);
    }
}