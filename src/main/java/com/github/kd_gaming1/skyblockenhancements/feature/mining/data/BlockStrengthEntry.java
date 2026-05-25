package com.github.kd_gaming1.skyblockenhancements.feature.mining.data;

import net.minecraft.world.level.block.Block;

/**
 * Immutable record of SkyBlock-specific mining data for a single vanilla block.
 * All fields are primitives — zero-allocation after construction.
 */
public final class BlockStrengthEntry {

    private final Block block;
    private final double strength;
    private final int breakingPower;
    private final BlockCategory category;
    private final boolean isOre;
    private final double softcapMiningSpeed;
    private final double instantMiningSpeed;

    BlockStrengthEntry(Block block, double strength, int breakingPower,
                       BlockCategory category, boolean isOre,
                       double softcapMiningSpeed, double instantMiningSpeed) {
        this.block = block;
        this.strength = strength;
        this.breakingPower = breakingPower;
        this.category = category;
        this.isOre = isOre;
        this.softcapMiningSpeed = softcapMiningSpeed;
        this.instantMiningSpeed = instantMiningSpeed;
    }

    public Block block() {
        return block;
    }

    public double strength() {
        return strength;
    }

    public int breakingPower() {
        return breakingPower;
    }

    public BlockCategory category() {
        return category;
    }

    public boolean isOre() {
        return isOre;
    }

    public double softcapMiningSpeed() {
        return softcapMiningSpeed;
    }

    public double instantMiningSpeed() {
        return instantMiningSpeed;
    }
}
