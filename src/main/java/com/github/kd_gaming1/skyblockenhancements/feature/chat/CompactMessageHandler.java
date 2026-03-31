package com.github.kd_gaming1.skyblockenhancements.feature.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks duplicate chat messages and compacts them into a single line with an occurrence counter
 * (e.g. {@code (×3)}).
 *
 * <p>Adapted from compact-chat by Caoimhe Byrne (MIT License). See THIRD_PARTY_LICENSES.md.
 */
public final class CompactMessageHandler {

    private static final Style COUNT_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    /** Marker string appended to compacted messages so we can strip it for comparison. */
    private static final String COUNT_PREFIX = " (×";

    private final SBEChatAccess chatAccess;
    private final Map<String, Integer> occurrences = new HashMap<>();
    private @Nullable String previousMessage;

    public CompactMessageHandler(SBEChatAccess chatAccess) {
        this.chatAccess = chatAccess;
    }

    /**
     * Processes an incoming message. If it is a duplicate, removes the previous occurrence from the
     * message list and returns a copy with an {@code (×N)} suffix. Otherwise returns the original.
     */
    public Component process(Component message) {
        if (!SkyblockEnhancementsConfig.compactDuplicateMessages) return message;

        String raw = stripCountSuffix(message.getString());
        if (raw.isBlank()) return message;

        if (SkyblockEnhancementsConfig.onlyCompactConsecutive && !raw.equals(previousMessage)) {
            previousMessage = raw;
            occurrences.putIfAbsent(raw, 1);
            return message;
        }
        previousMessage = raw;

        int count = occurrences.merge(raw, 1, Integer::sum);
        if (count <= 1) return message;

        // Remove the previous duplicate from allMessages.
        Iterator<GuiMessage> it = chatAccess.sbe$getAllMessages().iterator();
        while (it.hasNext()) {
            GuiMessage existing = it.next();
            String existingRaw = stripCountSuffix(existing.content().getString());
            if (existingRaw.equals(raw)) {
                it.remove();
                chatAccess.sbe$refreshMessages();
                break;
            }
        }

        MutableComponent result = message.copy();
        result.append(Component.literal(COUNT_PREFIX + count + ")").setStyle(COUNT_STYLE));
        return result;
    }

    /** Clears all tracked state (called on chat clear). */
    public void clear() {
        occurrences.clear();
        previousMessage = null;
    }

    /** Strips an existing {@code (×N)} suffix from a string for comparison purposes. */
    private static String stripCountSuffix(String s) {
        int idx = s.lastIndexOf(COUNT_PREFIX);
        return idx > 0 ? s.substring(0, idx) : s;
    }
}