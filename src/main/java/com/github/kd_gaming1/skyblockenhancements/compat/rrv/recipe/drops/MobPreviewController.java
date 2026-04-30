package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import java.util.ArrayList;
import java.util.List;
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
    private final List<LivingEntity> entityStack = new ArrayList<>();

    private int animationTick;
    private boolean hovered;

    public MobPreviewController(@Nullable MobPreview preview) {
        this.preview = preview;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    /** Spawns living entities when the recipe becomes visible. Safe to call when {@code preview} is null. */
    public void init(ClientLevel level) {
        if (preview == null || !preview.needsLivingEntity()) return;
        spawnRecursive(level, preview, null);
    }

    private void spawnRecursive(ClientLevel level, MobPreview current, @Nullable LivingEntity mount) {
        LivingEntity entity = spawnForLayer(level, current.entityType());
        if (entity != null) {
            if (mount != null) {
                entity.startRiding(mount);
                mount.positionRider(entity);
            }
            entityStack.add(entity);
        }
        if (current.rider() != null) {
            spawnRecursive(level, current.rider(), entity != null ? entity : mount);
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
        for (LivingEntity entity : entityStack) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        entityStack.clear();
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
        syncPassengerPositions();

        if (preview != null) {
            MobPreviewRenderer.render(preview, gfx, x, y, entityStack, animationTick, partialTicks);
        } else {
            MobPreviewRenderer.renderPlaceholder(gfx, x, y);
        }
    }

    /** Re-applies vanilla passenger positioning so riders sit at the correct attachment point. */
    private void syncPassengerPositions() {
        for (int i = 0; i < entityStack.size() - 1; i++) {
            LivingEntity mount = entityStack.get(i);
            LivingEntity rider = entityStack.get(i + 1);
            if (rider.getVehicle() == mount) {
                mount.positionRider(rider);
            }
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
