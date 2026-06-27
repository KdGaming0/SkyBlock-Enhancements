/*
 * Part of the ping-offset mining feature adapted from Revvilon/PingOffsetMiner,
 * CC0-1.0: https://github.com/Revvilon/PingOffsetMiner
 * See THIRD_PARTY_LICENSES.md for the full attribution.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.detect;

import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthRegistry;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthRegistry.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Resolves the block the player's crosshair is on, using Minecraft's built-in
 * pick raycast (the same one that drives the crosshair highlight).
 */
public final class TargetBlockDetector {

    private TargetBlockDetector() {}

    private static final double RAYCAST_DISTANCE = 5.0;

    /** A registered target block paired with its world position. */
    public record Result(Entry entry, BlockPos pos) {}

    /**
     * Returns the registered mining data for the targeted block, if the
     * crosshair is on a block that {@link BlockStrengthRegistry} knows.
     */
    public static Optional<Result> getTargetBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();

        Optional<BlockPos> pos = raycastBlockPos(mc);
        if (pos.isEmpty()) return Optional.empty();

        BlockState state = mc.level.getBlockState(pos.get());
        if (state.isAir()) return Optional.empty();

        return BlockStrengthRegistry.get(state.getBlock())
                .map(entry -> new Result(entry, pos.get()));
    }

    /**
     * Returns the targeted block position without a registry lookup — used to
     * check whether the player is still aiming at the same block.
     */
    public static Optional<BlockPos> getTargetPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        return raycastBlockPos(mc);
    }

    private static Optional<BlockPos> raycastBlockPos(Minecraft mc) {
        HitResult hit = mc.player.pick(RAYCAST_DISTANCE, 0f, false);
        if (!(hit instanceof BlockHitResult blockHit)) return Optional.empty();
        if (blockHit.getType() == HitResult.Type.MISS) return Optional.empty();
        return Optional.of(blockHit.getBlockPos());
    }
}
