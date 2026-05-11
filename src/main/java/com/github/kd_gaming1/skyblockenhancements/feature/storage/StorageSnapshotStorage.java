package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

/**
 * JSON persistence for the in-memory {@link StorageData} cache.
 *
 * <p>Version 2 format (simplified, no DTOs):
 * <pre>
 * {
 *   "profileId": "...",
 *   "version": 2,
 *   "pages": [
 *     {"slotIndex": 0, "title": "Ender Chest #1", "inventoryBase64": "..."},
 *     ...
 *   ]
 * }
 * </pre>
 */
public final class StorageSnapshotStorage {
    private final Path baseDir;

    public StorageSnapshotStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    public void save(String profileId, StorageData data) {
        Path file = baseDir.resolve(profileId + ".json");
        JsonObject root = new JsonObject();
        root.addProperty("profileId", profileId);
        root.addProperty("version", 2);

        JsonArray pages = new JsonArray();
        for (var entry : data.getInventories().entrySet()) {
            JsonObject page = new JsonObject();
            page.addProperty("slotIndex", entry.getKey().index());
            page.addProperty("title", entry.getValue().title());

            VirtualInventory inv = entry.getValue().inventory();
            if (inv != null) {
                try {
                    byte[] bytes = inv.getSerializationFuture().get();
                    if (bytes.length > 0) {
                        page.addProperty("inventoryBase64",
                                Base64.getEncoder().encodeToString(bytes));
                    }
                } catch (Exception e) {
                    SkyblockEnhancements.LOGGER.warn("Failed to serialize inventory for page {}", entry.getKey(), e);
                }
            }
            pages.add(page);
        }
        root.add("pages", pages);

        try {
            Files.createDirectories(baseDir);
            String json = new Gson().toJson(root);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save storage data", e);
        }
    }

    public void load(String profileId, StorageData target) {
        Path file = baseDir.resolve(profileId + ".json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file);
            JsonObject root = new Gson().fromJson(json, JsonObject.class);
            if (root == null) return;

            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            JsonArray pages = root.getAsJsonArray("pages");
            if (pages == null) return;

            HolderLookup.Provider lookup = null;
            if (Minecraft.getInstance().level != null) {
                lookup = Minecraft.getInstance().level.registryAccess();
            }

            for (JsonElement el : pages) {
                if (!el.isJsonObject()) continue;
                JsonObject page = el.getAsJsonObject();
                int idx = page.get("slotIndex").getAsInt();
                String title = page.has("title") ? page.get("title").getAsString() : null;
                if (title == null) title = new StoragePageSlot(idx).defaultName();

                StoragePageSlot slot = new StoragePageSlot(idx);

                // SKIP pages that already have live data (don't overwrite fresh captures with stale file data)
                if (target.hasInventory(slot) && target.getInventory(slot).inventory() != null) {
                    continue;
                }

                VirtualInventory inv = null;
                if (page.has("inventoryBase64")) {
                    String b64 = page.get("inventoryBase64").getAsString();
                    if (lookup != null) {
                        try {
                            byte[] bytes = Base64.getDecoder().decode(b64);
                            inv = VirtualInventory.deserialize(bytes, lookup);
                        } catch (Exception e) {
                            SkyblockEnhancements.LOGGER.warn("Failed to deserialize inventory for page {}", slot, e);
                        }
                    }
                }
                target.updateInventory(slot, title, inv);
            }
        } catch (JsonParseException e) {
            backupBrokenFile(file);
            SkyblockEnhancements.LOGGER.error("Storage snapshots file is invalid JSON and was reset. A backup was created.", e);
        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.error("Failed to load storage data, starting fresh", e);
        }
    }

    private void backupBrokenFile(Path filePath) {
        Path backup = filePath.resolveSibling(filePath.getFileName() + ".broken");
        try {
            Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to back up broken storage snapshots file", e);
        }
    }
}
