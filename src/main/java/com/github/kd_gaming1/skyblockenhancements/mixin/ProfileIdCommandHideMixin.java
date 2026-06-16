package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.util.ProfileIdTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the outgoing {@code /profileid} command sent by {@link ProfileIdTracker}.
 *
 * <p>The tracker sets an internal flag immediately before sending the command; this mixin
 * cancels the packet only when that flag is set, so a player manually typing the command
 * is unaffected.
 */
@Mixin(ClientPacketListener.class)
public class ProfileIdCommandHideMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void skyblockenhancements$hideProfileIdCommand(String command, CallbackInfo ci) {
        if ("profileid".equals(command) && ProfileIdTracker.shouldSuppressOutgoingCommand("/" + command)) {
            ci.cancel();
        }
    }
}
