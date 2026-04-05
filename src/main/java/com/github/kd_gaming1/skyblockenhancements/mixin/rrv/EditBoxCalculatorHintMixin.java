package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

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
 *   <li>Nulling the suggestion at HEAD so vanilla never draws it.</li>
 *   <li>Re-drawing it ourselves at TAIL, anchored to {@code textX + width(visibleText)},
 *       which is independent of cursor position.</li>
 * </ol>
 *
 * <p>Only active for {@link SearchBar} instances — every other {@code EditBox} in the
 * game is unaffected.
 */
@Mixin(EditBox.class)
public abstract class EditBoxCalculatorHintMixin {

    @Final
    @Shadow private Font font;
    @Shadow private String value;
    @Shadow private int displayPos;
    @Shadow private int textColor;
    @Shadow private boolean textShadow;
    @Shadow private String suggestion;

    @Shadow private int textX;
    @Shadow private int textY;

    @Shadow public abstract int getInnerWidth();

    // ── Render-thread scratch: holds the suggestion while vanilla renders ────────

    @Unique
    private transient String sbe$pendingSuggestion;

    // ── Injections ────────────────────────────────────────────────────────────────

    /**
     * Snapshot and suppress the suggestion before vanilla's render pass so vanilla
     * never draws it (regardless of cursor position).
     */
    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void sbe$suppressVanillaSuggestion(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {

        if (!isTrackedSearchBar()) return;
        sbe$pendingSuggestion = suggestion;
        suggestion = null;
    }

    /**
     * After vanilla finishes, restore the suggestion field and draw it ourselves
     * anchored to the end of the visible text — always visible regardless of where
     * the cursor currently sits.
     */
    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void sbe$renderCalculatorHintAlways(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {

        if (!isTrackedSearchBar()) return;

        suggestion = sbe$pendingSuggestion;
        sbe$pendingSuggestion = null;

        if (suggestion == null || suggestion.isEmpty()) return;
        if (font == null) return;

        String displayed = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        int hintX = textX + font.width(displayed);

        gfx.drawString(font, suggestion, hintX, textY, dimColor(textColor), textShadow);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

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