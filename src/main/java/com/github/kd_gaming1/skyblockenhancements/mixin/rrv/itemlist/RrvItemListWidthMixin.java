package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.itemlist;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
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

@Mixin(value = ItemViewOverlay.class, remap = false)
public class RrvItemListWidthMixin {

    @Unique
    private static final int ITEM_ENTRY_SIZE = 18;

    @Inject(
            method = "initForScreen",
            at = @At("TAIL"),
            remap = false)
    private void sbe$applyWidthPercent(
            AbstractContainerScreen<? extends AbstractContainerMenu> screen,
            cc.cassian.rrv.common.overlay.AbstractRrvOverlay.InventoryPositionInfo invInfo,
            CallbackInfo ci) {

        int percent = SkyblockEnhancementsConfig.rrvItemListWidthPercent;
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
            overlay.sbe$setX(invInfo.screenWidth() - newWidth);
        }

        itemList.sbe$setItemStartX(overlay.sbe$getX() + 2);
        itemList.sbe$setItemEndX(overlay.sbe$getX() + newWidth - 2);
    }
}