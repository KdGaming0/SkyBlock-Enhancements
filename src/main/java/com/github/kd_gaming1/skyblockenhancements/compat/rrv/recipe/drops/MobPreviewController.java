package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the entity lifecycle, animation, and rendering for a drop-recipe mob preview.
 * Extracted from {@link SkyblockDropsClientRecipe} so the recipe class can focus on
 * slot binding and UI rather than entity spawn/dispose bookkeeping.
 */
public class MobPreviewController {

    private final MobPreview preview;

    @Nullable private LivingEntity mountEntity;
    @Nullable private LivingEntity riderEntity;

    private int animationTick;
    private boolean hovered;

    public MobPreviewController(@Nullable MobPreview preview) {
        this.preview = preview;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    /** Spawns living entities when the recipe becomes visible. Safe to call when {@code preview} is null. */
    public void init(ClientLevel level) {
        if (preview == null || !preview.needsLivingEntity()) return;

        mountEntity = spawnForLayer(level, preview.entityType());
        MobPreview rider = preview.rider();
        if (rider != null) {
            riderEntity = spawnForLayer(level, rider.entityType());
            if (riderEntity != null && mountEntity != null) {
                riderEntity.startRiding(mountEntity);
            }
        }
    }

    @Nullable
    private static LivingEntity spawnForLayer(ClientLevel level, @Nullable EntityType<?> type) {
        if (type == null) return null;
        Entity entity = type.create(level, EntitySpawnReason.LOAD);
        if (!(entity instanceof LivingEntity living)) return null;
        living.setYBodyRot(30.0F);
        living.setYHeadRot(30.0F);
        return living;
    }

    /** Discards spawned entities when the recipe is hidden. */
    public void fade() {
        if (mountEntity != null) {
            mountEntity.remove(Entity.RemovalReason.DISCARDED);
            mountEntity = null;
        }
        if (riderEntity != null) {
            riderEntity.remove(Entity.RemovalReason.DISCARDED);
            riderEntity = null;
        }
    }

    // ── Animation ────────────────────────────────────────────────────────────────

    /** Advances the preview rotation animation. Call from the recipe's {@code tick()}. */
    public void tick() {
        if (hovered) return;
        animationTick++;
        if (animationTick >= MobPreviewRenderer.rotationPeriod()) animationTick = 0;
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    /**
     * Updates hover state, then renders the preview box (entity or placeholder).
     *
     * @param mouseX mouse X relative to the recipe card
     * @param mouseY mouse Y relative to the recipe card
     */
    public void render(GuiGraphics gfx, int x, int y, int mouseX, int mouseY, float partialTicks) {
        hovered = MobPreviewRenderer.isPointInPreviewBox(mouseX, mouseY);

        if (preview != null) {
            MobPreviewRenderer.render(preview, gfx, x, y, mountEntity, riderEntity, animationTick, partialTicks);
        } else {
            MobPreviewRenderer.renderPlaceholder(gfx, x, y);
        }
    }

    /** Whether the mouse is currently inside the preview hit-box. */
    public boolean isHovered() {
        return hovered;
    }

    @Nullable
    public MobPreview getPreview() {
        return preview;
    }
}
