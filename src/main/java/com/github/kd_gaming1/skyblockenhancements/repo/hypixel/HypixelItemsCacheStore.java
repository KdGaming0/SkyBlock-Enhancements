package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.RepoDiskCache;
import com.github.kd_gaming1.skyblockenhancements.repo.cache.VersionedJsonCache;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes the Hypixel items snapshot to disk. The on-disk format shares its schema
 * version with {@link RepoDiskCache}, so bumping the NEU cache version also invalidates the
 * Hypixel cache.
 *
 * <p>All JSON I/O is delegated to {@link VersionedJsonCache} for atomic writes and schema
 * validation. A single {@link VersionedJsonCache} instance is reused across calls to avoid
 * repeatedly constructing Gson and generic type metadata.
 */
public final class HypixelItemsCacheStore {

    /** Skip re-fetching when the cache file was written within this period. */
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1_000L;

    private static final VersionedJsonCache<HypixelItemsSnapshot> CACHE =
            new VersionedJsonCache<>(NeuItemParser.GSON, HypixelItemsSnapshot.class);

    private HypixelItemsCacheStore() {}

    /** Returns {@code true} when the cache file exists and is newer than the TTL. */
    public static boolean isFresh(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) return false;
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            return age < CACHE_TTL_MS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loads the cached snapshot, or {@code null} if the file is missing, malformed, or its
     * schema version is older than {@link RepoDiskCache#CACHE_VERSION}.
     */
    @Nullable
    public static HypixelItemsSnapshot tryLoad(Path cacheFile) {
        VersionedJsonCache.CachedResult<HypixelItemsSnapshot> result =
                CACHE.load(cacheFile, RepoDiskCache.CACHE_VERSION);
        return result != null ? result.payload() : null;
    }

    /**
     * Writes the snapshot atomically.
     *
     * @return {@code true} on success, {@code false} on I/O failure (logged)
     */
    public static boolean save(Path cacheFile, HypixelItemsSnapshot data) {
        try {
            VersionedJsonCache.Metadata meta = new VersionedJsonCache.Metadata(
                    null, System.currentTimeMillis(), RepoDiskCache.CACHE_VERSION);
            CACHE.save(cacheFile, data, meta);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to save Hypixel items cache: {}", e.getMessage());
            return false;
        }
    }
}
