package com.github.kd_gaming1.skyblockenhancements.feature.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import java.util.HashMap;
import java.util.List;
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
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return message;

        // Skip compacting for separator lines, they will be handled when their main text is compacted
        if (ChatTextHelper.isFullSeparator(trimmed) || ChatTextHelper.isCenteredSeparator(trimmed)) {
            return message;
        }

        if (SkyblockEnhancementsConfig.onlyCompactConsecutive && !raw.equals(previousMessage)) {
            previousMessage = raw;
            occurrences.putIfAbsent(raw, 1);
            return message;
        }
        previousMessage = raw;

        int count = occurrences.merge(raw, 1, Integer::sum);
        if (count <= 1) return message;

        // Find and remove the previous duplicate and its adjacent orphaned separator lines
        List<GuiMessage> msgs = chatAccess.sbe$getAllMessages();
        for (int i = 0; i < msgs.size(); i++) {
            GuiMessage existing = msgs.get(i);
            String existingRaw = stripCountSuffix(existing.content().getString());

            if (existingRaw.equals(raw)) {
                // Remove the old duplicate text
                msgs.remove(i);

                if (i < msgs.size()) {
                    String olderStr = msgs.get(i).content().getString().trim();
                    if (ChatTextHelper.isFullSeparator(olderStr) || ChatTextHelper.isCenteredSeparator(olderStr)) {
                        msgs.remove(i);
                    }
                }

                if (i - 1 >= 0 && i - 1 < msgs.size()) {
                    String newerStr = msgs.get(i - 1).content().getString().trim();
                    if (ChatTextHelper.isFullSeparator(newerStr) || ChatTextHelper.isCenteredSeparator(newerStr)) {
                        msgs.remove(i - 1);
                    }
                }

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