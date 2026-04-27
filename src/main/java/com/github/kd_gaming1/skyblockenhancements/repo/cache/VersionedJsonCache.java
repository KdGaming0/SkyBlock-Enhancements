package com.github.kd_gaming1.skyblockenhancements.repo.cache;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Generic versioned JSON cache with schema validation and atomic writes.
 *
 * <p>On disk, each entry is a single JSON object containing:
 * <pre>
 * {
 *   "schemaVersion": 11,
 *   "timestamp": 1715000000000,
 *   "etag": "\"abc123\"",
 *   "data": { ... payload ... }
 * }
 * </pre>
 *
 * @param <T> the payload type
 */
public final class VersionedJsonCache<T> {

    public record Metadata(String etag, long timestamp, int schemaVersion) {}

    private final Gson gson;
    private final Class<T> payloadType;

    public VersionedJsonCache(Gson gson, Class<T> payloadType) {
        this.gson = gson;
        this.payloadType = payloadType;
    }

    /**
     * Loads the payload from {@code file} if it exists and its schema version is
     * &gt;= {@code expectedSchema}. Returns {@code null} on missing file, outdated
     * schema, or parse failure.
     */
    @Nullable
    public CachedResult<T> load(Path file, int expectedSchema) {
        if (!Files.exists(file)) return null;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null) return null;

            int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (version < expectedSchema) {
                LOGGER.info("Cache version {} is outdated (expected: {}), will re-fetch", version, expectedSchema);
                return null;
            }

            long timestamp = root.has("timestamp") ? root.get("timestamp").getAsLong() : 0L;
            String etag = root.has("etag") ? root.get("etag").getAsString() : null;

            T payload = gson.fromJson(root.get("data"), payloadType);
            return new CachedResult<>(payload, new Metadata(etag, timestamp, version));
        } catch (Exception e) {
            LOGGER.warn("Failed to load versioned cache from {}: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * Serialises {@code payload} together with {@code metadata} and writes it atomically.
     *
     * @throws java.io.IOException if the write fails
     */
    public void save(Path file, T payload, Metadata metadata) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", metadata.schemaVersion());
        root.addProperty("timestamp", metadata.timestamp());
        root.addProperty("etag", metadata.etag() != null ? metadata.etag() : "");
        root.add("data", gson.toJsonTree(payload));

        AtomicFileWriter.writeJson(file, root, gson);
    }

    /**
     * Reads a sidecar metadata file (plain {@code etag}/{@code timestamp}/{@code schemaVersion} object).
     */
    @Nullable
    public Metadata readMeta(Path metaFile) {
        try {
            if (!Files.exists(metaFile)) return null;
            JsonObject meta = gson.fromJson(
                    Files.readString(metaFile, StandardCharsets.UTF_8), JsonObject.class);
            if (meta == null) return null;

            String etag = meta.has("etag") ? meta.get("etag").getAsString() : null;
            long timestamp = meta.has("timestamp") ? meta.get("timestamp").getAsLong() : 0L;
            int schemaVersion = meta.has("schemaVersion") ? meta.get("schemaVersion").getAsInt() : 0;
            return new Metadata(etag, timestamp, schemaVersion);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Writes a sidecar metadata file atomically.
     */
    public void saveMeta(Path metaFile, Metadata metadata) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("etag", metadata.etag() != null ? metadata.etag() : "");
        meta.addProperty("timestamp", metadata.timestamp());
        meta.addProperty("schemaVersion", metadata.schemaVersion());
        AtomicFileWriter.writeJson(metaFile, meta, gson);
    }

    public record CachedResult<T>(T payload, Metadata metadata) {}
}
