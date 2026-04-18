package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Centers the recipe viewer vertically on screen instead of pinning it to the top.
 *
 * <p>RRV hardcodes {@code topPos = 32} in {@code checkGui()}. This mixin replaces
 * that constant with {@code (height - imageHeight) / 2}.
 *
 * <p>TODO: Remove when updating to 26.1.
 */
@Mixin(RecipeViewScreen.class)
public abstract class RecipeViewCenterMixin extends AbstractContainerScreen<AbstractContainerMenu> {

    // Cache reflection
    @Unique
    private static java.lang.reflect.Field sbe$buttonsField = null;
    @Unique
    private static java.lang.reflect.Constructor<?> sbe$buttonConstructor = null;
    @Unique
    private static java.lang.reflect.Method sbe$orderMethod = null;

    // Track state to avoid rebuilding every frame
    @Unique
    private int sbe$lastTopPos = -1;
    @Unique
    private static boolean sbe$reflectionFailed = false;

    private RecipeViewCenterMixin() {
        super(null, null, Component.empty());
    }

    @ModifyConstant(method = "checkGui", constant = @Constant(intValue = 32), remap = false)
    private int sbe$centerTopPos(int original) {
        int centered = (this.height - this.imageHeight) / 2;
        return Math.max(centered, 32);
    }

    @Inject(method = "checkGui", at = @At("TAIL"), remap = false)
    private void sbe$fixViewTypeButtonPositions(CallbackInfo ci) {
        if (sbe$reflectionFailed || this.topPos == sbe$lastTopPos) return;
        sbe$lastTopPos = this.topPos;

        try {
            // 1. Initialize reflection
            if (sbe$buttonConstructor == null) {
                sbe$buttonsField = RecipeViewScreen.class.getDeclaredField("viewTypeButtons");
                sbe$buttonsField.setAccessible(true);

                Class<?> recordClass = Class.forName("cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen$ViewTypeButton");
                sbe$buttonConstructor = recordClass.getDeclaredConstructors()[0];
                sbe$buttonConstructor.setAccessible(true);

                sbe$orderMethod = this.getMenu().getClass().getMethod("getViewTypeOrder");
            }

            // 2. Rebuild the button list
            List<?> order = (List<?>) sbe$orderMethod.invoke(this.getMenu());
            int w = 24, h = 24;
            List<Object> rebuilt = new java.util.ArrayList<>();

            for (int i = 0; i < order.size(); i++) {
                int tempId = i % 5;
                int xPos = this.width / 2 - (5 * w / 2 + 4) + tempId * w + tempId * 2;
                int yPos = this.topPos - h - 1;
                rebuilt.add(sbe$buttonConstructor.newInstance(this, xPos, yPos, w, h, order.get(i), i));
            }

            sbe$buttonsField.set(this, rebuilt);
        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.error("[SBE] Failed to reposition RRV buttons. This usually means RRV updated and changed internal names. Please report it on: https://fluxer.gg/3jJy9cp6");
            SkyblockEnhancements.LOGGER.error("[SBE] Error details: ", e);
            sbe$reflectionFailed = true;
        }
    }
}