package com.github.kd_gaming1.skyblockenhancements.feature.chat.search;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Holds the active search query and determines whether a given chat message should be visible.
 *
 * <p>Search uses <b>multi-word AND matching</b>: the query is split on whitespace and every token
 * must appear (case-insensitive) in the message's plain text. This lets users type
 * {@code "party invite"} to find messages containing both words in any order — far more useful
 * than naive substring matching for filtering a busy chat log.
 */
public final class ChatSearchState {

    private static boolean active;
    private static String rawQuery = "";
    private static String[] tokens = {};

    private ChatSearchState() {}

    /** Whether the search bar is currently open and has a non-empty query. */
    public static boolean isFiltering() {
        return active && tokens.length > 0;
    }

    /** Whether the search bar UI is open (even if the query is empty). */
    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            rawQuery = "";
            tokens = new String[0];
        }
    }

    public static String getQuery() {
        return rawQuery;
    }

    /**
     * Updates the query and recomputes the token array. Tokens are lowercased once here so that
     * the per-message check in {@link #matches(Component)} avoids repeated lowercasing of the
     * query string.
     */
    public static void setQuery(String query) {
        rawQuery = query;
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            tokens = new String[0];
        } else {
            tokens = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
        }
    }

    /**
     * Returns {@code true} if the message should be displayed under the current search filter.
     * When no filter is active every message passes.
     */
    public static boolean matches(Component content) {
        if (!isFiltering()) return true;
        String plain = toSearchable(content);
        for (String token : tokens) {
            if (!plain.contains(token)) return false;
        }
        return true;
    }

    /** Convenience overload for {@link GuiMessage}. */
    public static boolean matches(GuiMessage message) {
        return matches(message.content());
    }

    /**
     * Counts how many messages in the given list match the current search filter.
     */
    public static int countMatching(List<GuiMessage> allMessages) {
        if (!isFiltering()) return allMessages.size();

        int count = 0;
        for (GuiMessage msg : allMessages) {
            if (matches(msg)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Converts a chat {@link Component} to a lowercase, formatting-stripped, compact-suffix-free
     * string suitable for searching.
     */
    private static String toSearchable(Component content) {
        String plain = ChatFormatting.stripFormatting(content.getString());
        return ChatTextHelper.stripCompactSuffix(plain != null ? plain : "").toLowerCase(Locale.ROOT);
    }
}