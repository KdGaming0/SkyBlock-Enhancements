package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderDefinition;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobSkinRegistry;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a NEU drop-recipe {@code render} string into a {@link MobPreview} render plan.
 *
 * <p>Two input shapes:
 * <ul>
 *   <li><b>Vanilla name</b> — e.g. {@code "Spider"}, {@code "Eisengolem"}. Routed through
 *       {@link VanillaEntityNames} and wrapped as {@link MobPreview.Kind#VANILLA_ENTITY}.</li>
 *   <li><b>NEU ref</b> — e.g. {@code "@neurepo:mobs/goblin.json"}. Looked up in
 *       {@link MobRenderRegistry}; the resulting {@link MobRenderDefinition} is translated
 *       into the richest possible preview (player skin, skull, or composite).</li>
 * </ul>
 *
 * <p>Unknown names, unparseable JSONs, and broken ArmorStand definitions return {@code null}
 * — the caller substitutes a placeholder.
 */
public final class MobRenderResolver {

    private static final String NEU_PREFIX = "@neurepo:";

    private MobRenderResolver() {}

    @Nullable
    public static MobPreview resolve(@Nullable String renderRef) {
        if (renderRef == null || renderRef.isEmpty()) return null;

        if (renderRef.startsWith(NEU_PREFIX)) {
            MobRenderDefinition def = MobRenderRegistry.get(renderRef);
            if (def == null) {
                LOGGER.debug("No MobRenderDefinition found for '{}' (registry has {} entries).",
                        renderRef, MobRenderRegistry.size());
                return null;
            }
            MobPreview preview = toPreview(def);
            if (preview == null) {
                LOGGER.debug("MobRenderDefinition for '{}' produced null preview "
                                + "(entity={}, skin={}, helmet={}).",
                        renderRef, def.entityKind(), def.skinPath(), def.helmetItemId());
            }
            return preview;
        }
        return resolveVanilla(renderRef);
    }

    /** Converts a parsed definition into the strongest preview kind its fields support. */
    @Nullable
    private static MobPreview toPreview(MobRenderDefinition def) {
        MobPreview base = baseFor(def);
        if (base == null) return null;

        if (def.rider() == null) return base;

        MobPreview riderPreview = toPreview(def.rider());
        if (riderPreview == null) return base;

        return MobPreview.composite(base, riderPreview);
    }

    @Nullable
    private static MobPreview baseFor(MobRenderDefinition def) {
        if (def.isArmorStandSkull()) {
            return MobPreview.skull(def.helmetItemId());
        }
        if ("Player".equals(def.entityKind()) && def.skinPath() != null) {
            if (!MobSkinRegistry.has(def.skinPath())) {
                LOGGER.warn("Player mob definition references skin '{}' but no PNG is stored.",
                        def.skinPath());
            }
            return MobPreview.playerSkin(def.skinPath());
        }
        if ("Horse".equals(def.entityKind()) && def.horseKind() != null) {
            EntityType<?> horseType = resolveHorseKind(def.horseKind());
            if (horseType != null) {
                return MobPreview.vanilla(horseType);
            }
        }
        return resolveVanilla(def.entityKind());
    }

    @Nullable
    private static EntityType<?> resolveHorseKind(String horseKind) {
        return switch (horseKind.toLowerCase()) {
            case "skeleton" -> EntityType.SKELETON_HORSE;
            case "zombie" -> EntityType.ZOMBIE_HORSE;
            default -> null;
        };
    }

    @Nullable
    private static MobPreview resolveVanilla(String name) {
        EntityType<?> type = VanillaEntityNames.resolve(name);
        if (type == null) {
            LOGGER.debug("Could not resolve vanilla entity name '{}'.", name);
        }
        return type != null ? MobPreview.vanilla(type) : null;
    }
}