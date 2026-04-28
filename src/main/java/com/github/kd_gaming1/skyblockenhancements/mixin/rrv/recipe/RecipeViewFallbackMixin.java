package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.recipe;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.service.RecipeFallbackResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts RRV recipe-view lookups to run {@link RecipeFallbackResolver} before
 * the default RRV logic. If the resolver finds a match (NPC tab, RESULT fallback,
 * or family page-seek) the original call is cancelled.
 */
@Mixin(ItemViewOverlay.class)
public class RecipeViewFallbackMixin {

    @Inject(
            method = "openRecipeView(Lnet/minecraft/world/item/ItemStack;Lcc/cassian/rrv/api/ActionType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    @SuppressWarnings("UnstableApiUsage")
    private void sbe$interceptForFallback(ItemStack stack, ActionType openType, CallbackInfo ci) {
        if (RecipeFallbackResolver.tryOpen(stack, openType)) {
            ci.cancel();
        }
    }
}
