package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.access.LightTextureAccessor;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import eu.midnightdust.lib.config.EntryInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(EntryInfo.class)
public class EntryInfoMixin {

    @Final
    @Shadow
    public Field field;

    @Inject(method = "updateFieldValue", at = @At("TAIL"), remap = false)
    private void onUpdateFieldValue(CallbackInfo ci) {
        if (this.field == null) return;
        if (this.field.getDeclaringClass() != SkyblockEnhancementsConfig.class) return;
        String name = this.field.getName();
        if (!name.equals("fullbrightStrength") && !name.equals("enableFullbright")) return;
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        LightTexture lt = mc.gameRenderer.lightTexture();
        if (lt instanceof LightTextureAccessor accessor) {
            accessor.skyblockenhancements$markDirty();
        }
    }
}
