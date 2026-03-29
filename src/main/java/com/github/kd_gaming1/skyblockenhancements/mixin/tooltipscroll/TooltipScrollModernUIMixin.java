package com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll;

import com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll.TooltipScrollState;
import icyllis.modernui.mc.ScrollController;
import icyllis.modernui.mc.TooltipRenderer;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into Modern UI's {@code TooltipRenderer.drawTooltip}.
 *
 * <p>Modern UI's own auto-scrolling stays active until the player scrolls the mouse
 * wheel.  Once the user scrolls, Modern UI's scrolling is suppressed and replaced
 * with the matrix translation supplied by {@link TooltipScrollState}.</p>
 */
@Mixin(value = TooltipRenderer.class, remap = false)
public abstract class TooltipScrollModernUIMixin {

    /* ──────────────── Modern UI fields ──────────────── */

    @Shadow private float mScroll;
    @Shadow private int   mMarqueeDir;
    @Shadow private int   mPendingArrowMove;
    @Shadow @Final private ScrollController mScroller;

    /* ──────────────── Local state ──────────────── */

    @Unique private boolean pushed;

    /* ──────────────── Injection ──────────────── */

    @Inject(method = "drawTooltip", at = @At("HEAD"))
    private void onHead(
            ItemStack                      stack,
            GuiGraphics                    gr,
            List<ClientTooltipComponent>   comps,
            int                            mouseX,
            int                            mouseY,
            Font                           font,
            int                            screenW,
            int                            screenH,
            float                          partialX,
            float                          partialY,
            ClientTooltipPositioner        positioner,
            @Nullable Object               style,
            CallbackInfo                   ci) {

        pushed = false;

        /* ── Suppress Modern UI scrolling once the user has scrolled ── */
        if (TooltipScrollState.hasUserScrolled()) {
            mScroll           = 0;
            mMarqueeDir       = 0;
            mPendingArrowMove = 0;
            mScroller.scrollTo(0);
            mScroller.abortAnimation();
        }

        /* ── Update our own scroll offsets ── */
        TooltipScrollState.trackTooltip(comps);
        TooltipScrollState.update();

        float dx = TooltipScrollState.getXOffset();
        float dy = TooltipScrollState.getYOffset();
        if (dx != 0 || dy != 0) {
            gr.pose().pushMatrix();
            gr.pose().translate(dx, dy);
            pushed = true;
        }
    }

    @Inject(method = "drawTooltip", at = @At("TAIL"))
    private void onTail(
            ItemStack                      stack,
            GuiGraphics                    gr,
            List<ClientTooltipComponent>   comps,
            int                            mouseX,
            int                            mouseY,
            Font                           font,
            int                            screenW,
            int                            screenH,
            float                          partialX,
            float                          partialY,
            ClientTooltipPositioner        positioner,
            @Nullable Object               style,
            CallbackInfo                   ci) {

        if (pushed) {
            gr.pose().popMatrix();
            pushed = false;
        }
    }
}