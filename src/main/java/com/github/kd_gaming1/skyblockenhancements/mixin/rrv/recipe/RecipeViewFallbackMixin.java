package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.recipe;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.service.RecipeFallbackResolver;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts RRV recipe-view lookups to run {@link RecipeFallbackResolver} before
 * the default RRV logic. If the resolver finds a match (NPC tab, RESULT fallback,
 * or family page-seek) the original call is cancelled.
 *
 * <p>Also sorts recipes in the {@code openRecipeView(ReliableClientRecipeType)} overload
 * so that the Reforges tab (and all other tabs) display recipes in deterministic order.
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

    /**
     * Sorts the recipe list before it is passed to {@code RecipeViewMenu} in the
     * {@code openRecipeView(ReliableClientRecipeType)} overload. RRV's native
     * {@code ClientRecipeCache.getRecipes()} returns recipes in HashMap iteration order,
     * which is random. This ensures reforge recipes are always grouped by name and ordered
     * by rarity regardless of how the view was opened.
     */
    @ModifyVariable(
            method = "openRecipeView(Lcc/cassian/rrv/api/recipe/ReliableClientRecipeType;)V",
            at = @At("STORE"),
            ordinal = 0,
            remap = false)
    @SuppressWarnings("UnstableApiUsage")
    private List<ReliableClientRecipe> sbe$sortRecipesForTypeView(List<ReliableClientRecipe> foundRecipes) {
        List<ReliableClientRecipe> mutable = new ArrayList<>(foundRecipes);
        mutable.sort(RecipeFallbackResolver.RECIPE_COMPARATOR);
        return mutable;
    }
}
