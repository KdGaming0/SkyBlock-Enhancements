package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.storage;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.ContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.HasContainerOverlay;
import com.github.kd_gaming1.skyblockenhancements.gui.storage.Rect;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the storage overlay bounds with RRV so the item list renders around it
 * instead of behind it.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1100)
public class RrvStorageOverlayMixin {

    private static final Identifier BLOCKING_ID =
            Identifier.fromNamespaceAndPath("skyblock_enhancements", "storage_overlay");

    @Inject(method = "init()V", at = @At("TAIL"))
    private void sbe$registerRrvBlocking(CallbackInfo ci) {
        ContainerOverlay overlay = ((HasContainerOverlay) this).skyBlock_Enhancements$getSbeOverlay();
        if (overlay == null) return;

        var bounds = overlay.getBounds();
        if (bounds.isEmpty()) return;

        Rect rect = bounds.get(0);
        OverlayManager.INSTANCE.setGuiBlocking(
                new BlockingGuiComponent(BLOCKING_ID, rect.x, rect.y, rect.width, rect.height));
    }
}
