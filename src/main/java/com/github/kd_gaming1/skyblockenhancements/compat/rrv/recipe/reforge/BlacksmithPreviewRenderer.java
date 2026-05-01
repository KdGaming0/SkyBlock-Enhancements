package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.common.rendering.RrvGuiRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a temporary Villager entity as the blacksmith preview.
 * Falls back to a villager spawn egg if entity creation fails.
 */
public class BlacksmithPreviewRenderer {

    private static final int PREVIEW_SIZE = 36;
    private static final int PREVIEW_X = 4;
    private static final int PREVIEW_Y = 4;

    private static final float BASE_SCALE = 15.0F;
    private static final float MAX_HEIGHT = PREVIEW_SIZE - 4.0F;
    private static final float ROT_X_DEG = 180.0F;
    private static final int ROTATION_PERIOD = 360;

    @Nullable private LivingEntity villager;
    private int animationTick;
    private boolean initFailed;

    public void init(ClientLevel level) {
        if (villager != null || initFailed) return;
        try {
            Entity entity = EntityType.VILLAGER.create(level, EntitySpawnReason.LOAD);
            if (entity instanceof LivingEntity v) {
                v.setYBodyRot(30.0F);
                v.setYHeadRot(30.0F);
                this.villager = v;
            } else {
                initFailed = true;
            }
        } catch (Exception e) {
            initFailed = true;
        }
    }

    public void fade() {
        if (villager != null) {
            villager.remove(Entity.RemovalReason.DISCARDED);
            villager = null;
        }
    }

    public void tick() {
        animationTick++;
        if (animationTick >= ROTATION_PERIOD) animationTick = 0;
    }

    public void render(GuiGraphics gfx, int recipeLeft, int recipeTop, float partialTicks) {
        if (villager == null) {
            renderSpawnEggFallback(gfx, recipeLeft, recipeTop);
            return;
        }

        float scale = BASE_SCALE;
        AABB bb = villager.getBoundingBox();
        if (bb.getYsize() * scale > MAX_HEIGHT) {
            scale = (float) (MAX_HEIGHT / bb.getYsize());
        }

        int x0 = recipeLeft + PREVIEW_X;
        int y0 = recipeTop + PREVIEW_Y;
        int x1 = x0 + PREVIEW_SIZE;
        int y1 = y0 + PREVIEW_SIZE;

        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(ROT_X_DEG),
                (animationTick + partialTicks) / 180.0F * Mth.PI,
                0.0F);

        RrvGuiRenderHelper.renderEntityOnScreen(
                gfx, villager, x0, y0, x1, y1, scale,
                new Vector3f(0.0F, PREVIEW_SIZE / scale / 2.0F, 0.0F),
                rotation, null);
    }

    private void renderSpawnEggFallback(GuiGraphics gfx, int recipeLeft, int recipeTop) {
        ItemStack egg = new ItemStack(Items.VILLAGER_SPAWN_EGG);
        int x = recipeLeft + PREVIEW_X + (PREVIEW_SIZE - 16) / 2;
        int y = recipeTop + PREVIEW_Y + (PREVIEW_SIZE - 16) / 2;
        gfx.renderItem(egg, x, y);
    }
}
