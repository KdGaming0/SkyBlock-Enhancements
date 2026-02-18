package com.github.kd_gaming1.skyblockenhancements.mixin.itemglow;

import com.github.kd_gaming1.skyblockenhancements.util.accessor.EntityRenderStateExtension;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Copies the entity UUID onto its render state so downstream mixins can identify it. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("TAIL")
    )
    private void captureUuid(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        ((EntityRenderStateExtension) cir.getReturnValue()).sbe_setUuid(entity.getUUID());
    }
}