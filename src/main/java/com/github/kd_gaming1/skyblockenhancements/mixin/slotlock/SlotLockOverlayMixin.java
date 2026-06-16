package com.github.kd_gaming1.skyblockenhancements.mixin.slotlock;

import com.github.kd_gaming1.skyblockenhancements.feature.slotlock.SlotLockRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the padlock overlay on locked slots at the tail of {@code extractSlot}, mirroring the
 * potion-overlay hook. Kept separate from {@code PotionOverlayMixin} so each overlay stays
 * single-responsibility.
 */
@Mixin(AbstractContainerScreen.class)
public class SlotLockOverlayMixin {

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void sbe$renderLockOverlay(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        SlotLockRenderer.render(graphics, slot);
    }
}
