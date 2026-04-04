package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces RRV's item overlay list on Hypixel to show only SkyBlock items.
 *
 * <p>The original {@code fullStackList()} adds every vanilla item from {@code BuiltInRegistries},
 * then runs {@code ResourceRecipeManager.replaceIndex()} which crashes on Hypixel because the
 * server's stripped registry is missing items that {@code vanilla.json} references. Even without
 * the crash, 1000+ vanilla items clutter the sidebar when all you care about are the 8000+ SkyBlock
 * items.
 *
 * <p>This mixin short-circuits the method when our integration is active: it collects only the
 * stack-sensitives (which contain all our SkyBlock items) and skips both the vanilla item loop
 * and the problematic {@code replaceIndex()} call.
 */
@Mixin(ItemFilters.class)
public class RrvItemListMixin {

    @Inject(method = "fullStackList", at = @At("HEAD"), cancellable = true, remap = false)
    @SuppressWarnings("UnstableApiUsage")
    private static void sbe$skyblockOnlyItemList(CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!RrvCompat.isActive()) return;

        List<ItemStack> results = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item ->
                results.addAll(
                        ClientRecipeCache.INSTANCE.getStackSensitives(item).stream()
                                .map(ItemView.StackSensitive::stack)
                                .toList()));
        cir.setReturnValue(results);
    }
}