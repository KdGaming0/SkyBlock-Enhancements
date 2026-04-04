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
     * returns a copy with a {@code (×N)} suffix.
     */
    public Component process(Component message) {
        if (!SkyblockEnhancementsConfig.compactDuplicateMessages) return message;

        // ChatTextHelper.stripCompactSuffix validates digit-only counts, unlike a bare lastIndexOf.
        String raw = ChatTextHelper.stripCompactSuffix(message.getString());
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return message;

        // Separator lines are always kept as-is; they compact with their surrounding content.
        if (isSeparator(trimmed)) {
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

    /**
     * Removes the first occurrence of {@code raw} from the raw-message history, then cleans up any
     * separator lines that became orphaned as a result.
     *
     * <p>A separator is orphaned when it no longer has a non-separator neighbour on at least one
     * side.
     */
    private void removePreviousDuplicate(String raw) {
        List<GuiMessage> msgs = chatAccess.sbe$getAllMessages();
        for (int i = 0; i < msgs.size(); i++) {
            if (!ChatTextHelper.stripCompactSuffix(msgs.get(i).content().getString()).equals(raw)) {
                continue;
            }
            msgs.remove(i);
            removeOrphanedSeparators(msgs, i);
            chatAccess.sbe$refreshMessages();
            return;
        }
    }

    /**
     * After a message at {@code removedIndex} was removed, checks the neighbours at that position
     * for orphaned separators. Removals are done highest-index-first to keep indices stable.
     */
    private static void removeOrphanedSeparators(List<GuiMessage> msgs, int belowIdx) {
        boolean removeBelow = belowIdx < msgs.size() && isOrphanedSeparator(msgs, belowIdx);

        int aboveIdx = belowIdx - 1;
        boolean removeAbove = aboveIdx >= 0 && isOrphanedSeparator(msgs, aboveIdx);

        if (removeBelow) msgs.remove(belowIdx);
        if (removeAbove && aboveIdx < msgs.size()) msgs.remove(aboveIdx);
    }

    /**
     * Returns {@code true} if the message at {@code index} is a separator with no non-separator
     * neighbour on at least one side.
     */
    private static boolean isOrphanedSeparator(List<GuiMessage> msgs, int index) {
        if (!isSeparator(plainText(msgs.get(index)))) return false;

        boolean hasContentAbove = index > 0 && !isSeparator(plainText(msgs.get(index - 1)));
        boolean hasContentBelow =
                index < msgs.size() - 1 && !isSeparator(plainText(msgs.get(index + 1)));

        return !hasContentAbove || !hasContentBelow;
    }

    private static String plainText(GuiMessage msg) {
        return ChatFormatting.stripFormatting(msg.content().getString()).trim();
    }

    private static boolean isSeparator(String trimmed) {
        return ChatTextHelper.isFullSeparator(trimmed) || ChatTextHelper.isCenteredSeparator(trimmed);
    }
}