package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.access.LightTextureAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightTextureMixin implements LightTextureAccessor {
    @Shadow
    private boolean updateLightTexture;

    @WrapOperation(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    // Target the 7th putFloat — BrightnessFactor — which is the last float before vec3s
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 6))
    private Std140Builder wrapBrightnessAndInjectFullbright(
            Std140Builder builder,
            float vanillaBrightness,
            Operation<Std140Builder> original) {

        Std140Builder result = original.call(builder, vanillaBrightness);

        // Append FullbrightIntensity as the new 8th float, before the vec3s
        float intensity = SkyblockEnhancementsConfig.enableFullbright
                ? (float) SkyblockEnhancementsConfig.fullbrightStrength / 100.0F
                : 0.0F;

        return result.putFloat(intensity);
    }

    @Override
    public void skyblockenhancements$markDirty() {
        this.updateLightTexture = true;
    }
}
