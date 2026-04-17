package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Owns the currently active chat tab and decides per-message visibility.
 *
 * <p>Separator lines are a special case: they are shown under a tab only when at least one of
 * their immediate non-separator neighbours belongs to that tab. This keeps context banners
 * like {@code ------ Party ------} visible in the Party tab while hiding orphaned separators
 * from unrelated channels.
 */
public final class ChatTabController {

    private ChatTab activeTab = ChatTab.ALL;

    public ChatTab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(ChatTab tab) {
        this.activeTab = tab;
    }

    /**
     * @param indexInHistory position of the message in {@code allMessages}, or {@code -1} if
     *                       the message has not yet been added to history.
     */
    public boolean shouldShow(Component message, List<GuiMessage> allMessages, int indexInHistory) {
        if (!SkyblockEnhancementsConfig.enableChatTabs) return true;
        if (!HypixelLocationState.isOnHypixel()) return true;
        if (activeTab == ChatTab.ALL) return true;

        String plain = plainText(message);

        if (isSeparator(plain)) {
            return separatorBelongsToActiveTab(allMessages, indexInHistory);
        }
        return activeTab.matches(plain);
    }

    // ---------------------------------------------------------------------
    // Separator handling
    // ---------------------------------------------------------------------

    private boolean separatorBelongsToActiveTab(List<GuiMessage> allMessages, int index) {
        if (index < 0) {
            // Not yet in history: the only neighbour we can inspect is the most-recent message,
            // which lives at index 0 once addMessageToQueue finishes. Check that first.
            if (allMessages.isEmpty()) return true;
            return matchesActiveTab(nearestNonSeparator(allMessages, -1, +1));
        }
        return matchesActiveTab(nearestNonSeparator(allMessages, index, -1))
                || matchesActiveTab(nearestNonSeparator(allMessages, index, +1));
    }

    private Component nearestNonSeparator(List<GuiMessage> allMessages, int startIndex, int step) {
        for (int i = startIndex + step; i >= 0 && i < allMessages.size(); i += step) {
            Component content = allMessages.get(i).content();
            if (!isSeparator(plainText(content))) return content;
        }
        return null;
    }

    private boolean matchesActiveTab(Component message) {
        return message != null && activeTab.matches(plainText(message));
    }

    // ---------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------

    private static String plainText(Component message) {
        String plain = ChatFormatting.stripFormatting(message.getString());
        return ChatTextHelper.stripCompactSuffix(plain).trim();
    }

    private static boolean isSeparator(String plain) {
        return ChatTextHelper.isFullSeparator(plain) || ChatTextHelper.isCenteredSeparator(plain);
    }
}