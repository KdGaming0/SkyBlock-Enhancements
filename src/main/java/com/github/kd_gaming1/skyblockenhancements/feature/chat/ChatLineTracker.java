package com.github.kd_gaming1.skyblockenhancements.feature.chat;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CustomChatRenderer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.client.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Per-ChatComponent bookkeeping shared by every chat feature that needs to correlate a
 * displayed line with its source message or renderer.
 *
 * <p>The map is keyed on the {@link FormattedCharSequence} stored inside each
 * {@link GuiMessage.Line}, which is unique per displayed line. This gives O(1) lookups for:
 * <ul>
 *   <li>hover/click hit-testing (content → renderer),</li>
 *   <li>copy and delete resolution (content/line → parent message),</li>
 *   <li>selection outline rendering (parent identity comparison).</li>
 * </ul>
 *
 * <p>Line registration is bracketed by {@link #beginAddingLinesFor(GuiMessage)} and
 * {@link #finishAddingLines()} so callers don't have to pass the parent to every record call.
 */
public final class ChatLineTracker {

    private record Entry(GuiMessage parent, @Nullable CustomChatRenderer renderer) {}

    private final Map<FormattedCharSequence, Entry> byContent = new Reference2ObjectOpenHashMap<>();

    private @Nullable GuiMessage pendingParent;
    private @Nullable GuiMessage selectedMessage;

    public void beginAddingLinesFor(GuiMessage parent) {
        this.pendingParent = parent;
    }

    public void finishAddingLines() {
        this.pendingParent = null;
    }

    /** Associates a freshly-added display line with its parent message and optional renderer. */
    public void recordLine(GuiMessage.Line line, @Nullable CustomChatRenderer renderer) {
        if (pendingParent == null) return;
        byContent.put(line.content(), new Entry(pendingParent, renderer));
    }

    public void evictLine(GuiMessage.Line line) {
        byContent.remove(line.content());
    }

    /** Discards all per-line state AND any active selection. Use on full history clear. */
    public void clearAll() {
        byContent.clear();
        selectedMessage = null;
    }

    /**
     * Discards per-line state only; leaves the selected message intact so the outline survives
     * display-queue rebuilds (e.g. rescale, tab switch) as long as the message still exists.
     */
    public void clearLineMappings() {
        byContent.clear();
    }

    public @Nullable CustomChatRenderer rendererFor(FormattedCharSequence content) {
        Entry entry = byContent.get(content);
        return entry == null ? null : entry.renderer;
    }

    public @Nullable GuiMessage parentFor(FormattedCharSequence content) {
        Entry entry = byContent.get(content);
        return entry == null ? null : entry.parent;
    }

    public @Nullable GuiMessage parentFor(GuiMessage.Line line) {
        return parentFor(line.content());
    }

    public void setSelectedMessage(@Nullable GuiMessage message) {
        this.selectedMessage = message;
    }

    public @Nullable GuiMessage getSelectedMessage() {
        return selectedMessage;
    }
}