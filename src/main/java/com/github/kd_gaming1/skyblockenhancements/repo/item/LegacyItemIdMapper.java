package com.github.kd_gaming1.skyblockenhancements.repo.item;

/**
 * Translates pre-1.13 numeric item IDs + damage values into modern 1.21 identifiers.
 *
 * <p>Pulled out of {@link ItemStackBuilder} so the mapping tables don't clutter the builder's
 * flow logic. Only consulted when a NEU item has no companion {@code .snbt} file with a modern ID.
 */
final class LegacyItemIdMapper {

    private LegacyItemIdMapper() {}

    static String map(String itemId, int damage) {
        return switch (itemId) {
            case "minecraft:skull"    -> mapSkull(damage);
            case "minecraft:dye"      -> mapDye(damage);
            case "minecraft:banner"   -> mapBanner(damage);

            case "minecraft:noteblock"             -> "minecraft:note_block";
            case "minecraft:bed"                   -> "minecraft:white_bed";
            case "minecraft:fish"                  -> "minecraft:cod";
            case "minecraft:mob_spawner"           -> "minecraft:spawner";
            case "minecraft:monster_egg"           -> "minecraft:infested_stone";
            case "minecraft:tallgrass"             -> "minecraft:short_grass";
            case "minecraft:stained_glass_pane"    -> "minecraft:black_stained_glass_pane";
            case "minecraft:stained_hardened_clay" -> "minecraft:red_terracotta";
            case "minecraft:planks"                -> "minecraft:oak_planks";
            case "minecraft:potion"                -> "minecraft:potion";
            case "minecraft:spawn_egg"             -> "minecraft:bat_spawn_egg";

            default -> itemId;
        };
    }

    private static String mapSkull(int damage) {
        return switch (damage) {
            case 1 -> "minecraft:wither_skeleton_skull";
            case 2 -> "minecraft:zombie_head";
            case 3 -> "minecraft:player_head";
            case 4 -> "minecraft:creeper_head";
            case 5 -> "minecraft:dragon_head";
            default -> "minecraft:skeleton_skull";
        };
    }

    private static String mapDye(int damage) {
        return switch (damage) {
            case 4  -> "minecraft:lapis_lazuli";
            case 6  -> "minecraft:cyan_dye";
            case 8  -> "minecraft:gray_dye";
            case 15 -> "minecraft:bone_meal";
            default -> "minecraft:ink_sac";
        };
    }

    private static String mapBanner(int damage) {
        return switch (damage) {
            case 0  -> "minecraft:black_banner";
            case 1  -> "minecraft:red_banner";
            case 2  -> "minecraft:green_banner";
            case 3  -> "minecraft:brown_banner";
            case 4  -> "minecraft:blue_banner";
            case 5  -> "minecraft:purple_banner";
            case 6  -> "minecraft:cyan_banner";
            case 7  -> "minecraft:light_gray_banner";
            case 8  -> "minecraft:gray_banner";
            case 9  -> "minecraft:pink_banner";
            case 10 -> "minecraft:lime_banner";
            case 11 -> "minecraft:yellow_banner";
            case 12 -> "minecraft:light_blue_banner";
            case 13 -> "minecraft:magenta_banner";
            case 14 -> "minecraft:orange_banner";
            default -> "minecraft:white_banner";
        };
    }
}