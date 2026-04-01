package com.github.kd_gaming1.skyblockenhancements.access;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CustomChatRenderer;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Duck interface mixed into {@link net.minecraft.client.gui.components.ChatComponent}. */
public interface SBEChatAccess {

    List<GuiMessage> sbe$getAllMessages();

    void sbe$refreshMessages();

    int sbe$getScaledWidth();

    /** Look up a custom renderer by the line's FormattedCharSequence content. */
    @Nullable CustomChatRenderer sbe$getRenderer(FormattedCharSequence content);

    /** Captured during render — valid only inside the render call. */
    @Nullable GuiGraphics sbe$getGraphics();

    @Nullable Font sbe$getFont();
}