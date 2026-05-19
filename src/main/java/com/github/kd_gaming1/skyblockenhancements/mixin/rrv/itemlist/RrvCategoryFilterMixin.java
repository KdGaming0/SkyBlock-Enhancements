package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.itemlist;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.config.options.OverlayDisplay;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay.ScreenContext;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.CategoryIconButton;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryButtons;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryState;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds category filtering and toggle buttons to the RRV item list overlay.
 *
 * <p>When the advanced search index is active, category filtering is applied at the
 * index level (inside {@link com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SkyblockSearchFilter}),
 * so the post-search {@code removeIf} is skipped to avoid redundant work. The button bar
 * and prefix parsing still function exactly as before.
 */
@Mixin(ItemViewOverlay.class)
public abstract class RrvCategoryFilterMixin {

    @Shadow(remap = false)
    private SearchBar searchbar;

    @Unique
    private SkyblockItemCategory sbe$lastCategory = null;

    @Unique
    private String sbe$lastSubCategory = null;

    @Unique
    private String sbe$lastQuery = null;

    @Unique
    private final List<CategoryIconButton> sbe$categoryButtons = new ArrayList<>();

    // ── Search prefix parsing + stripping ────────────────────────────────────────

    @ModifyVariable(method = "updateQuery", at = @At("HEAD"), argsOnly = true, remap = false, name = "newQuery")
    private String sbe$parseAndStripCategoryPrefix(String newQuery) {
        if (!RrvCompat.isActive()) return newQuery;

        if (newQuery == null || !newQuery.startsWith("%")) {
            SkyblockCategoryState.clearSearchFilter();
            return newQuery;
        }

        int spaceIdx = newQuery.indexOf(' ');
        String prefix    = spaceIdx >= 0 ? newQuery.substring(1, spaceIdx) : newQuery.substring(1);
        String remainder = spaceIdx >= 0 ? newQuery.substring(spaceIdx + 1) : "";

        String categoryName;
        String subCategory = null;
        int slashIdx = prefix.indexOf('/');
        if (slashIdx >= 0) {
            categoryName = prefix.substring(0, slashIdx);
            subCategory  = prefix.substring(slashIdx + 1);
            if (subCategory.isEmpty()) subCategory = null;
        } else {
            categoryName = prefix;
        }

        SkyblockItemCategory parsed = SkyblockItemCategory.fromName(categoryName);
        if (parsed != null) {
            SkyblockCategoryState.setSearchFilter(parsed, subCategory);
            return remainder;
        }

        SkyblockCategoryState.clearSearchFilter();
        return newQuery;
    }

    // ── Category filter application ─────────────────────────────────────────────

    /**
     * Applies the active category filter to {@code availableItems()}.
     *
     * <p>The search index (via {@code ItemFiltersMixin}) handles text-query filtering,
     * but it is only invoked when {@code defaultFilter} runs. When a category button is
     * clicked with an empty query, RRV's {@code updateQuery} may skip calling
     * {@code defaultFilter}, leaving {@code availableItems()} as the full unfiltered list.
     * This method always applies the category via {@code removeIf} so the filter is
     * never accidentally bypassed.
     *
     * <p>For search-driven categories (via {@code %CATEGORY} prefix), the search index
     * already narrows the list, so the {@code removeIf} here is typically a no-op.
     * Sub-categories (e.g. {@code %PET/COMBAT}) always require the per-item
     * {@code SkyblockCategoryFilter.matches} check.
     */
    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void sbe$applyCategoryFilter(String newQuery, CallbackInfo ci) {
        if (!RrvCompat.isActive()) return;

        sbe$updateCategoryButtonVisibility();

        SkyblockItemCategory target = SkyblockCategoryState.getActiveCategory();
        if (target == null) return;

        @Nullable String subCategory = SkyblockCategoryState.getActiveSubCategory();

        AbstractRrvItemListOverlay self = (AbstractRrvItemListOverlay) (Object) this;

        boolean filterChanged = sbe$lastCategory != target
                || !Objects.equals(sbe$lastSubCategory, subCategory)
                || !Objects.equals(sbe$lastQuery, newQuery);

        if (filterChanged) {
            ((AbstractRrvItemListOverlayAccessor) self).sbe$setStartIndex(0);

            sbe$lastCategory = target;
            sbe$lastSubCategory = subCategory;
            sbe$lastQuery = newQuery;
        }

        if (subCategory != null && !subCategory.isEmpty()) {
            self.availableItems().removeIf(
                    stack -> !SkyblockCategoryFilter.matches(stack, target, subCategory));
        } else {
            Set<ItemStack> allowed = com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache.getCategoryItems(target);
            self.availableItems().removeIf(stack -> !allowed.contains(stack));
        }

        self.updateSlots();
    }

    // ── Button injection ────────────────────────────────────────────────────────

    /** Adds category toggle buttons above the search bar after RRV places its own widgets. */
    @Inject(method = "placeWidgets", at = @At("TAIL"), remap = false)
    private void sbe$addCategoryButtons(ScreenContext ctx, CallbackInfo ci) {
        if (!RrvCompat.isActive() || searchbar == null) return;

        sbe$categoryButtons.clear();

        for (CategoryIconButton btn :
                SkyblockCategoryButtons.create(
                        searchbar.getX(), searchbar.getY(), searchbar.getWidth())) {
            sbe$categoryButtons.add(btn);
            ctx.addRenderable(btn);
        }

        sbe$updateCategoryButtonVisibility();
    }

    // ── Button visibility (hook into RRV's "hide when not searching" behaviour) ─

    @Inject(method = "setEnabled", at = @At("TAIL"), remap = false)
    private void sbe$onEnabledChanged(boolean enabled, CallbackInfo ci) {
        sbe$updateCategoryButtonVisibility();
    }

    @Unique
    private void sbe$updateCategoryButtonVisibility() {
        if (!RrvCompat.isActive() || sbe$categoryButtons.isEmpty()) return;

        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        OverlayDisplay rrvMode = Configs.CLIENT_SETTINGS.isShowOverlays();

        boolean visible;
        if (SkyblockEnhancementsConfig.hideCategoryButtons) {
            visible = false;
        } else if (rrvMode == OverlayDisplay.WHEN_SEARCHING) {
            visible = !SkyblockEnhancementsConfig.hideCategoryButtonsWhenNotSearching
                    || self.isSearching();
        } else {
            visible = rrvMode != OverlayDisplay.DISABLED;
        }

        for (CategoryIconButton btn : sbe$categoryButtons) {
            btn.visible = visible;
        }
    }
}
