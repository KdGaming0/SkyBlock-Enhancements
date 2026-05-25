package com.github.kd_gaming1.skyblockenhancements.feature.mining.data;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Static compile-time registry mapping vanilla Block instances to their
 * SkyBlock-specific mining properties (strength, breaking power, category).
 * Data sourced from the Hypixel SkyBlock wiki.
 */
public final class BlockStrengthRegistry {

    private BlockStrengthRegistry() {}

    private static final Reference2ObjectOpenHashMap<Block, BlockStrengthEntry> REGISTRY =
            new Reference2ObjectOpenHashMap<>(128);

    static {
        registerMithril();
        registerDwarvenMetal();
        registerGlacite();
        registerOres();
        registerGemstones();
    }

    public static Optional<BlockStrengthEntry> get(Block block) {
        return Optional.ofNullable(REGISTRY.get(block));
    }

    public static boolean isRegistered(Block block) {
        return REGISTRY.containsKey(block);
    }

    // ── Mithril ─────────────────────────────────────────────────────────────

    private static void registerMithril() {
        reg(Blocks.PRISMARINE,           500,   4, BlockCategory.MITHRIL);
        reg(Blocks.PRISMARINE_BRICKS,    800,   4, BlockCategory.MITHRIL);
        reg(Blocks.DARK_PRISMARINE,     1500,   4, BlockCategory.MITHRIL);
        reg(Blocks.GRAY_WOOL,            500,   4, BlockCategory.MITHRIL);
        reg(Blocks.CYAN_TERRACOTTA,      500,   4, BlockCategory.MITHRIL);
        reg(Blocks.LIGHT_BLUE_WOOL,     1500,   4, BlockCategory.MITHRIL);
    }

    // ── Dwarven Metals ──────────────────────────────────────────────────────

    private static void registerDwarvenMetal() {
        reg(Blocks.POLISHED_DIORITE,           2000,   5, BlockCategory.ORE);
        reg(Blocks.CLAY,                       5600,   9, BlockCategory.ORE);
        reg(Blocks.INFESTED_COBBLESTONE,       5600,   9, BlockCategory.ORE);
        reg(Blocks.BROWN_TERRACOTTA,           5600,   9, BlockCategory.ORE);
        reg(Blocks.TERRACOTTA,                 5600,   9, BlockCategory.ORE);
        reg(Blocks.SMOOTH_RED_SANDSTONE,       5600,   9, BlockCategory.ORE);
    }

    // ── Glacite ─────────────────────────────────────────────────────────────

    private static void registerGlacite() {
        reg(Blocks.PACKED_ICE,             6000,   9, BlockCategory.ORE);
    }

    // ── Vanilla ore blocks (SkyBlock stand-ins) ─────────────────────────────

    private static void registerOres() {
        reg(Blocks.COAL_BLOCK,      600, 5, BlockCategory.ORE);
        reg(Blocks.IRON_BLOCK,      600, 5, BlockCategory.ORE);
        reg(Blocks.GOLD_BLOCK,      600, 5, BlockCategory.ORE);
        reg(Blocks.LAPIS_BLOCK,     600, 5, BlockCategory.ORE);
        reg(Blocks.REDSTONE_BLOCK,  600, 5, BlockCategory.ORE);
        reg(Blocks.EMERALD_BLOCK,   600, 5, BlockCategory.ORE);
        reg(Blocks.DIAMOND_BLOCK,   600, 5, BlockCategory.ORE);
        reg(Blocks.QUARTZ_BLOCK,    600, 5, BlockCategory.ORE);
    }

    // ── Gemstones (breaking power varies per gem type) ──────────────────────

    private static void registerGemstones() {
        regGem(Blocks.RED_STAINED_GLASS,          2300, 6);   // Ruby
        regGem(Blocks.RED_STAINED_GLASS_PANE,     2300, 6);

        regGem(Blocks.ORANGE_STAINED_GLASS,       3000, 7);   // Amber
        regGem(Blocks.ORANGE_STAINED_GLASS_PANE,  3000, 7);
        regGem(Blocks.LIME_STAINED_GLASS,         3000, 7);   // Jade
        regGem(Blocks.LIME_STAINED_GLASS_PANE,    3000, 7);
        regGem(Blocks.LIGHT_BLUE_STAINED_GLASS,   3000, 7);   // Sapphire
        regGem(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, 3000, 7);
        regGem(Blocks.PURPLE_STAINED_GLASS,       3000, 7);   // Amethyst
        regGem(Blocks.PURPLE_STAINED_GLASS_PANE,  3000, 7);
        regGem(Blocks.WHITE_STAINED_GLASS,        3000, 7);   // Opal
        regGem(Blocks.WHITE_STAINED_GLASS_PANE,   3000, 7);

        regGem(Blocks.YELLOW_STAINED_GLASS,       3800, 8);   // Topaz
        regGem(Blocks.YELLOW_STAINED_GLASS_PANE,  3800, 8);

        regGem(Blocks.PINK_STAINED_GLASS,         4800, 9);   // Jasper
        regGem(Blocks.PINK_STAINED_GLASS_PANE,    4800, 9);
        regGem(Blocks.GRAY_STAINED_GLASS,         5200, 9);   // Onyx
        regGem(Blocks.GRAY_STAINED_GLASS_PANE,    5200, 9);
        regGem(Blocks.BLUE_STAINED_GLASS,         5200, 9);   // Aquamarine
        regGem(Blocks.BLUE_STAINED_GLASS_PANE,    5200, 9);
        regGem(Blocks.BROWN_STAINED_GLASS,        5200, 9);   // Citrine
        regGem(Blocks.BROWN_STAINED_GLASS_PANE,   5200, 9);
        regGem(Blocks.GREEN_STAINED_GLASS,        5200, 9);   // Peridot
        regGem(Blocks.GREEN_STAINED_GLASS_PANE,   5200, 9);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void reg(Block block, double strength, int breakingPower, BlockCategory category) {
        boolean isOre = category == BlockCategory.ORE
                || category == BlockCategory.PURE_ORE
                || category == BlockCategory.MITHRIL
                || category == BlockCategory.GEMSTONE;
        double softcapMs = (20.0 / 3.0) * strength;
        double instantMs = isOre ? strength * 60.0 : strength * 30.0;

        REGISTRY.put(block, new BlockStrengthEntry(
                block, strength, breakingPower, category, isOre, softcapMs, instantMs));
    }

    private static void regGem(Block block, double strength, int breakingPower) {
        double softcapMs = (20.0 / 3.0) * strength;
        double instantMs = strength * 60.0;

        REGISTRY.put(block, new BlockStrengthEntry(
                block, strength, breakingPower, BlockCategory.GEMSTONE, true, softcapMs, instantMs));
    }
}
