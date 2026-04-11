package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the active category filter state for the RRV item list overlay. Two independent
 * sources can set a filter:
 *
 * <ol>
 *   <li><b>Button bar</b> — toggling a category icon button. Takes priority.</li>
 *   <li><b>Search prefix</b> — typing {@code %CATEGORY} or {@code %CATEGORY/SUBCATEGORY}
 *       in the search bar. Only applies when no button is active.</li>
 * </ol>
 *
 * <p>All reads and writes occur on the main client thread (UI interactions), so no
 * synchronization is required.
 *
 * <p><b>Sub-category normalisation:</b> {@link #setSearchFilter} stores the sub-category
 * string uppercased. This means {@link SkyblockCategoryFilter#matches} never needs to call
 * {@code toUpperCase(Locale.ROOT)} on a per-item basis inside the filter loop, avoiding
 * repeated string allocations when thousands of items are filtered on every query change.
 */
public final class SkyblockCategoryState {

    // ── Button-driven state ─────────────────────────────────────────────────────

    /** Category selected via button toggle. Takes priority over search-driven state. */
    @Nullable private static SkyblockItemCategory buttonCategory = null;

    // ── Search-driven state ─────────────────────────────────────────────────────

    /** Category parsed from the {@code %CATEGORY} search prefix. */
    @Nullable private static SkyblockItemCategory searchCategory = null;

    /**
     * Sub-category parsed from {@code %CATEGORY/SUBCATEGORY} (e.g. {@code "COMBAT"}).
     * Always stored uppercased — normalised once in {@link #setSearchFilter}.
     */
    @Nullable private static String searchSubCategory = null;

    private SkyblockCategoryState() {}

    // ── Effective state (buttons take priority) ─────────────────────────────────

    /**
     * Returns the effective active category, preferring the button selection over the
     * search-prefix selection.
     */
    @Nullable
    public static SkyblockItemCategory getActiveCategory() {
        return buttonCategory != null ? buttonCategory : searchCategory;
    }

    /**
     * Returns the active sub-category, if any. Only applies when the search prefix is
     * active (button selections don't have sub-categories).
     *
     * <p>The returned string is guaranteed to be uppercase (normalised in
     * {@link #setSearchFilter}), so callers do not need to call {@code toUpperCase}.
     */
    @Nullable
    public static String getActiveSubCategory() {
        // Sub-category only applies when no button overrides
        if (buttonCategory != null) return null;
        return searchSubCategory;
    }

    /**
     * Returns {@code true} if the active filter was set via a button toggle (not search text).
     */
    public static boolean isButtonDriven() {
        return buttonCategory != null;
    }

    // ── Button controls ─────────────────────────────────────────────────────────

    /**
     * Toggles the button filter: clears it when {@code category} is already active, sets it
     * otherwise. Does not affect the search-driven state.
     */
    public static void toggle(SkyblockItemCategory category) {
        buttonCategory = (category == buttonCategory) ? null : category;
    }

    // ── Search prefix controls ──────────────────────────────────────────────────

    /**
     * Sets the search-driven category and optional sub-category. Called by the mixin
     * when parsing the search query text.
     *
     * @param category    the parsed category, or {@code null} to clear
     * @param subCategory the parsed sub-category (e.g. {@code "combat"}), or {@code null}
     */
    public static void setSearchFilter(@Nullable SkyblockItemCategory category,
                                       @Nullable String subCategory) {
        searchCategory = category;
        searchSubCategory = (subCategory != null && !subCategory.isEmpty())
                ? subCategory.toUpperCase(Locale.ROOT)
                : null;
    }

    /** Clears the search-driven filter. Called when the search text no longer has a prefix. */
    public static void clearSearchFilter() {
        searchCategory    = null;
        searchSubCategory = null;
    }

    // ── Full reset ──────────────────────────────────────────────────────────────

    /** Clears all filter state (both button and search). */
    public static void clear() {
        buttonCategory    = null;
        searchCategory    = null;
        searchSubCategory = null;
    }
}