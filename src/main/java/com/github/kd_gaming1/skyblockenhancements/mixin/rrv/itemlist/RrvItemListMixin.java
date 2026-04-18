package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.itemlist;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces RRV's item overlay list on Hypixel to show only SkyBlock items.
 */
@Mixin(ItemFilters.class)
public class RrvItemListMixin {

    @Inject(method = "fullStackList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sbe$skyblockOnlyItemList(CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!RrvCompat.isActive()) return;

        cir.setReturnValue(FullStackListCache.getOrBuild());
    }
}