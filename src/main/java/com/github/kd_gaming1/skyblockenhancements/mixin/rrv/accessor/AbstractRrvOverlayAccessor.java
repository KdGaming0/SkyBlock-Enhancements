package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractRrvOverlay.class)
public interface AbstractRrvOverlayAccessor {

    @Accessor(value = "x", remap = false)
    int sbe$getX();

    @Accessor(value = "width", remap = false)
    int sbe$getWidth();

    @Accessor(value = "effectiveX", remap = false)
    int sbe$getEffectiveX();

    @Accessor(value = "effectiveWidth", remap = false)
    int sbe$getEffectiveWidth();

    @Accessor("enabled")
    boolean sbe$getRawEnabled();
}