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
 * <p>When a duplicate is removed, every separator, blank line, and accompanying line that
 * arrived on the same tick is removed with it. This treats each Hypixel block (banner, profile
 * view, summary card) as an atomic unit: the separators that bordered it are part of the same
 * logical message and share its {@link GuiMessage#addedTime()}.
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
     * prior occurrence (along with its tick-group companions) from history and returns a new
     * component with a {@code (×N)} suffix appended; otherwise returns the original message
     * unchanged.
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

    /**
     * Removes the most-recent prior occurrence of {@code raw} and every other message that
     * arrived on the same tick. Hypixel emits multi-line blocks (banners, profile views,
     * separator borders) atomically within one tick, so the tick is the authoritative
     * grouping signal for "everything that belongs to this message".
     */
    private void removePreviousDuplicate(String raw) {
        List<GuiMessage> msgs = chatAccess.sbe$getAllMessages();
        for (int i = 0; i < msgs.size(); i++) {
            String candidate = ChatTextHelper.stripCompactSuffix(msgs.get(i).content().getString());
            if (!candidate.equals(raw)) continue;

            removeTickGroupAt(msgs, i);
            return;
        }
    }

    /**
     * Removes the message at {@code anchorIndex} and every contiguous neighbour sharing its
     * {@link GuiMessage#addedTime()}. The scan stops at the first timestamp mismatch in each
     * direction, so the cost is proportional to the group size — a handful of messages for
     * typical Hypixel blocks.
     */
// CompactMessageHandler.java

    private static void removeTickGroupAt(List<GuiMessage> msgs, int anchorIndex) {
        int anchorTick = msgs.get(anchorIndex).addedTime();

        int upper = anchorIndex;
        while (upper + 1 < msgs.size() && msgs.get(upper + 1).addedTime() == anchorTick) {
            upper++;
        }
        int lower = anchorIndex;
        while (lower - 1 >= 0 && msgs.get(lower - 1).addedTime() == anchorTick) {
            lower--;
        }

        msgs.subList(lower, upper + 1).clear();
    }

    private static boolean isSeparator(String trimmed) {
        return ChatTextHelper.isFullSeparator(trimmed) || ChatTextHelper.isCenteredSeparator(trimmed);
    }
}