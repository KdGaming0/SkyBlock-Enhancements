package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.List;
import java.util.Optional;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.ContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageOverlayGui;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.StorageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/**
 * Orchestrates transitions between the storage overlay, overview screen, and vanilla GUIs.
 */
public final class StorageOverlayLifecycle {

    private static boolean navigatingBetweenPages = false;
    private static boolean openingOverview = false;

    private static AbstractContainerScreen<?> stashedOverviewScreen = null;

    private StorageOverlayLifecycle() {}

    /**
     * Called by the mixin when a container screen initializes.
     * If the screen is a recognised storage page, creates and returns an overlay.
     */
    public static ContainerOverlay createOverlay(
            AbstractContainerScreen<?> screen) {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard) {
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
            if (!navigatingBetweenPages) {
                stashedOverviewScreen = screen;
                mc.execute(() -> {
                    if (mc.screen == screen) {
                        requestOverview();
                    }
                });
                return null;
            }
        }

        // Capture the live page data
        if (activeSlot != null && mc.level != null) {
            rememberPage(screen, activeSlot, rawTitle);
        }

        return new StorageOverlayGui(screen, activeSlot);
    }

    /** Called when the user explicitly wants to open the overview (e.g. pressing Escape). */
    public static void requestOverview() {
        openingOverview = true;
        Minecraft.getInstance().setScreen(new StorageOverviewScreen());
    }

    public static AbstractContainerScreen<?> getStashedScreen() {
        return stashedOverviewScreen;
    }

    /** Called when the overlay mixin detects the screen is being removed. */
    public static void onOverlayClosed() {
        if (openingOverview) {
            openingOverview = false;
            return;
        }
        if (navigatingBetweenPages) {
            navigatingBetweenPages = false;
            return;
        }
        // Normal close (e.g. server closed container) — don't force overview
    }

    /** Called when the overview screen itself is closed. */
    public static void onOverviewClosed() {
        navigatingBetweenPages = false;
        openingOverview = false;
    }

    /** Called before sending a navigation command so the backflip is skipped. */
    public static void onNavigateToPage(StoragePageSlot slot) {
        navigatingBetweenPages = true;
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
        int rows = Math.max(1, Math.min(5, (stacks.size() + 8) / 9));
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
        stashedOverviewScreen = null;
    }
}
