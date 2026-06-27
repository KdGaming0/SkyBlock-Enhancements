package com.github.kd_gaming1.skyblockenhancements.feature.slotmanage;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads and saves per-bucket slot state (locks + hotbar binds) to a single JSON file.
 *
 * <p>The whole map is read into memory once on startup; every lookup afterwards hits the in-memory
 * copy, so the only runtime disk I/O is an atomic rewrite on each toggle/bind change.
 *
 * <p>Buckets are keyed by {@code "<accountUuid>"} (off-SkyBlock / unknown profile) or
 * {@code "<accountUuid>|<profileUuid>"} (a specific SkyBlock profile). Both lock indices and bind
 * keys use {@link net.minecraft.world.inventory.Slot#getContainerSlot()} (0–40), which is stable
 * across every container screen.
 */
public final class SlotStorage {

    /** One profile bucket: locked inventory positions and inventory→hotbar binds. */
    public static final class Bucket {
        public Set<Integer> locked = new HashSet<>();
        public Map<Integer, Integer> binds = new HashMap<>();
    }

    /** Serialised shape of the file: {@code { "buckets": { "<key>": { locked, binds } } }}. */
    public static final class Data {
        public Map<String, Bucket> buckets = new HashMap<>();
    }

    private final Path filePath;
    private Data data = new Data();

    public SlotStorage(Path filePath) {
        this.filePath = filePath;
    }

    public Data data() {
        return data;
    }

    /**
     * Reads the file into memory. Recovers gracefully from corruption, and transparently upgrades the
     * legacy slot-lock format ({@code "<key>": [12, 21]}) to the current nested shape, rewriting the
     * file once so existing locks survive the feature merge.
     */
    public void load() {
        if (!Files.exists(filePath)) {
            data = new Data();
            return;
        }
        try {
            JsonObject root;
            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            boolean[] migrated = {false};
            data = fromJson(root, migrated);
            if (migrated[0]) {
                save(); // persist the upgraded shape
            }
        } catch (Exception e) {
            data = new Data();
            backupBrokenFile();
            SkyblockEnhancements.LOGGER.error(
                    "Slot-state file is invalid and was reset. A backup was created.", e);
        }
    }

    private static Data fromJson(JsonObject root, boolean[] migrated) {
        Data result = new Data();
        if (root == null || !root.has("buckets") || !root.get("buckets").isJsonObject()) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("buckets").entrySet()) {
            Bucket bucket = new Bucket();
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                // Legacy format: the bucket value was a bare array of locked indices.
                migrated[0] = true;
                value.getAsJsonArray().forEach(el -> bucket.locked.add(el.getAsInt()));
            } else if (value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                if (obj.has("locked") && obj.get("locked").isJsonArray()) {
                    obj.getAsJsonArray("locked").forEach(el -> bucket.locked.add(el.getAsInt()));
                }
                if (obj.has("binds") && obj.get("binds").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> bind : obj.getAsJsonObject("binds").entrySet()) {
                        bucket.binds.put(Integer.parseInt(bind.getKey()), bind.getValue().getAsInt());
                    }
                }
            }
            if (!bucket.locked.isEmpty() || !bucket.binds.isEmpty()) {
                result.buckets.put(entry.getKey(), bucket);
            }
        }
        return result;
    }

    /** Atomically rewrites the whole file. Cheap — the data set is tiny. */
    public void save() {
        try {
            JsonFileUtil.writeAtomic(filePath, data);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save slot state", e);
        }
    }

    private void backupBrokenFile() {
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".broken");
        try {
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to back up broken slot-state file", e);
        }
    }
}
