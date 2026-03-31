package com.github.kd_gaming1.skyblockenhancements.feature.chat.heads;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts player names from Hypixel-format chat messages and resolves their skin textures.
 *
 * <p>Inspired by Chat Heads by dzwdz (LGPL-3.0). This is an original implementation.
 */
public final class ChatHeadResolver {

    /** Size in pixels of the rendered head (both width and height). */
    public static final int HEAD_SIZE = 8;

    /** Horizontal offset applied to message text to make room for the head. */
    public static final int HEAD_OFFSET = HEAD_SIZE + 2;

    /**
     * Matches common Hypixel chat formats:
     *
     * <ul>
     *   <li>{@code [VIP] PlayerName: message}
     *   <li>{@code PlayerName: message}
     *   <li>{@code Party > [MVP+] PlayerName: message}
     *   <li>{@code From [ADMIN] PlayerName: message}
     *   <li>{@code Guild > [VIP+] PlayerName: message}
     * </ul>
     *
     * Capture group 1 = player name (3–16 alphanumeric/underscore characters).
     */
    private static final Pattern NAME_PATTERN =
            Pattern.compile(
                    "(?:(?:Party|Guild|Co-op|Friend) > |From |To )?(?:\\[[\\w+]+] )?(\\w{3,16}): ");

    private ChatHeadResolver() {}

    /**
     * Attempts to extract a player name from a chat message's text content.
     *
     * @return the player name, or {@code null} if the message does not match a known format
     */
    @Nullable
    public static String extractPlayerName(Component message) {
        String plain = message.getString();
        Matcher matcher = NAME_PATTERN.matcher(plain);
        if (!matcher.find()) return null;
        return matcher.group(1);
    }

    /**
     * Resolves a player's skin from the current server's player list.
     *
     * @return the {@link PlayerSkin}, or {@code null} if the player is not in the tab list
     */
    @Nullable
    public static PlayerSkin resolveSkin(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;

        PlayerInfo info = mc.getConnection().getPlayerInfo(playerName);
        return info != null ? info.getSkin() : null;
    }
}