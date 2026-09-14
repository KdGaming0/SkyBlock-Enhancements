package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sprint-input hook adapted from Odin by odtheking (BSD-3-Clause).
 * Source: https://github.com/odtheking/Odin/blob/331628623c59f7a7274e3985ae16f5411ae21123/src/main/java/com/odtheking/mixin/mixins/LocalPlayerMixin.java
 * Copyright (c) 2025, odtheking. See licenses/Odin-BSD-3-Clause.txt in resources.
 * Adds SkyBlock, menu, and optional water checks specific to SkyBlock Enhancements.
 */
@Mixin(LocalPlayer.class)
public abstract class AutoSprintMixin {

    @ModifyExpressionValue(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
    private boolean sbe$autoSprint(boolean original) {
        // Supply sprint input; vanilla still decides whether the player can sprint.
        return original || (SkyblockEnhancementsConfig.autoSprint
                && HypixelLocationState.isOnSkyblock()
                && (SkyblockEnhancementsConfig.autoSprintInWater
                    || !((LocalPlayer) (Object) this).isInWater())
                && Minecraft.getInstance().gui.screen() == null);
    }
}
