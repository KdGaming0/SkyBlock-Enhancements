package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import org.jetbrains.annotations.Nullable;

/**
 * Holds the current autocomplete completion so the render mixin can draw it
 * without recalculating on every frame.
 *
 * <p>All access is on the client render thread, so no synchronization is needed.
 */
public final class SearchSuggestionState {

    private static String currentQuery;
    private static String currentCompletion;

    private SearchSuggestionState() {}

    public static void set(@Nullable String query, @Nullable String completion) {
        currentQuery = query;
        currentCompletion = completion;
    }

    public static @Nullable String getCompletion() {
        return currentCompletion;
    }

    public static @Nullable String getQuery() {
        return currentQuery;
    }

    public static void clear() {
        currentQuery = null;
        currentCompletion = null;
    }
}
