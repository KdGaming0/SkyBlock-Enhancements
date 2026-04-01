package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Holds the currently active {@link ChatTab} and filters messages accordingly. */
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
     * Returns {@code true} if the given message should be displayed under the current tab. Always
     * returns {@code true} when tabs are disabled or the player is not on Hypixel.
     */
    public static boolean shouldShow(Component message) {
        if (!SkyblockEnhancementsConfig.enableChatTabs) return true;
        if (!HypixelLocationState.isOnHypixel()) return true;
        if (activeTab == ChatTab.ALL) return true;

        String plain = stripFormatting(message.getString()).trim();
        return activeTab.matches(plain);
    }

    /** Strips section-sign formatting codes from a string. */
    private static String stripFormatting(String s) {
        return ChatFormatting.stripFormatting(s);
    }
}