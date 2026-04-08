package com.github.kd_gaming1.skyblockenhancements.access;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CustomChatRenderer;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Duck interface mixed into {@link net.minecraft.client.gui.components.ChatComponent}. */
public interface SBEChatAccess {

    List<GuiMessage> sbe$getAllMessages();

    List<GuiMessage.Line> sbe$getTrimmedMessages();

    int sbe$getChatScrollbarPos();

    void sbe$refreshMessages();

    int sbe$getScaledWidth();

    /** Look up a custom renderer by the line's FormattedCharSequence content. */
    @Nullable CustomChatRenderer sbe$getRenderer(FormattedCharSequence content);

    /**
     * Returns the parent {@link GuiMessage} that produced the given display line, or {@code null}
     * if no mapping exists. This provides O(1) reverse lookup from any trimmed line back to its
     * source message — used for copy resolution, hover highlights, and context menu targeting.
     */
    @Nullable GuiMessage sbe$getParentMessage(GuiMessage.Line line);

    /**
     * Sets the message that should be visually highlighted (outlined) in the chat display.
     * Used by the context menu to show which message is targeted before the user picks an action.
     * Pass {@code null} to clear the highlight.
     */
    void sbe$setSelectedMessage(@Nullable GuiMessage message);

    /** Returns the currently highlighted message, or {@code null} if none. */
    @Nullable GuiMessage sbe$getSelectedMessage();
}