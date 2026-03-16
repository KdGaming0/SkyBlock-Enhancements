package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class GammaMixin {

    @Final
    @Shadow
    private OptionInstance<Double> gamma;

    @Unique
    private double savedGamma = 1.0;

    @Inject(method = "gamma", at = @At("RETURN"))
    private void applyFullbright(CallbackInfoReturnable<OptionInstance<Double>> cir) {
        double current = gamma.get();
        double target = SkyblockEnhancementsConfig.fullbrightStrength * 100.0;

        if (SkyblockEnhancementsConfig.enableFullbright) {
            if (current < target) {
                savedGamma = current;
            }
            if (current != target) gamma.set(target);
        } else {
            if (current == target) {
                gamma.set(savedGamma);
            }
        }
    }
}