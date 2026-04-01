package com.github.kd_gaming1.skyblockenhancements.feature.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import java.util.LinkedHashMap;
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
    private static final String COUNT_PREFIX = " (×";
    private static final int MAX_TRACKED = 512;

    private final SBEChatAccess chatAccess;
    private final Map<String, Integer> occurrences =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > MAX_TRACKED;
                }
            };
    private @Nullable String previousMessage;

    public CompactMessageHandler(SBEChatAccess chatAccess) {
        this.chatAccess = chatAccess;
    }

    /**
     * Processes an incoming message. If it is a duplicate, removes the previous occurrence and
     * returns a copy with an {@code (×N)} suffix.
     */
    public Component process(Component message) {
        if (!SkyblockEnhancementsConfig.compactDuplicateMessages) return message;

        String raw = stripCountSuffix(message.getString());
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return message;

        // Skip separator lines — they compact with their parent message.
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

        removePreviousDuplicate(raw);

        MutableComponent result = message.copy();
        result.append(Component.literal(COUNT_PREFIX + count + ")").setStyle(COUNT_STYLE));
        return result;
    }

    /** Clears all tracked state (called on chat clear). */
    public void clear() {
        occurrences.clear();
        previousMessage = null;
    }

    private void removePreviousDuplicate(String raw) {
        List<GuiMessage> msgs = chatAccess.sbe$getAllMessages();
        for (int i = 0; i < msgs.size(); i++) {
            if (!stripCountSuffix(msgs.get(i).content().getString()).equals(raw)) continue;

            msgs.remove(i);

            // Remove orphaned separator below (now at index i after shift).
            if (i < msgs.size() && isSeparator(msgs.get(i).content().getString().trim())) {
                msgs.remove(i);
            }

            // Remove orphaned separator above (now at i-1 after possible shift).
            int above = i - 1;
            if (above >= 0 && above < msgs.size()
                    && isSeparator(msgs.get(above).content().getString().trim())) {
                msgs.remove(above);
            }

            chatAccess.sbe$refreshMessages();
            return;
        }
    }

    private static boolean isSeparator(String trimmed) {
        return ChatTextHelper.isFullSeparator(trimmed) || ChatTextHelper.isCenteredSeparator(trimmed);
    }

    private static String stripCountSuffix(String s) {
        int idx = s.lastIndexOf(COUNT_PREFIX);
        return idx > 0 ? s.substring(0, idx) : s;
    }
}