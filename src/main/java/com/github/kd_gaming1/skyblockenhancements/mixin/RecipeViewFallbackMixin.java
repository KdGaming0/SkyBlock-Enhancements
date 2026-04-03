package com.github.kd_gaming1.skyblockenhancements.mixin;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When left-clicking an item in the recipe view (ActionType.RESULT) returns no recipes, retry with
 * ActionType.INPUT so that craft-reference items like NPC heads open their associated recipes.
 */
@Mixin(ItemViewOverlay.class)
public class RecipeViewFallbackMixin {

    @Inject(
            method = "openRecipeView(Lnet/minecraft/world/item/ItemStack;Lcc/cassian/rrv/api/ActionType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void sbe$fallbackResultToInput(ItemStack stack, ActionType openType, CallbackInfo ci) {
        if (openType != ActionType.RESULT) return;
        if (stack.isEmpty()) return;

        // Check if RESULT would find anything
        var resultRecipes = ClientRecipeCache.INSTANCE.getRecipesForCraftingOutput(stack);
        if (!resultRecipes.isEmpty()) return;

        // RESULT found nothing — check if INPUT has recipes (craft references like NPCs)
        var inputRecipes = ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
        if (inputRecipes.isEmpty()) return;

        // Cancel the original call and re-invoke with INPUT
        ci.cancel();
        ItemViewOverlay.INSTANCE.openRecipeView(stack, ActionType.INPUT);
    }
}