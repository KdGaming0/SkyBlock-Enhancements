package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SearchCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Displays a calculator result as ghost text in the RRV search bar.
 *
 * <p>When the user types a recognised math expression (e.g. {@code 10+10} or
 * {@code 1.5m*2}), the evaluated result is shown immediately after the cursor as a
 * faint suggestion (e.g. {@code 10+10 = 20}). Non-math queries are unaffected; the
 * suggestion is simply cleared.
 *
 * <p>Injection point: the TAIL of {@code ItemViewOverlay.updateQuery}, which is
 * called on every keystroke via the searchbar's change-responder.
 */
@Mixin(ItemViewOverlay.class)
public abstract class RrvSearchCalculatorMixin {

    @Shadow(remap = false)
    private SearchBar searchbar;

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void sbe$showCalculatorHint(String newQuery, CallbackInfo ci) {
        if (!RrvCompat.isActive() || searchbar == null) return;

        // null clears any existing suggestion when the query is not a math expression
        searchbar.setSuggestion(SearchCalculator.tryEvaluate(newQuery));
    }
}