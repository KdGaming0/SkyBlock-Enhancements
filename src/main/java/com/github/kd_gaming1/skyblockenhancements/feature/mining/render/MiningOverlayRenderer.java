package com.github.kd_gaming1.skyblockenhancements.feature.mining.render;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * Single-file renderer for the ping-offset mining overlay.
 *
 * <p>Draws a translucent fill + wireframe outline around the targeted block,
 * both rendered through walls so the player can see exactly when to switch.
 *
 * <p>All geometry respects the block's actual {@link VoxelShape} so thin
 * blocks (panes, slabs, stairs) get tight-fitting visuals instead of a
 * full 1x1x1 cube.
 */
public final class MiningOverlayRenderer {

    private static final MiningOverlayRenderer INSTANCE = new MiningOverlayRenderer();

    public static MiningOverlayRenderer getInstance() {
        return INSTANCE;
    }

    private MiningOverlayRenderer() {}

    private volatile double progress;
    private volatile int elapsedTick;
    private volatile BlockPos targetPos;

    // ── Public API ────────────────────────────────────────────────────────────

    public void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::onRender);
    }

    public void updateProgress(double progress, int elapsedTick, BlockPos targetPos) {
        this.progress = progress;
        this.elapsedTick = elapsedTick;
        this.targetPos = targetPos;
    }

    public void clear() {
        this.targetPos = null;
    }

    // ── Main render entry ─────────────────────────────────────────────────────

    private void onRender(LevelRenderContext context) {
        if (targetPos == null) return;
        if (!SkyblockEnhancementsConfig.enablePingOffsetMining) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean showHighlight = SkyblockEnhancementsConfig.pingOffsetShowHighlight;
        boolean showOutline = SkyblockEnhancementsConfig.pingOffsetShowOutline;
        if (!showHighlight && !showOutline) return;

        var consumers = context.bufferSource();
        if (consumers == null) return;

        BlockState blockState = mc.level.getBlockState(targetPos);
        VoxelShape shape = blockState.getShape(mc.level, targetPos);
        if (shape.isEmpty()) return;

        double currentProgress = this.progress;
        int currentTick = this.elapsedTick;
        int outlineColor = MiningColors.getOutlineColor(currentProgress, currentTick);
        int highlightColor = MiningColors.getHighlightColor(currentProgress, currentTick);

        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;

        Vec3 cameraPos = context.gameRenderer().getMainCamera().position();

        if (showHighlight) {
            VertexConsumer buffer = consumers.getBuffer(RenderTypes.HIGHLIGHT);
            renderHighlight(poseStack, buffer, shape, highlightColor, cameraPos, targetPos);
        }

        if (showOutline) {
            float lineWidth = (float) SkyblockEnhancementsConfig.pingOffsetLineWidth;
            VertexConsumer buffer = consumers.getBuffer(RenderTypes.OUTLINE);
            renderOutline(poseStack, buffer, shape, outlineColor, cameraPos, targetPos, lineWidth);
        }
    }

    // ── Translucent filled highlight ──────────────────────────────────────────

    private static void renderHighlight(PoseStack poseStack, VertexConsumer buffer,
                                        VoxelShape shape, int color, Vec3 cameraPos,
                                        BlockPos blockPos) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        poseStack.pushPose();
        poseStack.translate(
                (float) (blockPos.getX() - cameraPos.x),
                (float) (blockPos.getY() - cameraPos.y),
                (float) (blockPos.getZ() - cameraPos.z));

        PoseStack.Pose pose = poseStack.last();

        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                filledBox(pose, buffer,
                        (float) minX, (float) minY, (float) minZ,
                        (float) maxX, (float) maxY, (float) maxZ,
                        r, g, b, a));

        poseStack.popPose();
    }

    private static void filledBox(PoseStack.Pose pose, VertexConsumer buffer,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  int r, int g, int b, int a) {
        // Front
        quad(pose, buffer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        // Back
        quad(pose, buffer, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        // Left
        quad(pose, buffer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        // Right
        quad(pose, buffer, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        // Top
        quad(pose, buffer, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
        // Bottom
        quad(pose, buffer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
    }

    // ── Wireframe outline ─────────────────────────────────────────────────────

    private static void renderOutline(PoseStack poseStack, VertexConsumer buffer,
                                      VoxelShape shape, int color, Vec3 cameraPos,
                                      BlockPos blockPos, float lineWidth) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        poseStack.pushPose();
        poseStack.translate(
                (float) (blockPos.getX() - cameraPos.x),
                (float) (blockPos.getY() - cameraPos.y),
                (float) (blockPos.getZ() - cameraPos.z));

        PoseStack.Pose pose = poseStack.last();

        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            Vector3f dir = new Vector3f(
                    (float) (maxX - minX),
                    (float) (maxY - minY),
                    (float) (maxZ - minZ)
            ).normalize();

            buffer.addVertex(pose, (float) minX, (float) minY, (float) minZ)
                    .setColor(r, g, b, a)
                    .setNormal(pose, dir.x, dir.y, dir.z)
                    .setLineWidth(lineWidth);

            buffer.addVertex(pose, (float) maxX, (float) maxY, (float) maxZ)
                    .setColor(r, g, b, a)
                    .setNormal(pose, dir.x, dir.y, dir.z)
                    .setLineWidth(lineWidth);
        });

        poseStack.popPose();
    }

    // ── Shared: single quad (4 vertices) ──────────────────────────────────────

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             int r, int g, int b, int a) {
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }

    // ── RenderTypes: pipelines + render layers ────────────────────────────────

    /**
     * Custom render layers for the mining overlay.
     * Both disable depth testing so visuals show through walls.
     * The highlight uses translucent alpha blending; the outline uses lines.
     */
    private static final class RenderTypes {
        private RenderTypes() {}

        private static final RenderPipeline HIGHLIGHT_PIPELINE =
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation("pipeline/skyblock_mining_highlight")
                        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                        .withCull(false)
                        .build();

        /** Translucent filled quads, sorted back-to-front, no depth test. */
        static final RenderType HIGHLIGHT = RenderType.create(
                "skyblock_mining_highlight",
                RenderSetup.builder(HIGHLIGHT_PIPELINE).sortOnUpload().createRenderSetup());

        private static final RenderPipeline OUTLINE_PIPELINE =
                RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                        .withLocation("pipeline/skyblock_mining_outline")
                        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                        .build();

        /** Wireframe lines with no depth test — renders through walls. */
        static final RenderType OUTLINE = RenderType.create(
                "skyblock_mining_outline",
                RenderSetup.builder(OUTLINE_PIPELINE).createRenderSetup());
    }
}
