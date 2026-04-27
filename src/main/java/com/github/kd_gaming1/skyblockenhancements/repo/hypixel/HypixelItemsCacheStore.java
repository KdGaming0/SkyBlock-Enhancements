package com.github.kd_gaming1.skyblockenhancements.repo.hypixel;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.RepoDiskCache;
import com.github.kd_gaming1.skyblockenhancements.repo.cache.VersionedJsonCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes the Hypixel items snapshot to disk. The on-disk format shares its schema
 * version with {@link RepoDiskCache}, so bumping the NEU cache version also invalidates the
 * Hypixel cache.
 *
 * <p>All JSON I/O is delegated to {@link VersionedJsonCache} for atomic writes and schema
 * validation.
 */
public final class HypixelItemsCacheStore {

    /** Skip re-fetching when the cache file was written within this period. */
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1_000L;

    private static final Gson GSON = new GsonBuilder().create();

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
        VersionedJsonCache<HypixelItemsSnapshot> cache = new VersionedJsonCache<>(GSON, HypixelItemsSnapshot.class);
        VersionedJsonCache.CachedResult<HypixelItemsSnapshot> result = cache.load(cacheFile, RepoDiskCache.CACHE_VERSION);
        if (result == null) return null;
        return result.payload();
    }

    /** Writes the snapshot atomically. Swallows I/O errors with a warn log. */
    public static void save(Path cacheFile, HypixelItemsSnapshot data) {
        try {
            VersionedJsonCache<HypixelItemsSnapshot> cache = new VersionedJsonCache<>(GSON, HypixelItemsSnapshot.class);
            VersionedJsonCache.Metadata meta = new VersionedJsonCache.Metadata(
                    null, System.currentTimeMillis(), RepoDiskCache.CACHE_VERSION);
            cache.save(cacheFile, data, meta);
        } catch (Exception e) {
            LOGGER.warn("Failed to save Hypixel items cache: {}", e.getMessage());
        }
    }
}
