package com.github.kd_gaming1.skyblockenhancements.mixin.itemglow;

import com.github.kd_gaming1.skyblockenhancements.feature.ItemGlowManager;
import com.github.kd_gaming1.skyblockenhancements.util.accessor.EntityRenderStateExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Injects after visible entities are collected to apply rarity-based glow outlines on items. */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void applyItemGlow(
            Camera camera, Frustum frustum,
            DeltaTracker deltaTracker, LevelRenderState renderState,
            CallbackInfo ci
    ) {
        boolean hasGlowing = false;

        for (EntityRenderState state : renderState.entityRenderStates) {
            if (state.entityType != EntityType.ITEM) continue;

            UUID uuid = ((EntityRenderStateExtension) state).sbe_getUuid();
            int color = ItemGlowManager.getGlowColorIfActive(uuid);
            if (color == -1) continue;

            state.outlineColor = color;
            hasGlowing = true;
        }

        if (hasGlowing) {
            renderState.haveGlowingEntities = true;
        }
    }
}