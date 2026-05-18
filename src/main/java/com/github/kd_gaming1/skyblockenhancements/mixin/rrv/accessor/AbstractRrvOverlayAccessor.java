package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractRrvOverlay.class, remap = false)
public interface AbstractRrvOverlayAccessor {

    @Accessor("x")
    int sbe$getX();

    @Accessor("width")
    int sbe$getWidth();

    @Accessor("effectiveX")
    int sbe$getEffectiveX();

    @Accessor("effectiveWidth")
    int sbe$getEffectiveWidth();

    @Accessor("enabled")
    boolean sbe$getRawEnabled();

    @Accessor("x")
    void sbe$setX(int x);

    @Accessor("width")
    void sbe$setWidth(int width);
}