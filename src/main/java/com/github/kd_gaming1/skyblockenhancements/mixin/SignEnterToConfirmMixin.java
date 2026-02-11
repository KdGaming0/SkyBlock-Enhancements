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

import java.util.regex.Pattern;

/**
 * Mixin to allow confirming sign edits with Enter when a Hypixel-style input sign is detected.
 * Behavior:
 * - If `SkyblockEnhancementsConfig.enterToConfirmSign` is enabled and the user presses Enter
 *   (without Shift), this mixin can trigger the sign's confirmation action.
 * - When `enterToConfirmAllSigns` is enabled, skips the check for Hypixel input sing.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEnterToConfirmMixin {

    @Unique
    private static final Pattern HYPIXEL_CARETS_PATTERN = Pattern.compile("(?:\\^\\s*){8,}");

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
            if (line == null) continue;
            if (HYPIXEL_CARETS_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }
}