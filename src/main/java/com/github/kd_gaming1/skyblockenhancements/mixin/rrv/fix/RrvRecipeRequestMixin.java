package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.ClientRecipeManager;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses RRV's server recipe request when SkyBlock Enhancements provides recipes locally.
 *
 * <p>Without this mixin, every lobby switch on Hypixel triggers a
 * {@code "RRV cannot request recipes from a server without RVV installed!"} chat message because
 * RRV tries to request recipes from the server, fails (no RVV), and notifies the player. Since we
 * inject recipes via cache spoofing, the request is unnecessary and the message is noise.
 */
@SuppressWarnings("UnstableApiUsage")
@Mixin(ClientRecipeManager.class)
public class RrvRecipeRequestMixin {

    @Inject(method = "requestServerRrvData", at = @At("HEAD"), cancellable = true, remap = false)
    private void sbe$skipServerRequest(CallbackInfo ci) {
        if (RrvCompat.isActive()) {
            ci.cancel();
        }
    }
}