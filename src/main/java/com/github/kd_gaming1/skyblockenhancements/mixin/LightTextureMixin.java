package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @ModifyArg(
            method = "updateLightTexture",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 6),
            index = 0)
    private float overrideBrightnessFactor(float original) {
        if (!SkyblockEnhancementsConfig.enableFullbright) return original;
        return Math.max(original, (float) SkyblockEnhancementsConfig.fullbrightStrength);
    }
}