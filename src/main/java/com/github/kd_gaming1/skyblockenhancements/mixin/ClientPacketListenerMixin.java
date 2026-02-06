package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.multiplayer.ClientPacketListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "verifyCommand", at = @At("RETURN"), cancellable = true)
    private void disableCommandConfirmation(String command, CallbackInfoReturnable<Enum<?>> cir) {
        // Get the return value from Minecraft's command verification
        var ret = cir.getReturnValue();
        if (!(ret instanceof Enum<?> e))
            return;

        String name = e.name();

        if ("SIGNATURE_REQUIRED".equals(name))
            return;

        // Check if config says to disable confirmations
        if (SkyblockEnhancementsConfig.disableCommandConfirmation) {
            // Change any "PERMISSIONS_REQUIRED" result to "NO_ISSUES" (no confirmation needed)
            if (!"NO_ISSUES".equals(name)) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Enum<?> replacement = Enum.valueOf((Class) e.getDeclaringClass(), "NO_ISSUES");
                cir.setReturnValue(replacement);
            }
        }
    }
}