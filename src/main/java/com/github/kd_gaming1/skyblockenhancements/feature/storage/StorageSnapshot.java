package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of one storage page's contents and metadata.
 */
public final class StorageSnapshot {

    public final String pageId;
    public final StoragePageType type;
    public final int pageNumber;
    public final String titleText;
    public final long capturedAt;
    public final List<StorageSlotData> slots;

    public StorageSnapshot(
            String pageId,
            StoragePageType type,
            int pageNumber,
            String titleText,
            long capturedAt,
            List<StorageSlotData> slots) {
        this.pageId = pageId;
        this.type = type;
        this.pageNumber = pageNumber;
        this.titleText = titleText;
        this.capturedAt = capturedAt;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }
}
