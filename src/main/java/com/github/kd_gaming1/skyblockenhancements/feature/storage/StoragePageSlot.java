package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.Optional;
import net.minecraft.client.Minecraft;

/**
 * Typed, comparable key for a single storage page (0–26).
 *
 * <p>Indices 0–8 are Ender Chest pages 1–9.
 * Indices 9–26 are Backpack pages 1–18.
 */
public record StoragePageSlot(int index) implements Comparable<StoragePageSlot> {

    public static final int COUNT = 27;

    public StoragePageSlot {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("Page slot index must be 0.." + (COUNT - 1));
        }
    }

    public boolean isEnderChest() {
        return index < 9;
    }

    public boolean isBackpack() {
        return !isEnderChest();
    }

    public int getPageNumber() {
        return isEnderChest() ? index + 1 : index - 9 + 1;
    }

    public String defaultName() {
        return isEnderChest()
                ? "Ender Chest #" + getPageNumber()
                : "Backpack #" + getPageNumber();
    }

    public void navigateTo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (isEnderChest()) {
            mc.getConnection().sendCommand("ec " + getPageNumber());
        } else {
            mc.getConnection().sendCommand("backpack " + getPageNumber());
        }
    }

    /** Parse from a Hypixel overview hub menu slot index. */
    public static StoragePageSlot fromOverviewSlotIndex(int slot) {
        if (slot >= 9 && slot < 18) return new StoragePageSlot(slot - 9);      // Ender Chests
        if (slot >= 27 && slot < 45) return new StoragePageSlot(slot - 27 + 9); // Backpacks
        return null;
    }

    public static StoragePageSlot ofEnderChest(int page) {
        if (page < 1 || page > 9) throw new IllegalArgumentException("EC page must be 1..9");
        return new StoragePageSlot(page - 1);
    }

    public static StoragePageSlot ofBackpack(int page) {
        if (page < 1 || page > 18) throw new IllegalArgumentException("Backpack page must be 1..18");
        return new StoragePageSlot(page - 1 + 9);
    }

    /** Attempt to parse from a legacy page ID string. */
    public static Optional<StoragePageSlot> fromPageId(String pageId) {
        if (pageId == null || pageId.isEmpty()) return Optional.empty();
        try {
            if (pageId.startsWith("ender_")) {
                return Optional.of(ofEnderChest(Integer.parseInt(pageId.substring(6))));
            }
            if (pageId.startsWith("backpack_")) {
                return Optional.of(ofBackpack(Integer.parseInt(pageId.substring(9))));
            }
            if (pageId.startsWith("storage_")) {
                int num = Integer.parseInt(pageId.substring(8));
                // Storage hub pages are ender chests in the new model
                if (num >= 1 && num <= 9) return Optional.of(ofEnderChest(num));
            }
        } catch (NumberFormatException ignored) {
        }
        return Optional.empty();
    }

    @Override
    public int compareTo(StoragePageSlot o) {
        return Integer.compare(this.index, o.index);
    }
}
