package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.search;

import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces the calculator hint (set via {@link EditBox#setSuggestion}) to always render
 * at the end of the typed text, regardless of cursor position.
 *
 * <p>Vanilla {@code EditBox} only draws its suggestion when the cursor is at the very
 * end of the value ({@code !insert} guard in {@code renderWidget}). This mixin bypasses
 * that guard by:
 * <ol>
 *   <li>Snapshotting and clearing the suggestion at HEAD so vanilla never draws it.</li>
 *   <li>Restoring and re-drawing it at TAIL, anchored to the end of the visible text.</li>
 * </ol>
 *
 * <p>The field mutation is conditional — it only occurs when the suggestion is non-empty,
 * avoiding unnecessary per-frame writes when no calculator result is present.
 *
 * <p>Only active for {@link SearchBar} instances.
 */
@Mixin(EditBox.class)
public abstract class EditBoxCalculatorHintMixin {

    @Final @Shadow private Font font;
    @Shadow private String value;
    @Shadow private int displayPos;
    @Shadow private int textColor;
    @Shadow private boolean textShadow;
    @Shadow private String suggestion;

    @Shadow private int textX;
    @Shadow private int textY;

    @Shadow public abstract int getInnerWidth();

    // ── Render-thread scratch state ─────────────────────────────────────────────

    /** Holds the original suggestion while vanilla renders, or {@code null} if unchanged. */
    @Unique private transient String sbe$pendingSuggestion;
    /** Set to {@code true} only when we actually cleared the field for this frame. */
    @Unique private transient boolean sbe$didSuppress;

    // ── Injections ──────────────────────────────────────────────────────────────

    /**
     * Snapshot and suppress the suggestion before vanilla's render pass so vanilla
     * never draws it (regardless of cursor position).
     *
     * <p>Only mutates the field when the suggestion is non-empty, avoiding needless
     * writes on every render frame.
     */
    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void sbe$suppressVanillaSuggestion(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {

        if (!isTrackedSearchBar()) return;
        if (suggestion == null || suggestion.isEmpty()) return;

        sbe$pendingSuggestion = suggestion;
        sbe$didSuppress = true;
        suggestion = null;
    }

    /**
     * After vanilla finishes, restore the suggestion field and draw it ourselves
     * anchored to the end of the visible text.
     */
    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void sbe$renderCalculatorHintAlways(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {

        if (!isTrackedSearchBar()) return;
        if (!sbe$didSuppress) return;

        sbe$didSuppress = false;
        suggestion = sbe$pendingSuggestion;
        sbe$pendingSuggestion = null;

        if (suggestion == null || suggestion.isEmpty()) return;
        if (font == null) return;

        String displayed = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        int hintX = textX + font.width(displayed);

        gfx.drawString(font, suggestion, hintX, textY, dimColor(textColor), textShadow);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    @Unique
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "ConstantValue"})
    private boolean isTrackedSearchBar() {
        return RrvCompat.isActive() && ((Object) this) instanceof SearchBar;
    }

    @Unique
    private static int dimColor(int color) {
        int r = ((color >> 16) & 0xFF) / 2;
        int g = ((color >>  8) & 0xFF) / 2;
        int b =  (color        & 0xFF) / 2;
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
