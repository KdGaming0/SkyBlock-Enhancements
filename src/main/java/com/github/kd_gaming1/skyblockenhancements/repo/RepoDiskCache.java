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
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;

/**
 * On-disk consolidated cache for NEU repo data.
 *
 * <p>The cache is a single JSON document containing:
 * <ul>
 *   <li>{@code cacheVersion}  — schema version, bumped when parser logic changes</li>
 *   <li>{@code items}         — serialised {@link NeuItem} entries</li>
 *   <li>{@code constants}     — raw JSON for parents, essence costs, museum, pets, petnums</li>
 *   <li>{@code etag} / {@code timestamp} — freshness metadata</li>
 * </ul>
 *
 * <p>ETag/timestamp metadata is written to a sidecar file so freshness can be checked without
 * loading and parsing the whole cache.
 */
public final class RepoDiskCache {

    /** Bump whenever {@link NeuItem} schema or parser output changes. */
    public static final int CACHE_VERSION = 10;

    private static final Gson GSON = new GsonBuilder().create();

    /** Keys used in the cache document's {@code constants} object. */
    private static final String[] CONSTANT_KEYS = {
            "parents", "essencecosts", "museum", "pets", "petnums"
    };

    private final Path cacheFile;
    private final Path metaFile;

    public RepoDiskCache(Path cacheFile, Path metaFile) {
        this.cacheFile = cacheFile;
        this.metaFile  = metaFile;
    }

    public boolean cacheExists() {
        return Files.exists(cacheFile);
    }

    // ── Load ────────────────────────────────────────────────────────────────────

    /**
     * Streams the cache file into {@link NeuItemRegistry} and {@link NeuConstantsRegistry}.
     * Returns {@code false} if the cache is outdated, missing required sections, or malformed.
     */
    public boolean loadFromCache() {
        try (BufferedReader br = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(br)) {

            NeuItemRegistry.clear();

            int version = 0;
            int itemCount = 0;
            boolean itemsLoaded = false;
            boolean constantsLoaded = false;

            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "cacheVersion" -> version = reader.nextInt();

                    case "items" -> {
                        if (!ensureVersionCompatible(version)) return false;
                        itemCount = readItems(reader);
                        itemsLoaded = true;
                    }

                    case "constants" -> {
                        loadConstantsFromJson(GSON.fromJson(reader, JsonObject.class));
                        constantsLoaded = true;
                    }

                    default -> reader.skipValue();
                }
            }
            reader.endObject();

            if (!ensureVersionCompatible(version)) return false;
            if (!itemsLoaded) {
                LOGGER.warn("Cache file contained no items section — will re-download");
                return false;
            }
            if (!constantsLoaded) {
                LOGGER.warn("Cache file contained no constants section — constants will be "
                        + "missing until the next re-download");
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

    private int readItems(JsonReader reader) throws IOException {
        int count = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String internalName = reader.nextName();
            NeuItem item = GSON.fromJson(reader, NeuItem.class);
            item.category = SkyblockItemCategory.fromNeuItem(item);
            item.rarity   = SkyblockItemCategory.extractRarity(item);
            NeuItemRegistry.register(internalName, item);
            count++;
        }
        reader.endObject();
        return count;
    }

    private boolean ensureVersionCompatible(int version) throws IOException {
        if (version >= CACHE_VERSION) return true;
        LOGGER.info("Cache version {} is outdated (current: {}), will re-download",
                version, CACHE_VERSION);
        Files.deleteIfExists(metaFile);
        return false;
    }

    private static void loadConstantsFromJson(@Nullable JsonObject constants) {
        NeuConstantsRegistry.clear();
        if (constants == null) return;

        if (constants.has("parents"))      NeuConstantsRegistry.loadParents(constants.getAsJsonObject("parents"));
        if (constants.has("essencecosts")) NeuConstantsRegistry.loadEssenceCosts(constants.getAsJsonObject("essencecosts"));
        if (constants.has("museum"))       NeuConstantsRegistry.loadMuseum(constants.getAsJsonObject("museum"));
        if (constants.has("pets"))         NeuConstantsRegistry.loadPetTypes(constants.getAsJsonObject("pets"));
        if (constants.has("petnums"))      NeuConstantsRegistry.loadPetNums(constants.getAsJsonObject("petnums"));
    }

    // ── Save ────────────────────────────────────────────────────────────────────

    /**
     * Serialises items and constants to the cache file. Constants are passed in directly
     * (keyed by original file name, e.g. {@code "parents.json"}); the {@code .json} suffix
     * is stripped when writing.
     */
    public void saveCache(Map<String, NeuItem> items,
                          Map<String, JsonObject> constants,
                          @Nullable String etag) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8);
             JsonWriter jw = GSON.newJsonWriter(writer)) {

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
                jw.name(stripJsonExtension(entry.getKey()));
                GSON.toJson(entry.getValue(), JsonObject.class, jw);
            }
            jw.endObject();

            jw.name("etag").value(etag != null ? etag : "");
            jw.name("timestamp").value(System.currentTimeMillis());
            jw.endObject();
            jw.flush();
        }
    }

    private static String stripJsonExtension(String key) {
        return key.endsWith(".json") ? key.substring(0, key.length() - 5) : key;
    }

    // ── Meta ────────────────────────────────────────────────────────────────────

    public void saveMeta(@Nullable String etag) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("etag", etag != null ? etag : "");
        meta.addProperty("timestamp", System.currentTimeMillis());
        Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    @Nullable
    public String readMeta(String key) {
        try {
            if (!Files.exists(metaFile)) return null;
            JsonObject meta = GSON.fromJson(
                    Files.readString(metaFile, StandardCharsets.UTF_8), JsonObject.class);
            return meta.has(key) ? meta.get(key).getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Exposes the constants-key set for code that needs to know the cache schema. */
    public static String[] constantKeys() {
        return CONSTANT_KEYS.clone();
    }
}