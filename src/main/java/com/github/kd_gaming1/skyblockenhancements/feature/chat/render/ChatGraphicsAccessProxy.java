package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import java.util.function.Consumer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Proxies vanilla {@link ChatComponent.ChatGraphicsAccess} to intercept text rendering for custom
 * renderers while preserving mouse interaction (hover/click) detection.
 */
public class ChatGraphicsAccessProxy implements ChatComponent.ChatGraphicsAccess {

    /** Semi-transparent white outline for the selected message. */
    private static final int OUTLINE_COLOR = 0x40FFFFFF;

    private final ChatComponent.ChatGraphicsAccess delegate;
    private final SBEChatAccess chatAccess;
    private final GuiGraphics graphics;
    private final Font font;

    /**
     * Tracks the current line being processed so we can check if it belongs to the selected
     * message for outline rendering.
     */
    private GuiMessage.@Nullable Line currentLine;

    /** Entry bounds captured from the background fill call, used for outline rendering. */
    private int currentEntryTop;
    private int currentEntryBottom;

    public ChatGraphicsAccessProxy(
            ChatComponent.ChatGraphicsAccess delegate,
            SBEChatAccess chatAccess,
            GuiGraphics graphics,
            Font font) {
        this.delegate = delegate;
        this.chatAccess = chatAccess;
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public void updatePose(@NonNull Consumer<Matrix3x2f> updater) {
        delegate.updatePose(updater);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        delegate.fill(x0, y0, x1, y1, color);

        currentEntryTop = y0;
        currentEntryBottom = y1;
    }

    @Override
    public boolean handleMessage(
            int textTop, float opacity, @NonNull FormattedCharSequence message) {

        currentLine = resolveCurrentLine(message);
        CustomChatRenderer renderer = chatAccess.sbe$getRenderer(message);

        boolean hovered;
        if (renderer != null) {
            int scaledWidth = chatAccess.sbe$getScaledWidth();

            if (graphics != null) {
                renderer.render(graphics, font, message, 0, textTop, scaledWidth, opacity);
            }

            int offsetX = renderer.getTextOffsetX(font, message, 0, scaledWidth);

            if (offsetX == -1) {
                hovered = false;
            } else {
                if (offsetX != 0) {
                    delegate.updatePose(pose -> pose.translate(offsetX, 0));
                }

                if (graphics != null) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(0, -10000);
                }

                hovered = delegate.handleMessage(textTop, opacity, message);

                if (graphics != null) {
                    graphics.pose().popMatrix();
                }

                if (offsetX != 0) {
                    delegate.updatePose(pose -> pose.translate(-offsetX, 0));
                }
            }
        } else {
            hovered = delegate.handleMessage(textTop, opacity, message);
        }

        renderSelectionOutline(opacity);
        return hovered;
    }

    @Override
    public void handleTag(int x0, int y0, int x1, int y1, float opacity, @NonNull GuiMessageTag tag) {
        // Intentionally blank — disables the colored indicator bar on the left.
    }

    @Override
    public void handleTagIcon(
            int left,
            int bottom,
            boolean forceVisible,
            @NonNull GuiMessageTag tag,
            GuiMessageTag.@NonNull Icon icon) {
        delegate.handleTagIcon(left, bottom, forceVisible, tag, icon);
    }

    // --- Selection outline ---

    /**
     * Draws a subtle outline around the current entry if its parent message is the one
     * selected by the context menu.
     */
    private void renderSelectionOutline(float opacity) {
        if (graphics == null) return;

        GuiMessage selected = chatAccess.sbe$getSelectedMessage();
        if (selected == null || currentLine == null) return;

        GuiMessage parent = chatAccess.sbe$getParentMessage(currentLine);
        if (parent != selected) return;

        int scaledWidth = chatAccess.sbe$getScaledWidth();
        int alpha = ARGB.as8BitChannel(opacity * 0.35f);
        int color = ARGB.color(alpha, 0xFF, 0xFF, 0xFF);

        int x0 = -4;
        int x1 = scaledWidth + 4 + 4;
        int y0 = currentEntryTop;
        int y1 = currentEntryBottom;

        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    /**
     * Finds the {@link GuiMessage.Line} whose {@code content()} matches the given sequence
     * by identity. This works because we use the same {@link FormattedCharSequence} references
     * that were stored in {@code trimmedMessages}.
     */
    private GuiMessage.@Nullable Line resolveCurrentLine(FormattedCharSequence message) {
        var trimmed = chatAccess.sbe$getTrimmedMessages();
        int scrollPos = chatAccess.sbe$getChatScrollbarPos();

        int pageSize = Math.min(trimmed.size() - scrollPos, 100);
        for (int i = 0; i < pageSize; i++) {
            GuiMessage.Line line = trimmed.get(i + scrollPos);
            if (line.content() == message) {
                return line;
            }
        }
        return null;
    }
}