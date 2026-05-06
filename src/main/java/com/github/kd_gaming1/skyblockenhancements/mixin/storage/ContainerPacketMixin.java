package com.github.kd_gaming1.skyblockenhancements.mixin.storage;

import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageOverlayManager;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StoragePageType;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageTitleParser;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.Slot;
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
        if (!SkyblockEnhancementsConfig.enableStorageDashboard_CHANGEwhenRELASE) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getMenu().containerId != packet.containerId()) return;
        if (mc.level == null) return;

        StorageOverlayManager manager = StorageFeature.getManager();
        if (manager == null) return;

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = manager.classifyTitle(rawTitle);
        if (parsed.isEmpty()) return;

        // Skip the /storage overview screen — it contains selector buttons, not real items.
        if (parsed.get().type() == StoragePageType.STORAGE
                && "Storage".equalsIgnoreCase(parsed.get().rawTitle())) {
            return;
        }

        List<Slot> containerSlots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
                containerSlots.add(slot);
            }
        }
        manager.capturePage(parsed.get(), containerSlots, mc.level.registryAccess());
    }
}
