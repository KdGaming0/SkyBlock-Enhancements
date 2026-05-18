package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.search;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryState;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SearchAutocomplete;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SearchCalculator;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SearchSuggestionState;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Evaluates the search query for both calculator results and autocomplete completions.
 *
 * <p>Calculator results are shown via {@link SearchBar#setSuggestion} as ghost text.
 * Autocomplete completions are stored in {@link SearchSuggestionState} and drawn behind
 * the typed text by {@link EditBoxCalculatorHintMixin} so only the suffix peeks through.
 *
 * <p>Priority:
 * <ol>
 *   <li>Math expression → calculator ghost text (e.g. {@code " = 20"}), completion cleared</li>
 *   <li>Search token prefix → best completion stored for render, calculator cleared</li>
 *   <li>Neither → both cleared</li>
 * </ol>
 */
@Mixin(ItemViewOverlay.class)
public abstract class RrvSearchCalculatorMixin {

    @Shadow(remap = false)
    private SearchBar searchbar;

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void sbe$showSearchHint(String newQuery, CallbackInfo ci) {
        if (!RrvCompat.isActive() || searchbar == null) return;

        // Priority 1: calculator result
        String calc = SearchCalculator.tryEvaluate(newQuery);
        if (calc != null) {
            searchbar.setSuggestion(calc);
            SearchSuggestionState.clear();
            return;
        }

        // Clear any stale calculator suggestion
        searchbar.setSuggestion(null);

        // Priority 2: autocomplete from the search index
        var index = FullStackListCache.getSearchIndex();
        SearchAutocomplete autocomplete = index != null ? index.getAutocomplete() : null;
        if (autocomplete != null) {
            SkyblockItemCategory activeCategory = SkyblockCategoryState.getActiveCategory();
            SearchAutocomplete.Suggestion suggestion = autocomplete.suggest(newQuery, activeCategory);
            if (suggestion != null && !suggestion.text().isEmpty()) {
                SearchSuggestionState.set(newQuery, suggestion.text(), suggestion.replaceWholeQuery());
                return;
            }
        }

        // Nothing to suggest
        SearchSuggestionState.clear();
    }
}
