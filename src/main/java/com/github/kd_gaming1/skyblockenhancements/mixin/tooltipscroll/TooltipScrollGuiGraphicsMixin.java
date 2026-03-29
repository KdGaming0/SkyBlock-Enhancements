package com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll.TooltipScrollState;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the tooltip scroll offset via matrix translation on {@code GuiGraphics.pose()}.
 *
 * <p>This mixin handles vanilla tooltip rendering only. When Modern UI's enhanced tooltip is
 * active, {@code GuiGraphics.renderTooltip()} is never called — Modern UI bypasses it entirely
 * and routes through {@code TooltipRenderer.drawTooltip()} instead. The Modern UI path is
 * handled by {@code TooltipScrollModernUIMixin} (conditionally loaded).
 */
@Mixin(GuiGraphics.class)
public abstract class TooltipScrollGuiGraphicsMixin {

    @Shadow
    @Final
    private Matrix3x2fStack pose;

    @Unique
    private boolean skyblockenhancements$pushed;

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void onTooltipHead(
            Font font,
            List<ClientTooltipComponent> components,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            @Nullable Identifier texture,
            CallbackInfo ci) {
        skyblockenhancements$pushed = false;

        if (!SkyblockEnhancementsConfig.enableTooltipScroll) {
            return;
        }

        // When Modern UI's tooltip is active, this method is never called, so no guard needed.
        // Track tooltip content — resets scroll when the hovered item changes.
        TooltipScrollState.trackTooltip(components);
        TooltipScrollState.update();

        float xOffset = TooltipScrollState.getXOffset();
        float yOffset = TooltipScrollState.getYOffset();
        if (xOffset != 0 || yOffset != 0) {
            pose.pushMatrix();
            pose.translate(xOffset, yOffset);
            skyblockenhancements$pushed = true;
        }
    }

    @Inject(method = "renderTooltip", at = @At("TAIL"))
    private void onTooltipTail(
            Font font,
            List<ClientTooltipComponent> components,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            @Nullable Identifier texture,
            CallbackInfo ci) {
        if (skyblockenhancements$pushed) {
            pose.popMatrix();
            skyblockenhancements$pushed = false;
        }
    }
}