package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.search;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor.AbstractRrvOverlayAccessor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemViewOverlay.class)
public class SearchBarWidthMixin {

    @Shadow(remap = false)
    private SearchBar searchbar;

    @ModifyVariable(
            method = "createSearchbarElement",
            at = @At(value = "STORE"),
            remap = false,
            name = "boxWidth")
    private int sbe$widenSearchBar(int boxWidth) {
        if (!RrvCompat.isActive()) return boxWidth;
        if (!SkyblockEnhancementsConfig.wideRrvSearchBar) return boxWidth;

        int minWidth = SkyblockEnhancementsConfig.rrvSearchBarWidth;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        // Center position
        if (Configs.CLIENT_SETTINGS.isCenterSearch()) {
            return Math.max(boxWidth, Math.min(minWidth, screenWidth - 20));
        }

        AbstractRrvOverlayAccessor overlay = (AbstractRrvOverlayAccessor) this;
        boolean wrapMode = Configs.CLIENT_SETTINGS.isItemWrapMode();

        int available = (wrapMode ? overlay.sbe$getWidth() : overlay.sbe$getEffectiveWidth()) - 4;
        int centerX = wrapMode
                ? overlay.sbe$getX() + overlay.sbe$getWidth() / 2
                : overlay.sbe$getEffectiveX() + overlay.sbe$getEffectiveWidth() / 2;

        int maxFromScreen = 2 * Math.min(centerX, screenWidth - centerX) - 4;
        int maxWidth = Math.min(minWidth, Math.min(available, Math.max(maxFromScreen, 0)));

        return Math.max(boxWidth, maxWidth);
    }

    @Inject(method = "createSearchbarElement", at = @At("RETURN"), remap = false)
    private void sbe$increaseMaxLength(CallbackInfo ci) {
        if (!RrvCompat.isActive()) return;
        if (!SkyblockEnhancementsConfig.wideRrvSearchBar) return;
        if (this.searchbar != null) {
            this.searchbar.setMaxLength(128);
        }
    }
}