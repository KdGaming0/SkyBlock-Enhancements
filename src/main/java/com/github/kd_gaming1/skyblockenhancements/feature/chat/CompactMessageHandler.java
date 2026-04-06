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
 *
 * <p>Three compaction modes are supported (evaluated in order of priority):
 * <ol>
 *   <li><b>Consecutive-only</b> ({@code onlyCompactConsecutive}): only collapses a message if it
 *       repeats immediately after itself. Any intervening message resets the streak and counter.
 *   <li><b>Time-window</b> ({@code compactWithinTimeWindowMinutes > 0}): collapses repeats that
 *       arrive within a configurable rolling window. Messages outside the window start a fresh
 *       streak.
 *   <li><b>Unlimited</b>: the original behaviour — collapse every duplicate regardless of position
 *       or age.
 * </ol>
 */
public final class CompactMessageHandler {

    private static final Style COUNT_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final String COUNT_PREFIX = " (×";
    private static final int MAX_TRACKED = 512;

    /** Holds the compaction state for a single unique message text. */
    private static final class Entry {
        int count;
        long firstSeenMs;
        boolean consecutiveEligible;

        Entry(long nowMs) {
            this.count = 1;
            this.firstSeenMs = nowMs;
            this.consecutiveEligible = true;
        }
    }

    private final SBEChatAccess chatAccess;

    // LRU map so the eldest entries are evicted automatically once we exceed MAX_TRACKED.
    private final Map<String, Entry> entries =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_TRACKED;
                }
            };

    private @Nullable String previousMessage;

    public CompactMessageHandler(SBEChatAccess chatAccess) {
        this.chatAccess = chatAccess;
    }

    /**
     * Processes an incoming message. If it qualifies as a compactable duplicate, removes the
     * previous occurrence from history and returns a copy with a {@code (×N)} suffix.
     */
    public Component process(Component message) {
        if (!SkyblockEnhancementsConfig.compactDuplicateMessages) return message;

        String raw = ChatTextHelper.stripCompactSuffix(message.getString());
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || isSeparator(trimmed)) return message;

        long nowMs = System.currentTimeMillis();
        boolean isConsecutive = raw.equals(previousMessage);
        previousMessage = raw;

        Entry entry = entries.get(raw);

        if (entry == null) {
            // First time we see this message — record it and return as-is.
            entries.put(raw, new Entry(nowMs));
            return message;
        }

        if (!isEligibleForCompaction(entry, isConsecutive, nowMs)) {
            // Streak broken or window expired — reset and treat as a fresh first occurrence.
            entries.put(raw, new Entry(nowMs));
            return message;
        }

        // Eligible duplicate: increment and compact.
        entry.count++;
        removePreviousDuplicate(raw);

        MutableComponent result = message.copy();
        result.append(Component.literal(COUNT_PREFIX + entry.count + ")").setStyle(COUNT_STYLE));
        return result;
    }

    /** Clears all tracked state (called on chat clear). */
    public void clear() {
        entries.clear();
        previousMessage = null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code entry} should be collapsed with the incoming duplicate.
     *
     * <p>Priority:
     * <ol>
     *   <li>Consecutive-only mode: the message must have arrived immediately after itself.
     *   <li>Time-window mode: the first occurrence must be within the configured window.
     *   <li>Otherwise: always eligible.
     * </ol>
     */
    private static boolean isEligibleForCompaction(Entry entry, boolean isConsecutive, long nowMs) {
        if (SkyblockEnhancementsConfig.onlyCompactConsecutive) {
            return isConsecutive;
        }

        int windowMinutes = SkyblockEnhancementsConfig.compactTimeWindowMinutes;
        if (windowMinutes > 0) {
            long windowMs = (long) windowMinutes * 60_000L;
            return (nowMs - entry.firstSeenMs) <= windowMs;
        }

        return true;
    }

    /**
     * Removes the most-recent prior occurrence of {@code raw} from the raw-message history, then
     * cleans up any separator lines that became orphaned as a result.
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