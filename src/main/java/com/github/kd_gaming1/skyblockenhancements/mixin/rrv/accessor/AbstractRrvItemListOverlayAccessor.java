package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes {@code startIndex} for page-reset after a category filter is applied. */
@Mixin(AbstractRrvItemListOverlay.class)
public interface AbstractRrvItemListOverlayAccessor {

    @Accessor(value = "startIndex", remap = false)
    void sbe$setStartIndex(int startIndex);
}