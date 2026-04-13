package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ItemFrameRenderer.class)
public class HideItemFrameMixin {

    // Cache: block-position key → should hide, valid for one tick
    @Unique
    private static final Map<Long, Boolean> hideCache = new HashMap<>();
    @Unique
    private static long lastGameTime = -1;

    @Unique
    private static long blockKey(ItemFrame entity) {
        BlockPos pos = entity.blockPosition();
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }

    @Inject(method = "extractRenderState*", at = @At("RETURN"))
    private void onExtractRenderState(
            ItemFrame entity,
            ItemFrameRenderState state,
            float partialTick,
            CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.hideItemFrames) return;

        // Invalidate cache each game tick
        long gameTime = entity.level().getGameTime();
        if (gameTime != lastGameTime) {
            hideCache.clear();
            lastGameTime = gameTime;
        }

        long key = blockKey(entity);
        Boolean cached = hideCache.get(key);
        if (cached == null) {
            AABB box = entity.getBoundingBox().inflate(0.1);
            List<ItemFrame> framesAtPos = entity.level().getEntitiesOfClass(ItemFrame.class, box);
            cached = framesAtPos.stream().anyMatch(f -> !f.getItem().isEmpty());
            hideCache.put(key, cached);
        }

        if (cached) {
            state.isInvisible = true;
        }
    }
}