package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.IrisCompat;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.chat.Component;

@Mixin(OptionInstance.class)
public class OptionInstanceMixin<T> {

    @Shadow @Final
    Component caption;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void overrideGamma(CallbackInfoReturnable<T> cir) {
        if (!SkyblockEnhancementsConfig.enableFullbright) return;
        if (!IrisCompat.isShadersActive() && !SkyblockEnhancementsConfig.fullbrightUseGamma) return;
        if (!(caption.getContents() instanceof TranslatableContents tc)
                || !tc.getKey().equals("options.gamma")) return;
        cir.setReturnValue((T) Double.valueOf(
                (SkyblockEnhancementsConfig.fullbrightStrength / 100.0) * 15.0));
    }
}