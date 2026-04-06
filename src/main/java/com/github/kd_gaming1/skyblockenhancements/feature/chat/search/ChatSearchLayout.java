package com.github.kd_gaming1.skyblockenhancements.feature.chat.search;

/**
 * Shared layout constants for the chat search bar. Used by both the search screen mixin and the
 * tab screen mixin to keep vertical positioning consistent.
 */
public final class ChatSearchLayout {

    public static final int SEARCH_BAR_HEIGHT = 10;

    private ChatSearchLayout() {}

    /**
     * Returns the extra vertical offset that elements above the chat input should apply when the
     * search bar is visible. Zero when the search bar is hidden.
     */
    public static int extraOffset() {
        return ChatSearchState.isActive() ? SEARCH_BAR_HEIGHT + 4 : 0;
    }


    /** The Y coordinate for the search bar itself. */
    public static int searchBarY(int screenHeight) {
        return screenHeight - 12 - SEARCH_BAR_HEIGHT - 4;
    }
}