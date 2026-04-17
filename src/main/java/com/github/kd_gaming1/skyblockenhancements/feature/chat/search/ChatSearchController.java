package com.github.kd_gaming1.skyblockenhancements.feature.chat.search;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Owns the chat search query and decides whether individual messages pass the filter.
 *
 * <p>Matching is <b>multi-word AND</b>: the query is split on whitespace and every token must
 * appear (case-insensitive) in the plain-text form of the message. Tokens are lowercased once
 * at query-set time so the per-message path does no string allocation beyond the searchable
 * form of the message itself.
 */
public final class ChatSearchController {

    private static final String[] NO_TOKENS = new String[0];

    private boolean active;
    private String rawQuery = "";
    private String[] tokens = NO_TOKENS;

    public boolean isActive() {
        return active;
    }

    public boolean isFiltering() {
        return active && tokens.length > 0;
    }

    public String getQuery() {
        return rawQuery;
    }

    public void setActive(boolean value) {
        this.active = value;
        if (!value) {
            this.rawQuery = "";
            this.tokens = NO_TOKENS;
        }
    }

    public void setQuery(String query) {
        this.rawQuery = query;
        String trimmed = query.trim();
        this.tokens = trimmed.isEmpty()
                ? NO_TOKENS
                : trimmed.toLowerCase(Locale.ROOT).split("\\s+");
    }

    public boolean matches(Component content) {
        if (!isFiltering()) return true;
        String plain = toSearchable(content);
        for (String token : tokens) {
            if (!plain.contains(token)) return false;
        }
        return true;
    }

    public boolean matches(GuiMessage message) {
        return matches(message.content());
    }

    public int countMatching(List<GuiMessage> messages) {
        if (!isFiltering()) return messages.size();
        int count = 0;
        for (GuiMessage msg : messages) {
            if (matches(msg)) count++;
        }
        return count;
    }

    private static String toSearchable(Component content) {
        String plain = ChatFormatting.stripFormatting(content.getString());
        return ChatTextHelper.stripCompactSuffix(plain).toLowerCase(Locale.ROOT);
    }
}