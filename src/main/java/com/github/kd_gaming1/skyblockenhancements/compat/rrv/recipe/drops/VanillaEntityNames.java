package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves NEU's vanilla-name render strings ({@code "Spider"}, {@code "Mooshroom"},
 * {@code "Eisengolem"}, ...) into real {@link EntityType}s on 1.21.
 *
 * <p>NEU uses a historic mix of:
 * <ul>
 *   <li>1.8-era PascalCase ({@code MagmaCube}, {@code CaveSpider})</li>
 *   <li>Pre-rename entity names ({@code Mooshroom} → {@code mooshroom_cow}, {@code Snowman} → {@code snow_golem})</li>
 *   <li>Non-English names ({@code Eisengolem} → {@code iron_golem})</li>
 *   <li>Typos ({@code Salmom}, {@code SiNelverfish})</li>
 * </ul>
 *
 * <p>The lookup table is closed — anything not in the table falls back to a camelCase→snake_case
 * conversion and a registry lookup. Unresolvable strings return {@code null}; callers render a
 * text-only fallback.
 */
public final class VanillaEntityNames {

    private static final Map<String, EntityType<?>> ALIASES = new HashMap<>();

    static {
        alias("Mooshroom",     EntityType.MOOSHROOM);
        alias("CaveSpider",    EntityType.CAVE_SPIDER);
        alias("MagmaCube",     EntityType.MAGMA_CUBE);
        alias("GlowSquid",     EntityType.GLOW_SQUID);
        alias("Snowman",       EntityType.SNOW_GOLEM);
        alias("Eisengolem",    EntityType.IRON_GOLEM);
        alias("Pigman",        EntityType.ZOMBIFIED_PIGLIN);
        alias("SkeletonHorse", EntityType.SKELETON_HORSE);
        alias("Dragon",        EntityType.ENDER_DRAGON);
        alias("Salmom",        EntityType.SALMON);
        alias("SiNelverfish",  EntityType.SILVERFISH);
    }

    private static void alias(String neuName, EntityType<?> type) {
        ALIASES.put(neuName, type);
    }

    private VanillaEntityNames() {}

    @Nullable
    public static EntityType<?> resolve(String neuName) {
        if (neuName == null || neuName.isEmpty()) return null;

        EntityType<?> aliased = ALIASES.get(neuName);
        if (aliased != null) return aliased;

        Identifier id = Identifier.tryBuild("minecraft", toSnakeCase(neuName));
        if (id == null) return null;

        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /** {@code "MagmaCube"} → {@code "magma_cube"}. */
    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(name.charAt(i - 1))) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}