/*
 * Part of the ping-offset mining feature inspired by PingOffsetMiner:
 * https://github.com/Revvilon/PingOffsetMiner
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.detect;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthEntry;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Detects which block the player is currently looking at and whether it has
 * SkyBlock-specific mining data.
 *
 * <p>Uses Minecraft's built-in pick raycast (same as the crosshair). Only
 * resolves the target when the attack key is held — lazy evaluation for
 * performance.
 */
public final class TargetBlockDetector {

    private TargetBlockDetector() {}

    private static final double RAYCAST_DISTANCE = 5.0;

    /**
     * Raycasts from the player's eyes and returns the BlockStrengthEntry
     * for the targeted block, if any.
     *
     * @return Optional containing the entry and position if the targeted block
     *         is registered in BlockStrengthRegistry
     */
    public static Optional<Result> getTargetBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();

        HitResult hit = mc.player.pick(RAYCAST_DISTANCE, 0f, false);
        if (!(hit instanceof BlockHitResult blockHit)) return Optional.empty();
        if (blockHit.getType() == HitResult.Type.MISS) return Optional.empty();

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return Optional.empty();

        return BlockStrengthRegistry.get(state.getBlock())
                .map(entry -> new Result(entry, pos, blockHit));
    }

    /**
     * Returns the targeted block position without doing a registry lookup.
     * Useful for checking if the player is still looking at the same block.
     */
    public static Optional<BlockPos> getTargetPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();

        HitResult hit = mc.player.pick(RAYCAST_DISTANCE, 0f, false);
        if (!(hit instanceof BlockHitResult blockHit)) return Optional.empty();
        if (blockHit.getType() == HitResult.Type.MISS) return Optional.empty();

        return Optional.of(blockHit.getBlockPos());
    }

    /**
     * Result tuple: block data + position + hit result.
     */
    public record Result(BlockStrengthEntry entry, BlockPos pos, BlockHitResult hitResult) {
        public long packedPos() {
            return pos.asLong();
        }
    }
}
