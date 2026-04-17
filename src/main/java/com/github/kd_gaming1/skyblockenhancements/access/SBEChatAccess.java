package com.github.kd_gaming1.skyblockenhancements.access;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatLineTracker;
import java.util.List;
import net.minecraft.client.GuiMessage;

/**
 * Duck interface mixed into {@link net.minecraft.client.gui.components.ChatComponent}.
 * Exposes the collaborators external chat features need: raw/display history, scroll state,
 * scaled geometry, and the per-instance {@link ChatLineTracker}.
 */
public interface SBEChatAccess {

    List<GuiMessage> sbe$getAllMessages();

    List<GuiMessage.Line> sbe$getTrimmedMessages();

    int sbe$getChatScrollbarPos();

    int sbe$getScaledWidth();

    void sbe$refreshMessages();

    ChatLineTracker sbe$getLineTracker();
}