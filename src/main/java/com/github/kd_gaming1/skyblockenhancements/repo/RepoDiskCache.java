package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.cache.VersionedJsonCache;
import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import com.google.gson.Gson;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemParser;
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
    public static final int CACHE_VERSION = 13;

    private static final Gson GSON = NeuItemParser.GSON;

    /** Keys used in the cache document's {@code constants} object. */
    private static final String[] CONSTANT_KEYS = {
            "parents", "essencecosts", "museum", "pets", "petnums",
            "reforges", "reforgestones"
    };

    private final Path cacheFile;
    private final Path metaFile;
    private final VersionedJsonCache<Object> metaCache;

    public RepoDiskCache(Path cacheFile, Path metaFile) {
        this.cacheFile = cacheFile;
        this.metaFile  = metaFile;
        this.metaCache = new VersionedJsonCache<>(GSON, Object.class);
    }

    public boolean cacheExists() {
        return Files.exists(cacheFile);
    }

    // ── Load ────────────────────────────────────────────────────────────────────

    /**
     * Streams the cache file into {@link NeuItemRegistry} and {@link NeuConstantsRegistry}.
     * Returns {@code false} if the cache is outdated, missing required sections, or malformed.
     *
     * <p>All top-level fields are read before validation so that field order in the JSON
     * does not matter (e.g. {@code "items"} may appear before {@code "cacheVersion"}).
     */
    public boolean loadFromCache() {
        try (BufferedReader br = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(br)) {

            NeuItemRegistry.clear();

            int version = 0;
            int itemCount = 0;
            JsonObject constants = null;
            boolean itemsLoaded = false;

            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "cacheVersion" -> version = reader.nextInt();
                    case "items" -> {
                        itemCount = readItems(reader);
                        itemsLoaded = true;
                    }
                    case "constants" -> constants = GSON.fromJson(reader, JsonObject.class);
                    default -> reader.skipValue();
                }
            }
            reader.endObject();

            if (!ensureVersionCompatible(version)) return false;
            if (!itemsLoaded) {
                LOGGER.warn("Cache file contained no items section — will re-download");
                return false;
            }

            loadConstantsFromJson(constants);

            NeuItemRegistry.markLoaded();
            LOGGER.info("Loaded {} SkyBlock items from cache (version {})", itemCount, version);
            RecipeDiagnostic.run();
            return true;

        } catch (IOException | RuntimeException e) {
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

        loadConstant(constants, "parents",      NeuConstantsRegistry::loadParents);
        loadConstant(constants, "essencecosts", NeuConstantsRegistry::loadEssenceCosts);
        loadConstant(constants, "museum",       NeuConstantsRegistry::loadMuseum);
        loadConstant(constants, "pets",         NeuConstantsRegistry::loadPetTypes);
        loadConstant(constants, "petnums",      NeuConstantsRegistry::loadPetNums);
        loadConstant(constants, "reforges",     NeuConstantsRegistry::loadReforges);
        loadConstant(constants, "reforgestones", NeuConstantsRegistry::loadReforgeStones);
    }

    private static void loadConstant(JsonObject constants, String key,
                                     java.util.function.Consumer<JsonObject> loader) {
        if (constants.has(key)) loader.accept(constants.getAsJsonObject(key));
    }

    // ── Save ────────────────────────────────────────────────────────────────────

    /**
     * Serialises items and constants to the cache file atomically. Constants are passed in directly
     * (keyed by original file name, e.g. {@code "parents.json"}); the {@code .json} suffix
     * is stripped when writing.
     */
    public void saveCache(Map<String, NeuItem> items,
                          Map<String, JsonObject> constants,
                          @Nullable String etag) throws Exception {
        Path tempPath = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8);
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

        try {
            Files.move(tempPath, cacheFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempPath, cacheFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String stripJsonExtension(String key) {
        return key.endsWith(".json") ? key.substring(0, key.length() - 5) : key;
    }

    // ── Meta ────────────────────────────────────────────────────────────────────

    public void saveMeta(@Nullable String etag) throws IOException {
        VersionedJsonCache.Metadata meta = new VersionedJsonCache.Metadata(
                etag != null ? etag : "", System.currentTimeMillis(), CACHE_VERSION);
        metaCache.saveMeta(metaFile, meta);
    }

    @Nullable
    public String readMeta(String key) {
        VersionedJsonCache.Metadata meta = metaCache.readMeta(metaFile);
        if (meta == null) return null;
        return switch (key) {
            case "etag" -> meta.etag();
            case "timestamp" -> String.valueOf(meta.timestamp());
            default -> null;
        };
    }

    /** Exposes the constants-key set for code that needs to know the cache schema. */
    public static String[] constantKeys() {
        return CONSTANT_KEYS.clone();
    }
}
