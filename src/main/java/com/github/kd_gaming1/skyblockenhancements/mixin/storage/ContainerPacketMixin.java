package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageData;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayLifecycle;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageSlot;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageTitleParser;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.VirtualInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-captures storage page data when the server sends a full container contents update.
 * This fixes the race condition where {@code init} fires before slot items have arrived.
 */
@Mixin(ClientPacketListener.class)
public class ContainerPacketMixin {

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void sbe$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard_Test) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getMenu().containerId != packet.containerId()) return;

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) return;

        if (parsed.get().isOverview()) {
            StorageOverlayLifecycle.onOverviewPacketReceived(screen);
            return;
        }

        StoragePageSlot slot = parsed.get().slot();
        if (slot == null) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (Slot s : screen.getMenu().slots) {
            if (s.container != mc.player.getInventory()) {
                while (stacks.size() <= s.index) stacks.add(ItemStack.EMPTY);
                stacks.set(s.index, s.getItem().copy());
            }
        }
        int rows = Math.clamp((stacks.size() + 8) / 9, 1, 6);
        int target = rows * 9;
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);

        VirtualInventory vinv = new VirtualInventory(stacks);
        StorageData.INSTANCE.updateInventory(slot, rawTitle, vinv);
        StorageData.INSTANCE.markDirty(vinv.getSerializationFuture());
    }
}
