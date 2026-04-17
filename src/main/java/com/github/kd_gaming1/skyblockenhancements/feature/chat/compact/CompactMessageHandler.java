package com.github.kd_gaming1.skyblockenhancements.feature.chat.compact;

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
import org.jspecify.annotations.Nullable;

/**
 * Detects repeats of identical chat messages and replaces the older occurrence with a single
 * line carrying a {@code (×N)} suffix.
 *
 * <p><b>Three compaction modes</b>, evaluated in order of priority:
 * <ol>
 *   <li><b>Consecutive-only</b> ({@code onlyCompactConsecutive} = true) — only immediate
 *       repeats collapse; any intervening different message resets the counter.</li>
 *   <li><b>Time-windowed</b> ({@code compactTimeWindowMinutes} &gt; 0) — repeats within the
 *       rolling window collapse; stale first-seen timestamps start a fresh streak.</li>
 *   <li><b>Unlimited</b> — every duplicate collapses regardless of distance or age.</li>
 * </ol>
 *
 * <p>The {@link #entries} map uses <b>access-order</b> {@link LinkedHashMap}: every
 * {@link Map#get} promotes the entry to the most-recently-used position, so the eldest entry
 * (least-recently-accessed) is the natural eviction candidate once size exceeds
 * {@link #MAX_TRACKED_MESSAGES}. This keeps memory bounded on players who sit in busy lobbies
 * for hours while still tracking a generous working set of recent unique messages.
 */
public final class CompactMessageHandler {

    /**
     * Cap on distinct messages tracked simultaneously. Chosen empirically: large enough that
     * ordinary lobby activity fits comfortably (Hypixel lobbies rarely produce more than
     * ~100 distinct messages per minute of observation), small enough that the map stays in
     * a few-KB regime even with long strings.
     */
    private static final int MAX_TRACKED_MESSAGES = 512;

    private static final Style COUNT_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final String COUNT_PREFIX = " (×";
    private static final String COUNT_SUFFIX = ")";

    /** Per-message compaction state. */
    private static final class Entry {
        int count;
        long firstSeenMs;

        Entry(long nowMs) {
            this.count = 1;
            this.firstSeenMs = nowMs;
        }
    }

    private final SBEChatAccess chatAccess;

    /** Access-order LRU; see class javadoc for why. */
    private final Map<String, Entry> entries =
            new LinkedHashMap<>(64, 0.75f, /* accessOrder */ true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_TRACKED_MESSAGES;
                }
            };

    private @Nullable String previousMessage;

    public CompactMessageHandler(SBEChatAccess chatAccess) {
        this.chatAccess = chatAccess;
    }

    /**
     * Inspects an incoming message. If it qualifies as a compactable duplicate, removes the
     * prior occurrence from history and returns a new component with a {@code (×N)} suffix
     * appended; otherwise returns the original message unchanged.
     */
    public Component process(Component message) {
        if (!SkyblockEnhancementsConfig.compactDuplicateMessages) return message;

        String raw = ChatTextHelper.stripCompactSuffix(message.getString());
        if (raw.trim().isEmpty() || isSeparator(raw.trim())) return message;

        long nowMs = System.currentTimeMillis();
        boolean isConsecutive = raw.equals(previousMessage);
        previousMessage = raw;

        Entry entry = entries.get(raw);
        if (entry == null) {
            entries.put(raw, new Entry(nowMs));
            return message;
        }

        if (!isEligibleForCompaction(entry, isConsecutive, nowMs)) {
            entries.put(raw, new Entry(nowMs));
            return message;
        }

        entry.count++;
        removePreviousDuplicate(raw);
        return withCountSuffix(message, entry.count);
    }

    public void clear() {
        entries.clear();
        previousMessage = null;
    }

    // ---------------------------------------------------------------------
    // Eligibility
    // ---------------------------------------------------------------------

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

    private static MutableComponent withCountSuffix(Component message, int count) {
        MutableComponent result = message.copy();
        result.append(Component.literal(COUNT_PREFIX + count + COUNT_SUFFIX).setStyle(COUNT_STYLE));
        return result;
    }

    // ---------------------------------------------------------------------
    // History manipulation
    // ---------------------------------------------------------------------

    /** Removes the most-recent prior occfurrence of {@code raw} and any orphaned separators. */
    private void removePreviousDuplicate(String raw) {
        List<GuiMessage> msgs = chatAccess.sbe$getAllMessages();
        for (int i = 0; i < msgs.size(); i++) {
            String candidate = ChatTextHelper.stripCompactSuffix(msgs.get(i).content().getString());
            if (!candidate.equals(raw)) continue;

            msgs.remove(i);
            removeOrphanedSeparators(msgs, i);
            return;
        }
    }

    /**
     * After removing a message at {@code removedIndex}, walks outward in both directions
     * removing any separator lines that are no longer bordered by non-separator content.
     * Repeats until no further orphans are found, handling stacked separators (e.g. a role
     * header directly above a full-width rule).
     */
    private void removeOrphanedSeparators(List<GuiMessage> msgs, int removedIndex) {
        boolean removeBelow = removedIndex < msgs.size() && isOrphanedSeparator(msgs, removedIndex);
        int aboveIdx = removedIndex - 1;
        boolean removeAbove = aboveIdx >= 0 && isOrphanedSeparator(msgs, aboveIdx);

        if (removeBelow) msgs.remove(removedIndex);
        if (removeAbove && aboveIdx < msgs.size()) msgs.remove(aboveIdx);

        removeOrphanedBlanks(msgs, removedIndex);
    }

    /** Removes blank lines that are left directly adjacent to the removal site. */
    private static void removeOrphanedBlanks(List<GuiMessage> msgs, int index) {
        // Check below first (higher index), then above, so removals don't shift the above index.
        if (index < msgs.size() && plainText(msgs.get(index)).isEmpty()) {
            msgs.remove(index);
        }
        int aboveIdx = index - 1;
        if (aboveIdx >= 0 && aboveIdx < msgs.size() && plainText(msgs.get(aboveIdx)).isEmpty()) {
            msgs.remove(aboveIdx);
        }
    }

    /**
     * A separator is orphaned when it has no non-separator neighbour on at least one side,
     * meaning it no longer visually separates any content from anything else.
     */
    private static boolean isOrphanedSeparator(List<GuiMessage> msgs, int index) {
        if (!isSeparator(plainText(msgs.get(index)))) return false;

        boolean hasContentAbove = index > 0
                && !isSeparator(plainText(msgs.get(index - 1)))
                && !plainText(msgs.get(index - 1)).isEmpty();
        boolean hasContentBelow = index < msgs.size() - 1
                && !isSeparator(plainText(msgs.get(index + 1)))
                && !plainText(msgs.get(index + 1)).isEmpty();

        return !hasContentAbove || !hasContentBelow;
    }

    private static String plainText(GuiMessage msg) {
        return ChatFormatting.stripFormatting(msg.content().getString()).trim();
    }

    private static boolean isSeparator(String trimmed) {
        return ChatTextHelper.isFullSeparator(trimmed) || ChatTextHelper.isCenteredSeparator(trimmed);
    }
}