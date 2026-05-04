package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Gson-serializable root object for persisted storage snapshots.
 */
public final class StorageSnapshotsFileData {

    public String profileId = "unknown";
    public List<StorageSnapshotJson> snapshots = new ArrayList<>();

    /**
     * Flat DTO for Gson serialization of {@link StorageSnapshot}.
     */
    public static final class StorageSnapshotJson {
        public String pageId;
        public String type;
        public int pageNumber;
        public String titleText;
        public long capturedAt;
        public List<StorageSlotJson> slots = new ArrayList<>();
    }

    /**
     * Flat DTO for Gson serialization of {@link StorageSlotData}.
     */
    public static final class StorageSlotJson {
        public int slotIndex;
        public String itemBase64;
    }
}
