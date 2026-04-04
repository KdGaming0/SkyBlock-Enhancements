package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

/** Sprite definitions for chat tab buttons. Resource-pack retexturable via the GUI sprite atlas. */
public final class ChatTabSprites {

    /** Inactive tab */
    public static final WidgetSprites INACTIVE =
            new WidgetSprites(
                    Identifier.fromNamespaceAndPath(MOD_ID, "chat/button"),
                    Identifier.fromNamespaceAndPath(MOD_ID, "chat/button_disabled"),
                    Identifier.fromNamespaceAndPath(MOD_ID, "chat/button_highlighted"));

    /** Active/selected tab */
    public static final WidgetSprites ACTIVE =
            new WidgetSprites(
                    Identifier.fromNamespaceAndPath(MOD_ID, "chat/button_toggled"),
                    Identifier.fromNamespaceAndPath(MOD_ID, "chat/button_disabled"),
                    Identifier.fromNamespaceAndPath(MOD_ID, "chat/button_toggled_highlightedpng"));

    private ChatTabSprites() {}
}