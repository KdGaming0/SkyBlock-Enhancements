package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractRrvItemListOverlay.class, remap = false)
public interface AbstractRrvItemListOverlayAccessor {

    @Accessor("startIndex")
    void sbe$setStartIndex(int startIndex);

    @Accessor("itemStartX")
    void sbe$setItemStartX(int value);

    @Accessor("itemEndX")
    void sbe$setItemEndX(int value);
}