package com.github.kd_gaming1.skyblockenhancements.mixin.potionoverlay;

import com.github.kd_gaming1.skyblockenhancements.feature.potionoverlay.PotionOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a configurable overlay tint on slots containing disabled potion effects
 * in the Hypixel SkyBlock "Toggle Potion Effects" GUI.
 *
 * <p>The injection runs at {@code TAIL} of {@code renderSlot} so the tint is drawn
 * on top of the item without interfering with vanilla rendering or other mods.
 */
@Mixin(AbstractContainerScreen.class)
public class PotionOverlayMixin {

    @Inject(
            method = "renderSlot",
            at = @At("TAIL")
    )
    private void sbe$onRenderSlot(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        //noinspection unchecked
        PotionOverlayRenderer.render(graphics, (AbstractContainerScreen<?>) (Object) this, slot);
    }
}
