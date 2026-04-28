package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.internal.RrvViewTypeButtonReflection;
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

/**
 * Centers the recipe viewer vertically on screen instead of pinning it to the top.
 *
 * <p>RRV hardcodes {@code topPos = 32} in {@code checkGui()}. This mixin replaces
 * that constant with {@code (height - imageHeight) / 2}.
 *
 * <p>Button repositioning is delegated to {@link RrvViewTypeButtonReflection} so the
 * mixin stays focused on layout policy, not reflection mechanics.
 *
 * <p>TODO: Remove when updating to 26.1.
 */
@Mixin(RecipeViewScreen.class)
public abstract class RecipeViewCenterMixin extends AbstractContainerScreen<AbstractContainerMenu> {

    /** Tracks the last known topPos to avoid rebuilding buttons every frame. */
    @Unique
    private int sbe$lastTopPos = -1;

    /** Latches true after the first reflection failure so we log once and stop trying. */
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
            RrvViewTypeButtonReflection.rebuild(
                    (RecipeViewScreen) (Object) this,
                    (RecipeViewMenu) this.getMenu(),
                    this.width, this.topPos);
        } catch (RrvViewTypeButtonReflection.ReflectionFailure e) {
            SkyblockEnhancements.LOGGER.error(
                    "[SBE] Failed to reposition RRV buttons. This usually means RRV updated "
                            + "and changed internal names. Please report it on: "
                            + "https://fluxer.gg/3jJy9cp6");
            SkyblockEnhancements.LOGGER.error("[SBE] Error details: ", e);
            sbe$reflectionFailed = true;
        }
    }
}
