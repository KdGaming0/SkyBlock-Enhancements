package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the active {@link SkyblockItemCategory} filter for the RRV item list overlay.
 *
 * <p>All reads and writes occur on the main client thread (UI interactions), so no
 * synchronization is required.
 */
public final class SkyblockCategoryState {

    @Nullable private static SkyblockItemCategory activeCategory = null;

    private SkyblockCategoryState() {}

    @Nullable
    public static SkyblockItemCategory getActiveCategory() {
        return activeCategory;
    }

    /**
     * Toggles the filter: clears it when {@code category} is already active, sets it otherwise.
     */
    public static void toggle(SkyblockItemCategory category) {
        activeCategory = (category == activeCategory) ? null : category;
    }

    /** Clears the active filter. */
    public static void clear() {
        activeCategory = null;
    }
}