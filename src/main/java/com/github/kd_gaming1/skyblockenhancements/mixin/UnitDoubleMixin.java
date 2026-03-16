package com.github.kd_gaming1.skyblockenhancements.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(OptionInstance.UnitDouble.class)
public abstract class UnitDoubleMixin {

    @Inject(method = "validateValue(Ljava/lang/Double;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true)
    private void allowUnlimitedGamma(Double value, CallbackInfoReturnable<Optional<Double>> cir) {
        cir.setReturnValue(Optional.of(value));
    }
}