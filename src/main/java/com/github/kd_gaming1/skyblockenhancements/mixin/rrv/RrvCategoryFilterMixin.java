package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay.ScreenContext;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.*;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds category filtering and toggle buttons to the RRV item list overlay.
 *
 * <p>Two filter sources are supported, with buttons taking priority:
 * <ol>
 *   <li><b>Buttons</b> — icon buttons above the search bar, toggling a category.</li>
 *   <li><b>Search prefix</b> — typing {@code %CATEGORY} or {@code %CATEGORY/SUBCATEGORY}
 *       at the start of the search query.</li>
 * </ol>
 *
 * <p>The prefix is stripped from the query before RRV applies its own text filter, so
 * {@code %PET/COMBAT spider} would filter to combat pets whose name contains "spider".
 *
 * <p>Three injection points:
 * <ol>
 *   <li>{@code updateQuery} HEAD — parses and strips the {@code %} prefix from the query.</li>
 *   <li>{@code updateQuery} TAIL — applies the combined category+sub-category filter.</li>
 *   <li>{@code placeWidgets} TAIL — adds the toggle buttons above the search bar.</li>
 * </ol>
 */
@Mixin(ItemViewOverlay.class)
public abstract class RrvCategoryFilterMixin {

    @Shadow(remap = false)
    private SearchBar searchbar;

    // ── Search prefix parsing + stripping ────────────────────────────────────────

    /**
     * Intercepts the query string before RRV processes it. Parses any leading {@code %}
     * prefix into a category (and optional sub-category) in {@link SkyblockCategoryState},
     * then strips the prefix so RRV only sees the remaining text filter.
     */
    @ModifyVariable(method = "updateQuery", at = @At("HEAD"), argsOnly = true, remap = false, name = "newQuery")
    private String sbe$parseAndStripCategoryPrefix(String newQuery) {
        if (!RrvCompat.isActive()) return newQuery;

        if (newQuery == null || !newQuery.startsWith("%")) {
            SkyblockCategoryState.clearSearchFilter();
            return newQuery;
        }

        // Find where the prefix ends (first space, or end of string)
        int spaceIdx = newQuery.indexOf(' ');
        String prefix = spaceIdx >= 0 ? newQuery.substring(1, spaceIdx) : newQuery.substring(1);
        String remainder = spaceIdx >= 0 ? newQuery.substring(spaceIdx + 1) : "";

        // Parse category and optional sub-category from "CATEGORY" or "CATEGORY/SUBCATEGORY"
        String categoryName;
        String subCategory = null;
        int slashIdx = prefix.indexOf('/');
        if (slashIdx >= 0) {
            categoryName = prefix.substring(0, slashIdx);
            subCategory = prefix.substring(slashIdx + 1);
            if (subCategory.isEmpty()) subCategory = null;
        } else {
            categoryName = prefix;
        }

        SkyblockItemCategory parsed = SkyblockItemCategory.fromName(categoryName);
        if (parsed != null) {
            SkyblockCategoryState.setSearchFilter(parsed, subCategory);
            // Return only the text after the prefix for RRV's normal filter
            return remainder;
        }

        // Invalid prefix — treat as normal text, clear any stale search filter
        SkyblockCategoryState.clearSearchFilter();
        return newQuery;
    }

    // ── Category filter application ─────────────────────────────────────────────

    /**
     * Narrows the item list to the active category (and sub-category) after RRV has
     * finished applying its own query filters. Buttons take priority over search prefixes.
     */
    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void sbe$applyCategoryFilter(String newQuery, CallbackInfo ci) {
        if (!RrvCompat.isActive()) return;

        SkyblockItemCategory target = SkyblockCategoryState.getActiveCategory();
        if (target == null) return;

        @Nullable String subCategory = SkyblockCategoryState.getActiveSubCategory();

        AbstractRrvItemListOverlay self = (AbstractRrvItemListOverlay) (Object) this;

        if (subCategory != null && !subCategory.isEmpty()) {
            self.availableItems().removeIf(
                    stack -> !SkyblockCategoryFilter.matches(stack, target, subCategory));
        } else {
            self.availableItems().removeIf(
                    stack -> !SkyblockCategoryFilter.matches(stack, target));
        }
        self.updateSlots();
    }

    // ── Button injection ────────────────────────────────────────────────────────

    /** Adds category toggle buttons above the search bar after RRV places its own widgets. */
    @Inject(method = "placeWidgets", at = @At("TAIL"), remap = false)
    private void sbe$addCategoryButtons(ScreenContext ctx, CallbackInfo ci) {
        if (!RrvCompat.isActive() || searchbar == null) return;

        for (CategoryIconButton btn :
                SkyblockCategoryButtons.create(
                        searchbar.getX(), searchbar.getY(), searchbar.getWidth())) {
            ctx.addRenderable(btn);
        }
    }
}