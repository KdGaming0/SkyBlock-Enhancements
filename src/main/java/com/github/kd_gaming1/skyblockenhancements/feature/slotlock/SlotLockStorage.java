package com.github.kd_gaming1.skyblockenhancements.feature.slotlock;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads and saves locked-slot data to a single JSON file.
 *
 * <p>The whole map is read into memory once on startup and held there; every
 * lookup the rest of the mod does is against the in-memory copy, so the only
 * disk I/O at runtime is an atomic rewrite on each toggle.
 *
 * <p>Buckets are keyed by {@code "<accountUuid>"} (off-SkyBlock / unknown profile)
 * or {@code "<accountUuid>|<profileUuid>"} (a specific SkyBlock profile), so
 * positions locked on one account/profile never affect another.
 */
public final class SlotLockStorage {

    /** Serialised shape of the locked-slots file. */
    public static final class Data {
        public Map<String, Set<Integer>> buckets = new HashMap<>();
    }

    private final Path filePath;
    private Data data = new Data();

    public SlotLockStorage(Path filePath) {
        this.filePath = filePath;
    }

    /** Reads the file into memory, recovering gracefully from corruption. */
    public void load() {
        try {
            Data loaded = JsonFileUtil.readOrCreate(filePath, Data.class, new Data());
            data = (loaded != null) ? loaded : new Data();
            if (data.buckets == null) {
                data.buckets = new HashMap<>();
            }
        } catch (JsonParseException e) {
            data = new Data();
            backupBrokenFile();
            SkyblockEnhancements.LOGGER.error(
                    "Locked-slots file is invalid JSON and was reset. A backup was created.", e);
        } catch (IOException e) {
            data = new Data();
            SkyblockEnhancements.LOGGER.error("Failed to load locked slots, starting fresh", e);
        }
    }

    public Data data() {
        return data;
    }

    /** Atomically rewrites the whole file. Cheap — the data set is tiny. */
    public void save() {
        try {
            JsonFileUtil.writeAtomic(filePath, data);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save locked slots", e);
        }
    }

    private void backupBrokenFile() {
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".broken");
        try {
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to back up broken locked-slots file", e);
        }
    }
}
