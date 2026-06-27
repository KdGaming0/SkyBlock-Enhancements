/*
 * Block-strength data adapted from Revvilon/PingOffsetMiner (PomBlockData), CC0-1.0:
 * https://github.com/Revvilon/PingOffsetMiner
 * See THIRD_PARTY_LICENSES.md for the full attribution.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.data;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Maps the vanilla blocks Hypixel uses for SkyBlock mining nodes to the two
 * values the ping-offset feature needs: a {@code strength} (how long the block
 * takes to break) and a {@code breakingPower} (the tool power required to mine
 * it).
 */
public final class BlockStrengthRegistry {

    private BlockStrengthRegistry() {}

    /** Immutable mining data for one registered block. */
    public record Entry(double strength, int breakingPower) {}

    private static final Reference2ObjectOpenHashMap<Block, Entry> REGISTRY =
            new Reference2ObjectOpenHashMap<>(128);

    static {
        registerMithril();
        registerDwarvenMetal();
        registerOres();
        registerGemstones();
    }

    public static Optional<Entry> get(Block block) {
        return Optional.ofNullable(REGISTRY.get(block));
    }

    // ── Mithril ─────────────────────────────────────────────────────────────

    private static void registerMithril() {
        reg(Blocks.GRAY_WOOL,          500, 4);
        reg(Blocks.CYAN_TERRACOTTA,    500, 4);
        reg(Blocks.PRISMARINE,         800, 4);
        reg(Blocks.PRISMARINE_BRICKS,  800, 4);
        reg(Blocks.DARK_PRISMARINE,    800, 4);
        reg(Blocks.LIGHT_BLUE_WOOL,    1500, 4);
    }

    // ── Dwarven Metals ──────────────────────────────────────────────────────

    private static void registerDwarvenMetal() {
        reg(Blocks.POLISHED_DIORITE,     2000, 5);
        reg(Blocks.SMOOTH_RED_SANDSTONE, 5600, 9);
        reg(Blocks.TERRACOTTA,           5600, 9);
        reg(Blocks.BROWN_TERRACOTTA,     5600, 9);
        reg(Blocks.CLAY,                 5600, 9);
        reg(Blocks.COBBLESTONE,          5600, 9);
        reg(Blocks.PACKED_ICE,           6000, 9);
    }

    // ── Pure Ore Blocks ─────────────────────────────

    private static void registerOres() {
        reg(Blocks.COAL_BLOCK,     600, 5);
        reg(Blocks.IRON_BLOCK,     600, 5);
        reg(Blocks.GOLD_BLOCK,     600, 5);
        reg(Blocks.LAPIS_BLOCK,    600, 5);
        reg(Blocks.REDSTONE_BLOCK, 600, 5);
        reg(Blocks.EMERALD_BLOCK,  600, 5);
        reg(Blocks.DIAMOND_BLOCK,  600, 5);
        reg(Blocks.QUARTZ_BLOCK,   600, 5);
    }

    // ── Gemstones ──────────────────────

    private static void registerGemstones() {
        reg(Blocks.RED_STAINED_GLASS,            2300, 6);   // Ruby
        reg(Blocks.RED_STAINED_GLASS_PANE,       2300, 6);

        reg(Blocks.ORANGE_STAINED_GLASS,         3000, 7);   // Amber
        reg(Blocks.ORANGE_STAINED_GLASS_PANE,    3000, 7);
        reg(Blocks.LIME_STAINED_GLASS,           3000, 7);   // Jade
        reg(Blocks.LIME_STAINED_GLASS_PANE,      3000, 7);
        reg(Blocks.LIGHT_BLUE_STAINED_GLASS,     3000, 7);   // Sapphire
        reg(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, 3000, 7);
        reg(Blocks.PURPLE_STAINED_GLASS,         3000, 7);   // Amethyst
        reg(Blocks.PURPLE_STAINED_GLASS_PANE,    3000, 7);
        reg(Blocks.WHITE_STAINED_GLASS,          3000, 7);   // Opal
        reg(Blocks.WHITE_STAINED_GLASS_PANE,     3000, 7);

        reg(Blocks.YELLOW_STAINED_GLASS,         3800, 8);   // Topaz
        reg(Blocks.YELLOW_STAINED_GLASS_PANE,    3800, 8);

        reg(Blocks.MAGENTA_STAINED_GLASS,        4800, 9);   // Jasper
        reg(Blocks.MAGENTA_STAINED_GLASS_PANE,   4800, 9);
        reg(Blocks.BLACK_STAINED_GLASS,          5200, 9);   // Onyx
        reg(Blocks.BLACK_STAINED_GLASS_PANE,     5200, 9);
        reg(Blocks.BLUE_STAINED_GLASS,           5200, 9);   // Aquamarine
        reg(Blocks.BLUE_STAINED_GLASS_PANE,      5200, 9);
        reg(Blocks.BROWN_STAINED_GLASS,          5200, 9);   // Citrine
        reg(Blocks.BROWN_STAINED_GLASS_PANE,     5200, 9);
        reg(Blocks.GREEN_STAINED_GLASS,          5200, 9);   // Peridot
        reg(Blocks.GREEN_STAINED_GLASS_PANE,     5200, 9);
    }

    private static void reg(Block block, double strength, int breakingPower) {
        REGISTRY.put(block, new Entry(strength, breakingPower));
    }
}
