package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractSignEditScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEnterToConfirmMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$enterToConfirm(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!SkyblockEnhancementsConfig.enterToConfirmSign) return;

        if (!keyEvent.isConfirmation()) return;

        // Shift + Enter should stay vanilla (next line)
        if (keyEvent.hasShiftDown()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == (Object) this) {
            ((AbstractSignEditScreenAccessor) this)
                    .sbe$invokeOnDone();

            cir.setReturnValue(true);
        }
    }
}
