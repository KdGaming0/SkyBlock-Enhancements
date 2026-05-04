package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JSON persistence for storage snapshots.
 *
 * <p>Thread-safe via {@link AtomicReference}. Broken files are backed up
 * with a {@code .broken} suffix before resetting.
 */
public class StorageSnapshotStorage {

    private final Path baseDir;
    private final AtomicReference<StorageSnapshotsFileData> dataRef =
            new AtomicReference<>(new StorageSnapshotsFileData());

    public StorageSnapshotStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    public void load(String profileId) {
        Path filePath = baseDir.resolve(profileId + ".json");
        try {
            StorageSnapshotsFileData loaded =
                    JsonFileUtil.readOrCreate(filePath, StorageSnapshotsFileData.class, new StorageSnapshotsFileData());
            dataRef.set(loaded != null ? loaded : new StorageSnapshotsFileData());
        } catch (JsonParseException e) {
            backupBrokenFile(filePath);
            dataRef.set(new StorageSnapshotsFileData());
            SkyblockEnhancements.LOGGER.error("Storage snapshots file is invalid JSON and was reset. A backup was created.", e);
        } catch (IOException e) {
            dataRef.set(new StorageSnapshotsFileData());
            SkyblockEnhancements.LOGGER.error("Failed to load storage snapshots, starting fresh", e);
        }
    }

    public void save(String profileId) {
        Path filePath = baseDir.resolve(profileId + ".json");
        try {
            JsonFileUtil.writeAtomic(filePath, dataRef.get());
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save storage snapshots", e);
        }
    }

    public StorageSnapshotsFileData getData() {
        return dataRef.get();
    }

    public void setData(StorageSnapshotsFileData data) {
        dataRef.set(data != null ? data : new StorageSnapshotsFileData());
    }

    public List<StorageSnapshot> toSnapshots() {
        StorageSnapshotsFileData data = dataRef.get();
        if (data == null || data.snapshots == null) {
            return Collections.emptyList();
        }

        List<StorageSnapshot> result = new ArrayList<>(data.snapshots.size());
        for (StorageSnapshotsFileData.StorageSnapshotJson json : data.snapshots) {
            List<StorageSlotData> slots = new ArrayList<>(json.slots.size());
            for (StorageSnapshotsFileData.StorageSlotJson slotJson : json.slots) {
                slots.add(new StorageSlotData(slotJson.slotIndex, slotJson.itemBase64));
            }

            StoragePageType type;
            try {
                type = StoragePageType.valueOf(json.type);
            } catch (IllegalArgumentException e) {
                type = StoragePageType.STORAGE;
            }

            result.add(new StorageSnapshot(
                    json.pageId, type, json.pageNumber, json.titleText, json.capturedAt, slots));
        }
        return result;
    }

    public void fromSnapshots(String profileId, List<StorageSnapshot> snapshots, int maxPagesPerType) {
        StorageSnapshotsFileData data = new StorageSnapshotsFileData();
        data.profileId = profileId;

        // Trim to max pages per type to prevent unbounded growth
        if (maxPagesPerType > 0) {
            snapshots = trimByType(snapshots, maxPagesPerType);
        }

        for (StorageSnapshot snap : snapshots) {
            StorageSnapshotsFileData.StorageSnapshotJson json = new StorageSnapshotsFileData.StorageSnapshotJson();
            json.pageId = snap.pageId;
            json.type = snap.type.name();
            json.pageNumber = snap.pageNumber;
            json.titleText = snap.titleText;
            json.capturedAt = snap.capturedAt;

            for (StorageSlotData slot : snap.slots) {
                StorageSnapshotsFileData.StorageSlotJson slotJson = new StorageSnapshotsFileData.StorageSlotJson();
                slotJson.slotIndex = slot.slotIndex;
                slotJson.itemBase64 = slot.itemBase64;
                json.slots.add(slotJson);
            }
            data.snapshots.add(json);
        }
        dataRef.set(data);
    }

    private static List<StorageSnapshot> trimByType(List<StorageSnapshot> snapshots, int max) {
        java.util.Map<StoragePageType, List<StorageSnapshot>> byType = new java.util.EnumMap<>(StoragePageType.class);
        for (StorageSnapshot s : snapshots) {
            byType.computeIfAbsent(s.type, k -> new ArrayList<>()).add(s);
        }

        List<StorageSnapshot> trimmed = new ArrayList<>();
        for (List<StorageSnapshot> list : byType.values()) {
            // Keep most recent
            list.sort((a, b) -> Long.compare(b.capturedAt, a.capturedAt));
            trimmed.addAll(list.subList(0, Math.min(list.size(), max)));
        }
        return trimmed;
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
