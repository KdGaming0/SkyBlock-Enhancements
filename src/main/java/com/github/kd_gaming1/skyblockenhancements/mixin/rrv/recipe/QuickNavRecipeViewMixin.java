package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.ScreenAccessor;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.internal.QuickNavListFinder;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Skyblocker's QuickNav buttons on RRV's {@link RecipeViewScreen}.
 *
 * <p>Skyblocker adds QuickNav widgets via its own mixin on {@link AbstractContainerScreen#init}.
 * We run after it (priority 1100) and:
 * <ol>
 *   <li>Remove any QuickNav widgets from the screen's standard child list.</li>
 *   <li>Null out Skyblocker's internal {@code List<QuickNavButton>} so nothing re-renders.</li>
 * </ol>
 * Both operations are guarded by {@link RrvCompat#isRrvPresent()} and a {@code RecipeViewScreen}
 * instanceof check so other screens are untouched.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1100)
public class QuickNavRecipeViewMixin {

    @Inject(method = "init()V", at = @At("TAIL"))
    private void sbe$removeQuickNavFromRecipeView(CallbackInfo ci) {
        if (!RrvCompat.isRrvPresent()) return;
        if (!((Object) this instanceof RecipeViewScreen screen)) return;

        removeQuickNavWidgets(screen);
        clearSkyblockerQuickNavList();
    }

    @Unique
    private static void removeQuickNavWidgets(RecipeViewScreen screen) {
        ScreenAccessor accessor = (ScreenAccessor) screen;
        List.copyOf(screen.children()).stream()
                .filter(w -> w.getClass().getName().startsWith(QuickNavListFinder.QUICK_NAV_PACKAGE))
                .forEach(accessor::sbe$removeWidget);
    }

    @Unique
    private void clearSkyblockerQuickNavList() {
        try {
            QuickNavListFinder.clearQuickNavList(this);
        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.warn("Failed to clear Skyblocker's QuickNav rendering list.", e);
        }
    }
}