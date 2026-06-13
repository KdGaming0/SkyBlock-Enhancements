package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.access.LightTextureAccessor;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.IrisCompat;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public class LightTextureMixin implements LightTextureAccessor {

    @Inject(method = "render", at = @At("HEAD"))
    private void injectFullbrightIntensity(LightmapRenderState renderState, CallbackInfo ci) {
        boolean useShaderPath = SkyblockEnhancementsConfig.enableFullbright
                && !IrisCompat.isShadersActive()
                && !SkyblockEnhancementsConfig.fullbrightUseGamma;
        if (useShaderPath) {
            float intensity = (float) (SkyblockEnhancementsConfig.fullbrightStrength / 100.0);
            renderState.brightness = Math.max(renderState.brightness, intensity);
        }
    }

    @Override
    public void skyblockenhancements$markDirty() {
        // In 26.1 the lightmap render state is recalculated each frame; no explicit dirty flag exists.
    }
}
