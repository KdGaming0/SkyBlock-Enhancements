package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackCompatibility.class)
public class PackCompatibilityMixin {

    @Inject(method = "isCompatible", at = @At("HEAD"), cancellable = true)
    private void alwaysCompatible(CallbackInfoReturnable<Boolean> cir) {
        if (SkyblockEnhancementsConfig.disableResourcePackCompatibilityWaring) {
            cir.setReturnValue(true);
        }
    }
}