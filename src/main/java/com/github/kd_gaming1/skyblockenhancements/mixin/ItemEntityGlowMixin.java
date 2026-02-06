package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.feature.glow.ItemGlowManager;
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

        if (ItemGlowManager.shouldForceGlow(self)) {
            cir.setReturnValue(true);
        }
    }
}