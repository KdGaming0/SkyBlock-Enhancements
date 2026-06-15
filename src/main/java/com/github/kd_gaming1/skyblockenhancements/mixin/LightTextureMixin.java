package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.IrisCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Feeds the {@code FullbrightIntensity} uniform consumed by the shipped {@code lightmap.fsh}.
 *
 * <p>In 26.1 the lightmap is uploaded as a std140 UBO during {@link Lightmap#render}. The vanilla
 * block is six floats followed by four vec3s; std140 pads the float region up to a 16-byte
 * boundary before the first vec3, so a seventh float fits in that existing padding and the UBO
 * size is unchanged. We wrap the final float write ({@code renderState.brightness}) and append the
 * fullbright intensity immediately after it — matching the {@code FullbrightIntensity} slot the
 * shader declares right after {@code BrightnessFactor}.</p>
 */
@Mixin(Lightmap.class)
public class LightTextureMixin {

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 5))
    private Std140Builder skyblockenhancements$appendFullbrightIntensity(
            Std140Builder builder, float vanillaBrightness, Operation<Std140Builder> original) {
        Std140Builder result = original.call(builder, vanillaBrightness);
        boolean useShaderPath = SkyblockEnhancementsConfig.enableFullbright
                && !IrisCompat.isShadersActive()
                && !SkyblockEnhancementsConfig.fullbrightUseGamma;
        float intensity = useShaderPath
                ? (float) (SkyblockEnhancementsConfig.fullbrightStrength / 100.0)
                : 0.0F;
        return result.putFloat(intensity);
    }
}
