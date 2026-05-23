package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.search;

import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SearchAutocomplete;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SearchCalculator;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SearchSuggestionState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom renderer and key handler for the RRV search bar.
 *
 * <h3>Calculator hint</h3>
 * Vanilla {@code EditBox} only draws its suggestion when the cursor is at the very
 * end of the value. This mixin bypasses that guard by snapshotting and clearing the
 * suggestion at HEAD, then restoring and re-drawing it at TAIL anchored to the end of
 * the visible text.
 *
 * <h3>Autocomplete ghost</h3>
 * When an autocomplete completion is active (and no calculator result is present),
 * the completion is drawn in dim gray at the appropriate position. Vanilla then draws
 * the actual typed text in white on top, so the prefix overlaps perfectly and only
 * the suffix peeks through as a faint hint. For acronym-style whole-query replacements,
 * the full completion is drawn appended to the current text.
 *
 * <h3>Key acceptance</h3>
 * {@code Tab} or {@code Right Arrow} (when cursor is at end) accepts the ghost
 * completion, replacing the last word (or the entire query for acronym matches).
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
    @Shadow public abstract int getCursorPosition();
    @Shadow public abstract void setValue(String text);

    // ── Calculator hint state ───────────────────────────────────────────────────

    @Unique private transient String sbe$pendingSuggestion;
    @Unique private transient boolean sbe$didSuppress;

    // ── Render: HEAD ────────────────────────────────────────────────────────────

    /**
     * At HEAD of renderWidget:
     * <ol>
     *   <li>Suppress vanilla suggestion rendering <em>only when the cursor is at the
     *       end</em> — that's the only case where vanilla would actually draw it.</li>
     *   <li>When the cursor is elsewhere vanilla already skips the suggestion, so
     *       clearing the field is unnecessary and can only cause stale-state bugs.</li>
     * </ol>
     */
    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void sbe$renderAtHead(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {

        if (!isTrackedSearchBar()) return;

        if (getCursorPosition() == value.length()
                && suggestion != null && !suggestion.isEmpty()) {
            sbe$pendingSuggestion = suggestion;
            sbe$didSuppress = true;
            suggestion = null;
        }
    }

    // ── Render: TAIL ────────────────────────────────────────────────────────────

    /**
     * At TAIL of renderWidget: restore the suggestion field and draw the calculator
     * hint anchored to the end of the visible text.
     */
    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void sbe$renderAtTail(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {

        if (!isTrackedSearchBar()) return;

        if (sbe$didSuppress) {
            sbe$didSuppress = false;
            suggestion = sbe$pendingSuggestion;
            sbe$pendingSuggestion = null;
        }

        if (font == null) return;

        // Priority 1: calculator hint
        // Use the suggestion field when available; fall back to a live evaluation so
        // the hint is never lost if the field was cleared unexpectedly.
        String calcHint = suggestion;
        if ((calcHint == null || calcHint.isEmpty()) && isTrackedSearchBar()) {
            calcHint = SearchCalculator.tryEvaluate(value);
        }

        if (calcHint != null && !calcHint.isEmpty()) {
            String displayed = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
            int hintX = textX + font.width(displayed);
            gfx.drawString(font, calcHint, hintX, textY, dimColor(textColor), textShadow);
            return;
        }

        // Priority 2: autocomplete ghost
        String completion = SearchSuggestionState.getCompletion();
        if (completion == null) return;

        if (SearchSuggestionState.isReplaceWholeQuery()) {
            sbe$drawFullAutocompleteGhost(gfx, completion);
        } else {
            sbe$drawAutocompleteGhost(gfx, completion);
        }
    }

    // ── Key handling ────────────────────────────────────────────────────────────

    /**
     * {@code Tab} or {@code Right Arrow} (at end-of-text) accepts the current
     * autocomplete completion, replacing the last word with the full completion.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$acceptAutocomplete(
            KeyEvent event, CallbackInfoReturnable<Boolean> cir) {

        if (!isTrackedSearchBar()) return;

        int keyCode = event.key();
        if (keyCode == 258) { // Tab
            sbe$tryAccept(cir);
        } else if (keyCode == 262) { // Right Arrow
            if (getCursorPosition() == value.length()) {
                sbe$tryAccept(cir);
            }
        }
    }

    @Unique
    private void sbe$tryAccept(CallbackInfoReturnable<Boolean> cir) {
        String completion = SearchSuggestionState.getCompletion();
        if (completion == null) return;

        if (SearchSuggestionState.isReplaceWholeQuery()) {
            setValue(completion);
            SearchSuggestionState.clear();
            cir.setReturnValue(true);
            return;
        }

        String lastWord = SearchAutocomplete.extractLastWord(value);
        if (lastWord.isEmpty() || !completion.startsWith(lastWord)) return;

        int lastWordStart = value.lastIndexOf(lastWord);
        if (lastWordStart < 0) return;

        String newValue = value.substring(0, lastWordStart) + completion;
        setValue(newValue);
        SearchSuggestionState.clear();
        cir.setReturnValue(true);
    }

    // ── Ghost drawing ───────────────────────────────────────────────────────────

    /**
     * Draws the full completion word in dim gray aligned with the start of the last
     * typed word. Vanilla's white text draw (coming after this HEAD injection) will
     * cover the overlapping prefix, leaving only the suffix visible as a faint hint.
     */
    @Unique
    private void sbe$drawAutocompleteGhost(GuiGraphics gfx, String completion) {
        if (font == null) return;

        String lastWord = SearchAutocomplete.extractLastWord(value);
        if (lastWord.isEmpty() || !completion.startsWith(lastWord)) return;

        String suffix = completion.substring(lastWord.length());
        if (suffix.isEmpty()) return;

        String displayed = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        int ghostX = textX + font.width(displayed);
        gfx.drawString(font, suffix, ghostX, textY, 0xFF888888, false);
    }

    /**
     * Draws the full replacement text at the end of the current input.
     * Used for acronym matches where the completion replaces the entire query.
     */
    @Unique
    private void sbe$drawFullAutocompleteGhost(GuiGraphics gfx, String completion) {
        if (font == null) return;

        String displayed = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        int ghostX = textX + font.width(displayed);
        gfx.drawString(font, completion, ghostX, textY, 0xFF888888, false);
    }

    // ── Misc helpers ────────────────────────────────────────────────────────────

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
