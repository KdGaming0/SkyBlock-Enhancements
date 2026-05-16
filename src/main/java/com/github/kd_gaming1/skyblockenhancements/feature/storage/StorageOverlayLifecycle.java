package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.List;
import java.util.Optional;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.ContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageOverlayGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/**
 * Creates and caches storage overlays when a recognised container screen opens.
 */
public final class StorageOverlayLifecycle {

    private StorageOverlayLifecycle() {}

    /**
     * Called by the mixin when a container screen initializes.
     * If the screen is a recognised storage page, creates and returns an overlay.
     */
    public static ContainerOverlay createOverlay(AbstractContainerScreen<?> screen) {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard_Test) {
            return null;
        }

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) {
            return null;
        }

        StoragePageSlot activeSlot = parsed.get().slot();
        Minecraft mc = Minecraft.getInstance();

        if (parsed.get().isOverview()) {
            for (int i = 0; i < StoragePageSlot.COUNT; i++) {
                StoragePageSlot slot = new StoragePageSlot(i);
                if (!StorageData.INSTANCE.hasInventory(slot)) {
                    StorageData.INSTANCE.updateInventory(slot, slot.defaultName(), null);
                }
            }
            rememberOverview(screen);
            return new StorageOverlayGui(screen, null);
        }

        // Capture the live page data
        if (activeSlot != null && mc.level != null) {
            rememberPage(screen, activeSlot, rawTitle);
        }

        return new StorageOverlayGui(screen, activeSlot);
    }

    private static void rememberPage(AbstractContainerScreen<?> screen, StoragePageSlot slot, String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (net.minecraft.world.inventory.Slot s : screen.getMenu().slots) {
            if (s.container != mc.player.getInventory()) {
                while (stacks.size() <= s.index) stacks.add(net.minecraft.world.item.ItemStack.EMPTY);
                stacks.set(s.index, s.getItem().copy());
            }
        }
        // Trim to valid size
        int rows = Math.clamp((stacks.size() + 8) / 9, 1, 6);
        int target = rows * 9;
        while (stacks.size() < target) stacks.add(net.minecraft.world.item.ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);

        VirtualInventory vinv = new VirtualInventory(stacks);
        StorageData.INSTANCE.updateInventory(slot, title, vinv);
        StorageData.INSTANCE.markDirty(vinv.getSerializationFuture());
    }

    private static void rememberOverview(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (int i = 0; i < screen.getMenu().getItems().size(); i++) {
            net.minecraft.world.item.ItemStack stack = screen.getMenu().getItems().get(i);
            if (stack.isEmpty()) continue;
            StoragePageSlot slot = StoragePageSlot.fromOverviewSlotIndex(i);
            if (slot == null) continue;

            if (!StorageData.INSTANCE.hasInventory(slot)) {
                StorageData.INSTANCE.updateInventory(slot, slot.defaultName(), null);
            }
        }
        StorageData.INSTANCE.markDirty();
    }

    public static void onOverviewPacketReceived(AbstractContainerScreen<?> screen) {
        rememberOverview(screen);
    }
}
