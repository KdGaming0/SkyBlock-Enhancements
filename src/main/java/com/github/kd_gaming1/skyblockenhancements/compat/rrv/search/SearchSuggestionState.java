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
    private static boolean replaceWholeQuery;

    private SearchSuggestionState() {}

    public static void set(@Nullable String query, @Nullable String completion, boolean replaceWholeQuery) {
        currentQuery = query;
        currentCompletion = completion;
        SearchSuggestionState.replaceWholeQuery = replaceWholeQuery;
    }

    public static void set(@Nullable String query, @Nullable String completion) {
        set(query, completion, false);
    }

    public static @Nullable String getCompletion() {
        return currentCompletion;
    }

    public static @Nullable String getQuery() {
        return currentQuery;
    }

    public static boolean isReplaceWholeQuery() {
        return replaceWholeQuery;
    }

    public static void clear() {
        currentQuery = null;
        currentCompletion = null;
        replaceWholeQuery = false;
    }
}
