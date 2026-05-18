package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.bookmark;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.config.options.OverlayDisplay;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor.AbstractRrvOverlayAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRrvOverlay.class)
public class AbstractRrvOverlayMixin {

    @Inject(
            method = "isEnabled",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void sbe$sidePanelIgnoreGlobalHide(CallbackInfoReturnable<Boolean> cir) {
        AbstractRrvOverlay self = (AbstractRrvOverlay) (Object) this;

        if (!cir.getReturnValue()
                && ((AbstractRrvOverlayAccessor) self).sbe$getRawEnabled()
                && self instanceof SidePanelOverlay
                && SkyblockEnhancementsConfig.keepBookmarksVisibleWhenSearching
                && Configs.CLIENT_SETTINGS.isShowOverlays() == OverlayDisplay.WHEN_SEARCHING) {
            cir.setReturnValue(true);
        }
    }
}