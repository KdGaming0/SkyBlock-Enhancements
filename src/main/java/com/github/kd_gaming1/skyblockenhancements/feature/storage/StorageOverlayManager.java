package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * In-memory cache of all captured storage pages.
 *
 * <p>Reads vanilla container slots on screen open, classifies the title,
 * and maintains a map keyed by {@code pageId}.
 */
public class StorageOverlayManager {

    private final StorageSnapshotStorage storage;
    private final Map<String, StorageSnapshot> snapshotMap = new LinkedHashMap<>();
    private volatile String activePageId = null;
    private volatile long snapshotVersion = 0;

    public StorageOverlayManager(StorageSnapshotStorage storage) {
        this.storage = storage;
    }

    /** Loads persisted snapshots for the given profile into memory. */
    public void loadProfile(String profileId) {
        storage.load(profileId);
        List<StorageSnapshot> loaded = storage.toSnapshots();
        synchronized (snapshotMap) {
            snapshotMap.clear();
            for (StorageSnapshot snap : loaded) {
                snapshotMap.put(snap.pageId, snap);
            }
        }
    }

    /** Persists the current in-memory cache to disk. */
    public void saveToStorage(String profileId) {
        List<StorageSnapshot> list;
        synchronized (snapshotMap) {
            list = new ArrayList<>(snapshotMap.values());
        }
        storage.fromSnapshots(profileId, list, SkyblockEnhancementsConfig.storageSnapshotHistoryPages);
        storage.save(profileId);
    }

    /** Classifies the screen title and stores it as the active page ID. */
    public Optional<StorageTitleParser.ParsedTitle> classifyTitle(String title) {
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(title);
        parsed.ifPresent(p -> activePageId = p.pageId());
        return parsed;
    }

    /** Returns the currently active page ID (the one whose vanilla screen is open). */
    public String getActivePageId() {
        return activePageId;
    }

    /** Clears the active page ID (called when the screen closes). */
    public void clearActivePage() {
        activePageId = null;
    }

    /**
     * Captures the current container slots into a snapshot.
     *
     * @param parsed   the parsed title metadata
     * @param slots    all container slots (excluding player inventory)
     * @param lookup   registry lookup for NBT encoding
     */
    /** Returns a monotonically increasing version counter for cache invalidation. */
    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void capturePage(StorageTitleParser.ParsedTitle parsed, List<Slot> slots, HolderLookup.Provider lookup) {
        List<StorageSlotData> slotData = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            String base64 = NbtItemStackCodec.encode(stack, lookup);
            slotData.add(new StorageSlotData(slot.index, base64));
        }

        StorageSnapshot snap = new StorageSnapshot(
                parsed.pageId(),
                parsed.type(),
                parsed.pageNumber(),
                parsed.rawTitle(),
                System.currentTimeMillis(),
                slotData);

        synchronized (snapshotMap) {
            snapshotMap.put(parsed.pageId(), snap);
            snapshotVersion++;
        }
    }

    /** Returns an unmodifiable view of all cached snapshots. */
    public List<StorageSnapshot> getSnapshots() {
        synchronized (snapshotMap) {
            return List.copyOf(snapshotMap.values());
        }
    }

    /** Returns a specific snapshot by page ID, or null. */
    public StorageSnapshot getSnapshot(String pageId) {
        synchronized (snapshotMap) {
            return snapshotMap.get(pageId);
        }
    }

    /** Resolves lazy ItemStacks for all slots in all snapshots. */
    public void resolveAllStacks(HolderLookup.Provider lookup) {
        synchronized (snapshotMap) {
            for (StorageSnapshot snap : snapshotMap.values()) {
                for (StorageSlotData slot : snap.slots) {
                    if (!slot.isResolved() && !slot.isEmpty()) {
                        ItemStack stack = NbtItemStackCodec.decode(slot.itemBase64, lookup);
                        slot.setCachedStack(stack);
                    }
                }
            }
        }
    }

    /** Clears the entire in-memory cache. */
    public void clearCache() {
        synchronized (snapshotMap) {
            snapshotMap.clear();
            snapshotVersion++;
        }
        activePageId = null;
    }

    /** Attempts to derive a profile identifier for file isolation. */
    public static String resolveProfileId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "unknown";
        }
        // Fall back to Minecraft UUID; hm-api profile UUID can be wired later.
        return mc.player.getUUID().toString();
    }
}
