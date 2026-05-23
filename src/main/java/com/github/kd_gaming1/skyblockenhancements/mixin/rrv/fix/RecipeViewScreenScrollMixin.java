package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden.SkyblockGardenMutationClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents RRV from navigating to the next/previous recipe when a garden
 * mutation tooltip is being displayed.
 *
 * <p>When the user hovers over the info text area and a tooltip appears,
 * scrolling the mouse wheel should not change recipes. This Mixin cancels
 * {@link RecipeViewScreen#mouseScrolled} entirely while our tooltip is
 * active, consuming the scroll event harmlessly.
 */
@Mixin(value = RecipeViewScreen.class, remap = false)
public abstract class RecipeViewScreenScrollMixin {

    /**
     * Cancels the scroll event when a garden mutation tooltip is visible.
     * The field is set during {@code renderRecipe()} and reset at the start
     * of each frame, so it accurately reflects whether a tooltip was shown
     * on the most recently rendered frame.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
            double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SkyblockEnhancementsConfig.enableTooltipScroll
                && SkyblockGardenMutationClientRecipe.tooltipActive) {
            cir.setReturnValue(true); // consume the event and let tooltip scroll work instead of changing page
        }
    }
}
