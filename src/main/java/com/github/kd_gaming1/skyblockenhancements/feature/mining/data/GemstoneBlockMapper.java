package com.github.kd_gaming1.skyblockenhancements.feature.mining.data;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Maps stained glass and stained glass pane blocks to their corresponding
 * SkyBlock {@link GemstoneType}.
 *
 * <p>In 1.21+ each color is a separate block instance, so mapping is done
 * by direct block reference rather than block-state property extraction.
 * All mappings are hardcoded at compile time based on the Gemstone wiki.
 * No runtime wiki scraping.
 */
public final class GemstoneBlockMapper {

    private GemstoneBlockMapper() {}

    private static final Reference2ObjectOpenHashMap<Block, GemstoneType> BLOCK_TO_GEM =
            new Reference2ObjectOpenHashMap<>(32);

    static {
        put(Blocks.RED_STAINED_GLASS, GemstoneType.RUBY);
        put(Blocks.RED_STAINED_GLASS_PANE, GemstoneType.RUBY);
        put(Blocks.ORANGE_STAINED_GLASS, GemstoneType.AMBER);
        put(Blocks.ORANGE_STAINED_GLASS_PANE, GemstoneType.AMBER);
        put(Blocks.BLUE_STAINED_GLASS, GemstoneType.SAPPHIRE);
        put(Blocks.BLUE_STAINED_GLASS_PANE, GemstoneType.SAPPHIRE);
        put(Blocks.LIME_STAINED_GLASS, GemstoneType.JADE);
        put(Blocks.LIME_STAINED_GLASS_PANE, GemstoneType.JADE);
        put(Blocks.PURPLE_STAINED_GLASS, GemstoneType.AMETHYST);
        put(Blocks.PURPLE_STAINED_GLASS_PANE, GemstoneType.AMETHYST);
        put(Blocks.YELLOW_STAINED_GLASS, GemstoneType.TOPAZ);
        put(Blocks.YELLOW_STAINED_GLASS_PANE, GemstoneType.TOPAZ);
        put(Blocks.PINK_STAINED_GLASS, GemstoneType.JASPER);
        put(Blocks.PINK_STAINED_GLASS_PANE, GemstoneType.JASPER);
        put(Blocks.WHITE_STAINED_GLASS, GemstoneType.OPAL);
        put(Blocks.WHITE_STAINED_GLASS_PANE, GemstoneType.OPAL);
        put(Blocks.LIGHT_BLUE_STAINED_GLASS, GemstoneType.AQUAMARINE);
        put(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, GemstoneType.AQUAMARINE);
        put(Blocks.GRAY_STAINED_GLASS, GemstoneType.ONYX);
        put(Blocks.GRAY_STAINED_GLASS_PANE, GemstoneType.ONYX);
        put(Blocks.BROWN_STAINED_GLASS, GemstoneType.CITRINE);
        put(Blocks.BROWN_STAINED_GLASS_PANE, GemstoneType.CITRINE);
        put(Blocks.GREEN_STAINED_GLASS, GemstoneType.PERIDOT);
        put(Blocks.GREEN_STAINED_GLASS_PANE, GemstoneType.PERIDOT);
    }

    private static void put(Block block, GemstoneType type) {
        BLOCK_TO_GEM.put(block, type);
    }

    /** Returns the GemstoneType if the block is a recognized gemstone block. */
    public static Optional<GemstoneType> fromBlock(Block block) {
        if (block == null) return Optional.empty();
        return Optional.ofNullable(BLOCK_TO_GEM.get(block));
    }

    /** Returns true if the block is any variant of stained glass or pane. */
    public static boolean isGemstoneBlock(Block block) {
        return block != null && BLOCK_TO_GEM.containsKey(block);
    }
}
