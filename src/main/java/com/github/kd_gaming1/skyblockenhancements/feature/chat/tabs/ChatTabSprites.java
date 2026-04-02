package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

/** Sprite definitions for chat tab buttons. Resource-pack retexturable via the GUI sprite atlas. */
public final class ChatTabSprites {

    private static final String NS = "skyblock_enhancements";

    /** Inactive tab */
    public static final WidgetSprites INACTIVE =
            new WidgetSprites(
                    Identifier.fromNamespaceAndPath(NS, "chat/chat_tab"),
                    Identifier.fromNamespaceAndPath(NS, "chat/chat_tab_disabled"),
                    Identifier.fromNamespaceAndPath(NS, "chat/chat_tab_hovered"));

    /** Active/selected tab */
    public static final WidgetSprites ACTIVE =
            new WidgetSprites(
                    Identifier.fromNamespaceAndPath(NS, "chat/chat_tab_selected"),
                    Identifier.fromNamespaceAndPath(NS, "chat/chat_tab_selected_disabled"),
                    Identifier.fromNamespaceAndPath(NS, "chat/chat_tab_selected_hovered"));

    private ChatTabSprites() {}
}