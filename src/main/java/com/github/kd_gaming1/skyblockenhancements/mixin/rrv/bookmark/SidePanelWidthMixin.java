package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.bookmark;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor.AbstractRrvOverlayAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SidePanelOverlay.class)
public class SidePanelWidthMixin {

    @Unique
    private static final int ITEM_ENTRY_SIZE = 20;

    @Inject(
            method = "initForScreen",
            at = @At("TAIL"),
            remap = false)
    private void sbe$applySidePanelWidth(
            AbstractContainerScreen<? extends AbstractContainerMenu> screen,
            AbstractRrvOverlay.InventoryPositionInfo invInfo,
            CallbackInfo ci) {

        int percent = SkyblockEnhancementsConfig.rrvSidePanelWidthPercent;
        if (percent >= 100) return;

        AbstractRrvOverlayAccessor overlay = (AbstractRrvOverlayAccessor) (Object) this;
        AbstractRrvItemListOverlayAccessor itemList = (AbstractRrvItemListOverlayAccessor) (Object) this;

        int newWidth = (overlay.sbe$getWidth() * percent) / 100;
        newWidth -= (newWidth - 4) % ITEM_ENTRY_SIZE;

        if (newWidth < ITEM_ENTRY_SIZE + 4) {
            newWidth = ITEM_ENTRY_SIZE + 4;
        }

        overlay.sbe$setWidth(newWidth);

        if (Configs.CLIENT_SETTINGS.isRightIndex()) {
            overlay.sbe$setX(0);
        } else {
            overlay.sbe$setX(invInfo.screenWidth() - newWidth);
        }

        itemList.sbe$setItemStartX(overlay.sbe$getX() + 2);
        itemList.sbe$setItemEndX(overlay.sbe$getX() + newWidth - 2);
    }
}
