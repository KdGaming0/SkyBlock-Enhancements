package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

/**
 * In-memory cache of all known storage pages.
 *
 * <p>This is the single source of truth for the overlay renderer.
 * Pages are keyed by {@link StoragePageSlot} and kept in sorted order.
 */
public final class StorageData {
    public static final StorageData INSTANCE = new StorageData();

    private final SortedMap<StoragePageSlot, StorageInventory> inventories = new ConcurrentSkipListMap<>();
    private final List<Runnable> dirtyListeners = new CopyOnWriteArrayList<>();

    private StorageData() {}

    public void updateInventory(StoragePageSlot slot, String title, @Nullable VirtualInventory inventory) {
        inventories.merge(slot,
                new StorageInventory(title, slot, inventory),
                (old, neu) -> new StorageInventory(
                        neu.title() != null ? neu.title() : old.title(),
                        slot,
                        inventory != null ? inventory : old.inventory()));
        markDirty();
    }

    public boolean hasInventory(StoragePageSlot slot) {
        return inventories.containsKey(slot);
    }

    public @Nullable StorageInventory getInventory(StoragePageSlot slot) {
        return inventories.get(slot);
    }

    public void removeInventory(StoragePageSlot slot) {
        inventories.remove(slot);
        markDirty();
    }

    public void clear() {
        inventories.clear();
        markDirty();
    }

    public SortedMap<StoragePageSlot, StorageInventory> getInventories() {
        return Collections.unmodifiableSortedMap(inventories);
    }

    public void markDirty() {
        for (Runnable listener : dirtyListeners) {
            listener.run();
        }
    }

    public void markDirty(CompletableFuture<?> waitFor) {
        waitFor.whenComplete((a, b) -> markDirty());
    }

    public void addDirtyListener(Runnable listener) {
        dirtyListeners.add(listener);
    }

    /**
     * Represents a single cached page: a title plus an optional resolved inventory.
     * A {@code null} inventory means the page title is known but it has never been opened.
     */
    public record StorageInventory(String title, StoragePageSlot slot, @Nullable VirtualInventory inventory) {}
}
