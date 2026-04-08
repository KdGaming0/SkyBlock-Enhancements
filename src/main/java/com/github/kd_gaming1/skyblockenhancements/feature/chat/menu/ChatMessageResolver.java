package com.github.kd_gaming1.skyblockenhancements.feature.chat.menu;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

/**
 * Resolves which {@link GuiMessage} the cursor is hovering over and converts {@link Component}
 * content to plain or {@code &}-formatted strings.
 *
 * <p>Resolution uses the line-to-message mapping maintained by {@link SBEChatAccess} for O(1)
 * lookup, avoiding the old fragile {@code addedTime}-matching approach where multiple messages
 * sharing the same tick could cause incorrect resolution.
 */
public final class ChatMessageResolver {

    private ChatMessageResolver() {}

    /**
     * Returns the {@link GuiMessage} under the given screen coordinates, or {@code null} if the
     * cursor is not over any chat message.
     */
    public static @Nullable GuiMessage resolve(double screenX, double screenY) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        SBEChatAccess access = (SBEChatAccess) chat;

        List<GuiMessage.Line> trimmed = access.sbe$getTrimmedMessages();
        if (trimmed.isEmpty()) return null;

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        double scale = mc.options.chatScale().get();
        double localY = (screenY - (double) screenHeight + 40.0) / scale;

        double lineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int) (9.0 * (lineSpacing + 1.0));
        if (entryHeight <= 0) return null;

        int lineIndex = (int) Math.floor(-localY / entryHeight);
        if (lineIndex < 0 || lineIndex >= chat.getLinesPerPage()) return null;

        int trimmedIndex = lineIndex + access.sbe$getChatScrollbarPos();
        if (trimmedIndex < 0 || trimmedIndex >= trimmed.size()) return null;

        GuiMessage.Line line = trimmed.get(trimmedIndex);

        GuiMessage parent = access.sbe$getParentMessage(line);
        if (parent != null) {
            return parent;
        }

        // Fallback: if the mapping is missing (shouldn't happen normally), use addedTime.
        // This preserves backwards compatibility during edge cases like mid-refresh state.
        int targetTime = line.addedTime();
        for (GuiMessage msg : access.sbe$getAllMessages()) {
            if (msg.addedTime() == targetTime) {
                return msg;
            }
        }
        return null;
    }

    /**
     * Plain text with all formatting and compact suffixes stripped.
     */
    public static String toRawText(Component content) {
        String plain = ChatFormatting.stripFormatting(content.getString());
        return ChatTextHelper.stripCompactSuffix(plain != null ? plain : "");
    }

    /**
     * The message body only — everything after the first {@code ": "} separator — with formatting
     * and compact suffixes stripped. Returns the full plain text if no sender prefix is found.
     */
    public static String toMessageBody(Component content) {
        String plain = toRawText(content);
        int sep = plain.indexOf(": ");
        return sep >= 0 ? plain.substring(sep + 2) : plain;
    }

    /**
     * Text with {@code &} colour/formatting codes preserved; compact suffixes stripped.
     */
    public static String toFormattedText(Component content) {
        StringBuilder sb = new StringBuilder();
        Style[] previous = {Style.EMPTY};

        content.getVisualOrderText().accept((index, style, codePoint) -> {
            if (!style.equals(previous[0])) {
                appendStylePrefix(style, sb);
                previous[0] = style;
            }
            sb.appendCodePoint(codePoint);
            return true;
        });

        return ChatTextHelper.stripCompactSuffix(sb.toString());
    }

    private static void appendStylePrefix(Style style, StringBuilder sb) {
        sb.append("&r");

        TextColor color = style.getColor();
        if (color != null) {
            ChatFormatting fmt = chatFormattingFromColor(color);
            if (fmt != null) {
                sb.append('&').append(fmt.getChar());
            }
        }
        if (style.isBold()) sb.append("&l");
        if (style.isItalic()) sb.append("&o");
        if (style.isUnderlined()) sb.append("&n");
        if (style.isStrikethrough()) sb.append("&m");
        if (style.isObfuscated()) sb.append("&k");
    }

    private static @Nullable ChatFormatting chatFormattingFromColor(TextColor color) {
        int value = color.getValue();
        for (ChatFormatting fmt : ChatFormatting.values()) {
            if (fmt.isColor() && fmt.getColor() != null && fmt.getColor() == value) {
                return fmt;
            }
        }
        return null;
    }
}