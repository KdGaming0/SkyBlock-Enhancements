package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Holds the currently active {@link ChatTab} and filters messages accordingly. */
public final class ChatTabState {

    private static ChatTab activeTab = ChatTab.ALL;
    private static ChatTab lastContentTab = null;

    private ChatTabState() {
    }

    public static ChatTab getActiveTab() {
        return activeTab;
    }

    public static void setActiveTab(ChatTab tab) {
        activeTab = tab;
        lastContentTab = null;
    }

    /**
     * Returns {@code true} if the given message should be displayed under the current tab. Always
     * returns {@code true} when tabs are disabled or the player is not on Hypixel.
     */
    public static boolean shouldShow(Component message) {
        if (!SkyblockEnhancementsConfig.enableChatTabs) return true;
        if (!HypixelLocationState.isOnHypixel()) return true;
        if (activeTab == ChatTab.ALL) return true;

        String plain = ChatFormatting.stripFormatting(message.getString());
        plain = ChatTextHelper.stripCompactSuffix(plain).trim();

        // Separators belong to whichever tab the surrounding content belongs to
        if (ChatTextHelper.isFullSeparator(plain) || ChatTextHelper.isCenteredSeparator(plain)) {
            return lastContentTab == null || lastContentTab == activeTab;
        }

        boolean matches = activeTab.matches(plain);
        if (matches) lastContentTab = activeTab;
        else {
            // Find which tab owns this message
            for (ChatTab tab : ChatTab.values()) {
                if (tab != ChatTab.ALL && tab.matches(plain)) {
                    lastContentTab = tab;
                    break;
                }
            }
        }
        return matches;
    }

    public static void reset() {
        lastContentTab = null;
    }
}