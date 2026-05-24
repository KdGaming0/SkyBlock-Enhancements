package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.bookmark;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.config.options.OverlayDisplay;
import cc.cassian.rrv.common.config.options.SidePanel;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SidePanelOverlay.class)
public class SidePanelOverlayMixin {

    @Inject(
            method = "setEnabled",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void sbe$keepBookmarksEnabled(boolean enabled, CallbackInfo ci) {
        if (!enabled
                && SkyblockEnhancementsConfig.keepBookmarksVisibleWhenSearching
                && Configs.CLIENT_SETTINGS.isShowOverlays() == OverlayDisplay.WHEN_SEARCHING) {
            ci.cancel();
        }
    }

    @Inject(
            method = "updateSidePanelIndex",
            at = @At("RETURN"),
            remap = false)
    private void sbe$autoHideEmptyPanel(String reason, CallbackInfo ci) {
        if (!SidePanelOverlay.showBookmarks() || !SkyblockEnhancementsConfig.hideEmptyBookmarkPanel) {
            return;
        }

        if (((SidePanelOverlay) (Object) this).availableItems().isEmpty()) {
            Configs.CLIENT_SETTINGS.setSidePanel(SidePanel.DISABLED);
        }
    }
}
