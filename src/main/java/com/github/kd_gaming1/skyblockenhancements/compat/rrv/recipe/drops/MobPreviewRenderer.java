package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.common.rendering.RrvGuiRenderHelper;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobSkinRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a {@link MobPreview} into a centered preview box above the drop slot grid.
 * One render path per {@link MobPreview.Kind}; composites render as a stacked unit.
 *
 * <p>Each render path returns {@code true} on success or {@code false} on failure.
 * Failed renders fall through to a placeholder glyph so the preview box is never blank.
 */
public final class MobPreviewRenderer {

    private static final int BOX_SIZE = SkyblockDropsRecipeType.PREVIEW_BOX_SIZE;
    private static final int BOX_TOP  = SkyblockDropsRecipeType.PREVIEW_BOX_TOP;
    private static final int BOX_LEFT = (SkyblockDropsRecipeType.displayWidth() - BOX_SIZE) / 2;

    /**
     * Scale factor passed to {@code submitSkinRenderState}. The player model spans 32 model
     * units (head top at Y=-8, feet at Y=24). The PIP renderer multiplies this by
     * {@code guiScale} and uses the result for {@code poseStack.scale()}, so the final pixel
     * height is approximately {@code MODEL_HEIGHT * SKIN_SCALE * guiScale}. This must fit
     * within the render texture ({@code BOX_SIZE * guiScale} pixels).
     *
     * <p>{@code BOX_SIZE / MODEL_HEIGHT = 36 / 32 = 1.125} — we use 1.0 to leave a small
     * margin so the model doesn't clip at the edges.
     */
    private static final float SKIN_SCALE = 15.0F;

    /**
     * Vertical pivot for the X-rotation tilt, in model units. The renderer applies the
     * X-rotation around {@code (0, -scale * guiScale * pivotY, 0)}. A value of 12 centers
     * the rotation around the model's waist (midpoint of the 32-unit height).
     */
    private static final float SKIN_PIVOT_Y = 12.0F;

    /** Slight downward tilt (degrees) so the mob isn't staring straight at the camera. */
    private static final float SKIN_ROT_X = (float) Math.toRadians(20.0);

    private static final int ROTATION_PERIOD = 360;

    private static final float VANILLA_BASE_SCALE = 15.0F;
    private static final float VANILLA_MAX_HEIGHT = BOX_SIZE - 4.0F;
    private static final float VANILLA_ROT_X_DEG  = 180.0F;

    private static final float SKULL_SCALE = 2.0F;

    private static final String PLACEHOLDER_GLYPH = "?";
    private static final int    PLACEHOLDER_COLOR = 0xFFAAAAAA;

    /** Prevents per-frame log spam for skins that fail to load. */
    private static final Set<String> LOGGED_SKIN_FAILURES = ConcurrentHashMap.newKeySet();

    private static PlayerModel cachedPlayerModel;
    private static boolean     playerModelLookupFailed;

    private MobPreviewRenderer() {}

    /**
     * Renders the given preview into the preview box. Falls back to the placeholder
     * glyph if rendering fails for any reason (missing skin, broken model, etc.).
     */
    public static void render(MobPreview preview, GuiGraphics gfx,
                              int recipeLeft, int recipeTop,
                              List<LivingEntity> entityStack,
                              int animTick, float partialTicks) {
        boolean rendered = switch (preview.kind()) {
            case VANILLA_ENTITY   -> renderVanilla(topEntity(entityStack), gfx, recipeLeft, recipeTop, animTick, partialTicks, BOX_TOP);
            case PLAYER_WITH_SKIN -> renderPlayerSkin(preview.skinPath(), gfx, recipeLeft, recipeTop, animTick, partialTicks, BOX_TOP);
            case SKULL_ITEM       -> renderSkull(preview.helmetItemId(), gfx, recipeLeft, recipeTop, BOX_TOP);
            case COMPOSITE        -> renderComposite(preview, gfx, recipeLeft, recipeTop, entityStack, animTick, partialTicks);
        };

        if (!rendered) {
            renderPlaceholder(gfx, recipeLeft, recipeTop);
        }
    }

    @Nullable
    private static LivingEntity topEntity(List<LivingEntity> entityStack) {
        return entityStack.isEmpty() ? null : entityStack.get(0);
    }

    public static void renderPlaceholder(GuiGraphics gfx, int recipeLeft, int recipeTop) {
        Font font = Minecraft.getInstance().font;
        int centerX = recipeLeft + BOX_LEFT + BOX_SIZE / 2;
        int centerY = recipeTop + BOX_TOP + BOX_SIZE / 2 - font.lineHeight / 2;
        int x = centerX - font.width(PLACEHOLDER_GLYPH) / 2;
        gfx.drawString(font, PLACEHOLDER_GLYPH, x, centerY, PLACEHOLDER_COLOR, false);
    }

    // ── Composite ───────────────────────────────────────────────────────────────

    private static boolean renderComposite(MobPreview preview, GuiGraphics gfx,
                                           int recipeLeft, int recipeTop,
                                           List<LivingEntity> entityStack,
                                           int animTick, float partialTicks) {
        List<MobPreview> layers = collectLayers(preview);

        boolean allVanilla = true;
        for (MobPreview layer : layers) {
            if (layer.skinPath() != null || layer.helmetItemId() != null) {
                allVanilla = false;
                break;
            }
        }

        if (allVanilla && !entityStack.isEmpty()) {
            return renderEntityStack(entityStack, gfx, recipeLeft, recipeTop, animTick, partialTicks);
        }

        return renderMixedStack(layers, entityStack, gfx, recipeLeft, recipeTop, animTick, partialTicks);
    }

    /** Flattens the composite chain into a bottom-to-top list of layers. */
    private static List<MobPreview> collectLayers(MobPreview preview) {
        List<MobPreview> layers = new ArrayList<>();
        MobPreview current = preview;
        while (current != null) {
            layers.add(current);
            if (current.kind() == MobPreview.Kind.COMPOSITE) {
                current = current.rider();
            } else {
                current = null;
            }
        }
        return layers;
    }

    /** Renders a stack of pure vanilla entities into a single shared viewport. */
    private static boolean renderEntityStack(List<LivingEntity> entityStack, GuiGraphics gfx,
                                             int recipeLeft, int recipeTop,
                                             int animTick, float partialTicks) {
        if (entityStack.isEmpty()) return false;

        double stackBottom = Double.MAX_VALUE;
        double stackTop = -Double.MAX_VALUE;
        for (LivingEntity entity : entityStack) {
            AABB bb = entity.getBoundingBox();
            stackBottom = Math.min(stackBottom, bb.minY);
            stackTop = Math.max(stackTop, bb.maxY);
        }
        double stackHeight = Math.max(1.0, stackTop - stackBottom);
        double stackCenter = (stackBottom + stackTop) / 2.0;

        float scale = VANILLA_BASE_SCALE;
        if (stackHeight * scale > VANILLA_MAX_HEIGHT) {
            scale = (float) (VANILLA_MAX_HEIGHT / stackHeight);
        }

        int x0 = recipeLeft + BOX_LEFT;
        int y0 = recipeTop + BOX_TOP;
        int x1 = x0 + BOX_SIZE;
        int y1 = y0 + BOX_SIZE;

        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(VANILLA_ROT_X_DEG),
                (animTick + partialTicks) / 180.0F * Mth.PI,
                0.0F);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean anyRendered = false;

        for (LivingEntity entity : entityStack) {
            @SuppressWarnings("unchecked")
            EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
            EntityRenderState state = renderer.createRenderState(entity, 1.0F);
            state.lightCoords = 15728880;
            state.shadowPieces.clear();
            state.outlineColor = 0;

            float translationY = (float) (stackCenter - entity.getY());

            gfx.submitEntityRenderState(state, scale,
                    new Vector3f(0.0F, translationY, 0.0F),
                    rotation, null, x0, y0, x1, y1);
            anyRendered = true;
        }

        return anyRendered;
    }

    /** Fallback for stacks containing skins or skulls. Renders each layer separately with computed offsets. */
    private static boolean renderMixedStack(List<MobPreview> layers, List<LivingEntity> entityStack,
                                            GuiGraphics gfx, int recipeLeft, int recipeTop,
                                            int animTick, float partialTicks) {
        double stackBottom = Double.MAX_VALUE;
        double stackTop = -Double.MAX_VALUE;
        for (LivingEntity entity : entityStack) {
            AABB bb = entity.getBoundingBox();
            stackBottom = Math.min(stackBottom, bb.minY);
            stackTop = Math.max(stackTop, bb.maxY);
        }
        double stackHeight = Math.max(1.0, stackTop - stackBottom);
        float uniformScale = VANILLA_BASE_SCALE;
        if (stackHeight * uniformScale > VANILLA_MAX_HEIGHT) {
            uniformScale = (float) (VANILLA_MAX_HEIGHT / stackHeight);
        }

        boolean anyOk = false;
        int entityIdx = 0;
        int cumulativeYOffset = 0;

        for (MobPreview layer : layers) {
            @Nullable LivingEntity entity = null;
            if (layer.entityType() != null && entityIdx < entityStack.size()) {
                entity = entityStack.get(entityIdx++);
            }

            if (entity != null && entityIdx > 1) {
                LivingEntity prev = entityStack.get(entityIdx - 2);
                double prevCenter = prev.getBoundingBox().getCenter().y;
                double currCenter = entity.getBoundingBox().getCenter().y;
                int pixelOffset = (int) Math.round((currCenter - prevCenter) * uniformScale);
                cumulativeYOffset -= pixelOffset;
            } else if (entity == null && entityIdx > 0) {
                // Non-entity layer (skin/skull) above an entity: approximate offset from mount top
                LivingEntity prev = entityStack.get(entityIdx - 1);
                double prevCenter = prev.getBoundingBox().getCenter().y;
                double prevHeight = prev.getBoundingBox().getYsize();
                double approxRiderCenter = prevCenter + prevHeight / 2.0 + 0.6;
                int pixelOffset = (int) Math.round((approxRiderCenter - prevCenter) * uniformScale);
                cumulativeYOffset -= pixelOffset;
            }

            int yBase = BOX_TOP + cumulativeYOffset;

            if (layer.skinPath() != null) {
                anyOk |= renderPlayerSkin(layer.skinPath(), gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase);
            } else if (layer.helmetItemId() != null) {
                anyOk |= renderSkull(layer.helmetItemId(), gfx, recipeLeft, recipeTop, yBase);
            } else if (entity != null) {
                anyOk |= renderVanilla(entity, gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase, uniformScale);
            }
        }

        return anyOk;
    }

    private static boolean drawLayer(MobPreview layer, @Nullable LivingEntity vanillaEntity,
                                     GuiGraphics gfx, int recipeLeft, int recipeTop,
                                     int animTick, float partialTicks, int yBase) {
        if (layer.skinPath() != null) {
            return renderPlayerSkin(layer.skinPath(), gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase);
        }
        if (layer.helmetItemId() != null) {
            return renderSkull(layer.helmetItemId(), gfx, recipeLeft, recipeTop, yBase);
        }
        if (vanillaEntity != null) {
            return renderVanilla(vanillaEntity, gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase);
        }
        return false;
    }

    // ── Vanilla-entity path ─────────────────────────────────────────────────────

    private static boolean renderVanilla(@Nullable LivingEntity entity, GuiGraphics gfx,
                                         int recipeLeft, int recipeTop,
                                         int animTick, float partialTicks, int yBase) {
        return renderVanilla(entity, gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase, -1.0F);
    }

    private static boolean renderVanilla(@Nullable LivingEntity entity, GuiGraphics gfx,
                                         int recipeLeft, int recipeTop,
                                         int animTick, float partialTicks, int yBase,
                                         float overrideScale) {
        if (entity == null) return false;

        float scale = overrideScale > 0 ? overrideScale : VANILLA_BASE_SCALE;
        if (overrideScale <= 0.0F) {
            AABB bb = entity.getBoundingBox();
            if (bb.getYsize() * scale > VANILLA_MAX_HEIGHT) {
                scale = (float) (VANILLA_MAX_HEIGHT / bb.getYsize());
            }
        }

        int x0 = recipeLeft + BOX_LEFT;
        int y0 = recipeTop + yBase;
        int x1 = x0 + BOX_SIZE;
        int y1 = y0 + BOX_SIZE;

        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(VANILLA_ROT_X_DEG),
                (animTick + partialTicks) / 180.0F * Mth.PI,
                0.0F);

        RrvGuiRenderHelper.renderEntityOnScreen(
                gfx, entity, x0, y0, x1, y1, scale,
                new Vector3f(0.0F, BOX_SIZE / scale / 2.0F, 0.0F),
                rotation, null);
        return true;
    }

    // ── Player-skin path ────────────────────────────────────────────────────────

    private static boolean renderPlayerSkin(String skinPath, GuiGraphics gfx,
                                           int recipeLeft, int recipeTop,
                                           int animTick, float partialTicks, int yBase) {
        Identifier texture = MobSkinRegistry.getOrLoad(skinPath);
        if (texture == null) {
            if (LOGGED_SKIN_FAILURES.add(skinPath)) {
                LOGGER.warn("Player skin texture failed to load for '{}' — showing placeholder.", skinPath);
            }
            return false;
        }

        PlayerModel model = getPlayerModel();
        if (model == null) {
            if (LOGGED_SKIN_FAILURES.add("__player_model__")) {
                LOGGER.warn("PlayerModel is unavailable — player-skin mob previews will not render.");
            }
            return false;
        }

        int x0 = recipeLeft + BOX_LEFT;
        int y0 = recipeTop + yBase;
        int x1 = x0 + BOX_SIZE;
        int y1 = y0 + BOX_SIZE;

        float rotY = (animTick + partialTicks) * Mth.DEG_TO_RAD; // ← convert to radians

        model.setAllVisible(true);

        gfx.submitSkinRenderState(
                model, texture, SKIN_SCALE, SKIN_ROT_X, rotY, SKIN_PIVOT_Y, x0, y0, x1, y1);
        return true;
    }

    private static PlayerModel getPlayerModel() {
        if (cachedPlayerModel != null) return cachedPlayerModel;
        if (playerModelLookupFailed)    return null;

        try {
            EntityModelSet models = Minecraft.getInstance().getEntityModels();
            cachedPlayerModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
            LOGGER.debug("PlayerModel created successfully for drop preview.");
            return cachedPlayerModel;
        } catch (Exception e) {
            playerModelLookupFailed = true;
            LOGGER.error("Failed to create PlayerModel for drop preview.", e);
            return null;
        }
    }

    public static void invalidatePlayerModel() {
        cachedPlayerModel = null;
        playerModelLookupFailed = false;
        LOGGED_SKIN_FAILURES.clear();
    }

    // ── Skull path ──────────────────────────────────────────────────────────────

    private static boolean renderSkull(String helmetItemId, GuiGraphics gfx,
                                       int recipeLeft, int recipeTop, int yBase) {
        NeuItem item = NeuItemRegistry.get(helmetItemId);
        if (item == null) return false;

        ItemStack stack = ItemStackBuilder.build(item);
        if (stack.isEmpty()) return false;

        ItemStackBuilder.ensureSkinLoaded(stack);

        int centerX = recipeLeft + BOX_LEFT + (BOX_SIZE - 16) / 2;
        int centerY = recipeTop + yBase + (BOX_SIZE - 16) / 2;

        gfx.pose().pushMatrix();
        gfx.pose().identity();
        gfx.pose().translate(centerX + 8, centerY + 8);
        gfx.pose().scale(SKULL_SCALE, SKULL_SCALE);
        gfx.pose().translate(-8, -8);
        gfx.renderItem(stack, 0, 0);
        gfx.pose().popMatrix();
        return true;
    }

    // ── Hover hit-testing ───────────────────────────────────────────────────────

    public static boolean isPointInPreviewBox(int mouseX, int mouseY) {
        return mouseX >= BOX_LEFT && mouseX < BOX_LEFT + BOX_SIZE
                && mouseY >= BOX_TOP && mouseY < BOX_TOP + BOX_SIZE;
    }

    public static int rotationPeriod() { return ROTATION_PERIOD; }
}
