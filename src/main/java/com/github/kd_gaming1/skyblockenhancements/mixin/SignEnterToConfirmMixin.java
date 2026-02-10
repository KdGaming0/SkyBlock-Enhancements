package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractSignEditScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEnterToConfirmMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$enterToConfirm(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!SkyblockEnhancementsConfig.enterToConfirmSign) return;
        if (!keyEvent.isConfirmation()) return;
        if (keyEvent.hasShiftDown()) return;

        AbstractSignEditScreen self = (AbstractSignEditScreen) (Object) this;

        if (!SkyblockEnhancementsConfig.enterToConfirmAllSigns && !sbe$isHypixelInputSign(self)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != self) return;

        ((AbstractSignEditScreenAccessor) self).sbe$invokeOnDone();
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean sbe$isHypixelInputSign(AbstractSignEditScreen screen) {
        String[] lines = ((AbstractSignEditScreenAccessor) screen).sbe$getMessages();
        if (lines == null) return false;

        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;

            int visible = 0;
            int carets = 0;

            for (int i = 0, len = line.length(); i < len; i++) {
                char c = line.charAt(i);
                if (c == ' ') continue;

                visible++;
                if (c == '^') {
                    carets++;

                    if (visible >= 5 && (carets * 10) >= (visible * 6)) {
                        return true;
                    }
                }
            }

            if (visible >= 5 && (carets * 10) >= (visible * 6)) {
                return true;
            }
        }
        return false;
    }
}