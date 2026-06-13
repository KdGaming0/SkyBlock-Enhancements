package com.github.kd_gaming1.skyblockenhancements.mixin;

import eu.midnightdust.lib.config.EntryInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(EntryInfo.class)
public class EntryInfoMixin {

    @Final
    @Shadow
    public Field field;

    @Inject(method = "updateFieldValue", at = @At("TAIL"), remap = false)
    private void onUpdateFieldValue(CallbackInfo ci) {
        // In 26.1 the lightmap render state is recalculated each frame, so no explicit
        // dirty notification is required. The fullbright mixin in LightTextureMixin
        // reads the config directly during Lightmap#render.
    }
}
