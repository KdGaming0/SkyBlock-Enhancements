package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Skyblocker's VisitorHelper from crashing the game when RRV's RecipeViewScreen is open.
 */
@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.garden.visitor.VisitorHelper", remap = false)
public class VisitorHelperRecipeViewMixin {

    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "updateVisitors", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sbe$preventCrashOnRecipeView(CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RecipeViewScreen) {
            ci.cancel();
        }
    }
}