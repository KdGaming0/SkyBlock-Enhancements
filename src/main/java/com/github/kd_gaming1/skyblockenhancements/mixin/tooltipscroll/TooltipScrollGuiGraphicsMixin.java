package com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll.TopAnchoredTooltipPositioner;
import com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll.TooltipScrollState;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class TooltipScrollGuiGraphicsMixin {

    @Shadow @Final private Matrix3x2fStack pose;

    /** True when we pushed a matrix this frame and must pop it in TAIL. */
    @Unique private boolean skyblockenhancements$matrixPushed;

    // ── Positioner replacement ────────────────────────────────────────────────

    /**
     * Redirects the {@code positioner.positionTooltip(...)} call inside
     * {@code GuiGraphics.renderTooltip}.
     */
    @Redirect(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"
            )
    )
    private Vector2ic redirectPositioner(
            ClientTooltipPositioner positioner,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            int tooltipWidth,
            int tooltipHeight) {

        if (SkyblockEnhancementsConfig.enableTooltipScroll
                && SkyblockEnhancementsConfig.anchorTooltipToTop) {
            return TopAnchoredTooltipPositioner.INSTANCE.positionTooltip(
                    screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
        }

        // Vanilla path — call the original positioner unchanged.
        return positioner.positionTooltip(
                screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
    }

    // ── Scroll offset application ─────────────────────────────────────────────

    /**
     * Before the tooltip renders, track it and push a translated matrix if
     * there is a non-zero scroll offset to apply.
     */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD")
    )
    private void onRenderTooltipHead(
            Font font,
            List<ClientTooltipComponent> components,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            Identifier style,
            CallbackInfo ci) {

        skyblockenhancements$matrixPushed = false;
        if (!SkyblockEnhancementsConfig.enableTooltipScroll) return;

        // Detect tooltip change and reset offsets when hovering a different item.
        TooltipScrollState.trackTooltip(components);
        TooltipScrollState.update();

        float xOffset = TooltipScrollState.getXOffset();
        float yOffset = TooltipScrollState.getYOffset();

        if (xOffset != 0f || yOffset != 0f) {
            pose.pushMatrix();
            pose.translate(xOffset, yOffset);
            skyblockenhancements$matrixPushed = true;
        }
    }

    /**
     * After rendering, pop the matrix if we pushed one.
     * The push/pop pairing must always be symmetric.
     */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("TAIL")
    )
    private void onRenderTooltipTail(
            Font font,
            List<ClientTooltipComponent> components,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            Identifier style,
            CallbackInfo ci) {

        if (skyblockenhancements$matrixPushed) {
            pose.popMatrix();
            skyblockenhancements$matrixPushed = false;
        }
    }
}