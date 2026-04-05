package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.ScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Prevents Skyblocker's QuickNav buttons from appearing on RRV's
 * {@link RecipeViewScreen}.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1100)
public class QuickNavRecipeViewMixin {

    @Unique
    private static final String QUICK_NAV_PACKAGE =
            "de.hysky.skyblocker.skyblock.quicknav.QuickNav";

    @Inject(method = "init()V", at = @At("TAIL"))
    private void sbe$removeQuickNavFromRecipeView(CallbackInfo ci) {
        if (!RrvCompat.isRrvPresent()) return;
        if (!((Object) this instanceof RecipeViewScreen screen)) return;

        ScreenAccessor accessor = (ScreenAccessor) screen;

        // 1. Remove widgets from the standard screen
        List.copyOf(screen.children())
                .stream()
                .filter(w -> w.getClass().getName().startsWith(QUICK_NAV_PACKAGE))
                .forEach(accessor::sbe$removeWidget);

        // 2. Clear Skyblocker's internal list.
        try {
            for (Field field : AbstractContainerScreen.class.getDeclaredFields()) {
                if (field.getType() == List.class) {
                    field.setAccessible(true);
                    Object val = field.get(this);

                    if (val instanceof List<?> list && !list.isEmpty()) {
                        Object firstItem = list.getFirst();
                        if (firstItem != null && firstItem.getClass().getName().startsWith(QUICK_NAV_PACKAGE)) {
                            field.set(this, null);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.warn("Failed to clear Skyblocker's QuickNav rendering list.", e);
        }
    }
}