package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Determines which chat messages are visible under the active {@link ChatTab}.
 *
 * <p>Separator lines (full or centred dash lines) are shown only when at least one of their
 * immediate non-separator neighbours belongs to the active tab. This works correctly regardless
 * of the iteration order of the caller.
 */
public final class ChatTabState {

    private static ChatTab activeTab = ChatTab.ALL;

    private ChatTabState() {}

    public static ChatTab getActiveTab() {
        return activeTab;
    }

    public static void setActiveTab(ChatTab tab) {
        activeTab = tab;
    }

    /**
     * Returns {@code true} if {@code message} should be shown under the current tab.
     */
    public static boolean shouldShow(
            Component message, List<GuiMessage> allMessages, int indexInHistory) {
        if (!SkyblockEnhancementsConfig.enableChatTabs) return true;
        if (!HypixelLocationState.isOnHypixel()) return true;
        if (activeTab == ChatTab.ALL) return true;

        String plain = plainText(message);

        if (isSeparator(plain)) {
            return separatorBelongsToActiveTab(allMessages, indexInHistory);
        }

        return activeTab.matches(plain);
    }

    // Separator helpers

    /**
     * Determines whether a separator should be shown under the active tab by scanning its
     * nearest non-separator neighbours.
     */
    private static boolean separatorBelongsToActiveTab(
            List<GuiMessage> allMessages, int index) {
        if (index < 0) {
            if (allMessages.isEmpty()) {
                return true;
            }
            return matchesActiveTab(nearestContent(allMessages, -1, +1));
        }
        return matchesActiveTab(nearestContent(allMessages, index, -1))
                || matchesActiveTab(nearestContent(allMessages, index, +1));
    }

    /**
     * Walks from {@code startIndex} in direction {@code step} (+1 or -1) and returns the first
     * non-separator message content, or {@code null} if the boundary is reached first.
     */
    private static Component nearestContent(
            List<GuiMessage> allMessages, int startIndex, int step) {
        for (int i = startIndex + step; i >= 0 && i < allMessages.size(); i += step) {
            String plain = plainText(allMessages.get(i).content());
            if (!isSeparator(plain)) {
                return allMessages.get(i).content();
            }
        }
        return null;
    }

    private static boolean matchesActiveTab(Component message) {
        if (message == null) return false;
        return activeTab.matches(plainText(message));
    }

    // Text helpers
    private static String plainText(Component message) {
        String plain = ChatFormatting.stripFormatting(message.getString());
        return ChatTextHelper.stripCompactSuffix(plain).trim();
    }

    private static boolean isSeparator(String plain) {
        return ChatTextHelper.isFullSeparator(plain) || ChatTextHelper.isCenteredSeparator(plain);
    }
}