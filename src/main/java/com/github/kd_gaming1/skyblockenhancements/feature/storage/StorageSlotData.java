package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Immutable data for a single slot inside a storage page.
 *
 * <p>{@code itemBase64} is the persisted form; {@code cachedStack} is resolved
 * lazily when the client world (and therefore the registry lookup) is available.
 */
public final class StorageSlotData {

    public final int slotIndex;
    public final String itemBase64;

    private transient ItemStack cachedStack = ItemStack.EMPTY;
    private transient boolean resolved = false;

    public StorageSlotData(int slotIndex, String itemBase64) {
        this.slotIndex = slotIndex;
        this.itemBase64 = itemBase64;
    }

    /** Returns the resolved ItemStack, or EMPTY if not yet decoded. */
    public ItemStack getCachedStack() {
        return cachedStack;
    }

    public void setCachedStack(ItemStack stack) {
        this.cachedStack = stack != null ? stack : ItemStack.EMPTY;
        this.resolved = true;
    }

    public boolean isResolved() {
        return resolved;
    }

    public boolean isEmpty() {
        return itemBase64 == null || itemBase64.isEmpty();
    }
}
