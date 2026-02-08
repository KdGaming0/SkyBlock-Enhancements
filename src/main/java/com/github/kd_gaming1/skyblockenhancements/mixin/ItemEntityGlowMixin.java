package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.glow.ItemGlowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ItemEntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void skyblockenhancements$forceItemGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        if (!(self instanceof ItemEntity)) return;

        if (!SkyblockEnhancementsConfig.enableItemGlowOutline) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }


        if (ItemGlowManager.shouldForceGlow(self)) {
            if (!SkyblockEnhancementsConfig.showThoughWalls) {
                if (Minecraft.getInstance().player != null
                        && !Minecraft.getInstance().player.hasLineOfSight(self)) {
                    cir.setReturnValue(false);
                    cir.cancel();
                    return;
                }
            }
            cir.setReturnValue(true);
            cir.cancel();
        } else {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}