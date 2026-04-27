package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved render plan for a drop recipe's mob preview. Produced by {@link MobRenderResolver},
 * consumed by {@link MobPreviewRenderer}.
 *
 * <p>Four mutually exclusive cases, driven by {@link Kind}:
 * <ul>
 *   <li>{@link Kind#VANILLA_ENTITY} — render a vanilla {@link EntityType} as a {@code LivingEntity}.</li>
 *   <li>{@link Kind#PLAYER_WITH_SKIN} — render a {@code PlayerModel} with a NEU PNG skin applied.</li>
 *   <li>{@link Kind#SKULL_ITEM} — render a skull {@code ItemStack} built from {@code NeuItem} metadata.</li>
 *   <li>{@link Kind#COMPOSITE} — mount-plus-rider: any two of the above stacked.</li>
 * </ul>
 */
public record MobPreview(
        Kind kind,
        @Nullable EntityType<?> entityType,
        @Nullable String skinPath,
        @Nullable String helmetItemId,
        @Nullable MobPreview rider) {

    public enum Kind { VANILLA_ENTITY, PLAYER_WITH_SKIN, SKULL_ITEM, COMPOSITE }

    public static MobPreview vanilla(EntityType<?> type) {
        return new MobPreview(Kind.VANILLA_ENTITY, type, null, null, null);
    }

    public static MobPreview playerSkin(String skinPath) {
        return new MobPreview(Kind.PLAYER_WITH_SKIN, null, skinPath, null, null);
    }

    public static MobPreview skull(String helmetItemId) {
        return new MobPreview(Kind.SKULL_ITEM, null, null, helmetItemId, null);
    }

    public static MobPreview composite(MobPreview mount, MobPreview rider) {
        return new MobPreview(Kind.COMPOSITE,
                mount.entityType, mount.skinPath, mount.helmetItemId, rider);
    }

    /** {@code true} if this preview — or any nested layer — needs a spawned {@code LivingEntity}. */
    public boolean needsLivingEntity() {
        return switch (kind) {
            case VANILLA_ENTITY -> true;
            case COMPOSITE -> entityType != null
                    || (rider != null && rider.needsLivingEntity());
            default -> false;
        };
    }
}