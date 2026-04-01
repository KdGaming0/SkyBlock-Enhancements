package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import java.util.function.Consumer;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;

/** Proxies vanilla ChatGraphicsAccess to intercept text rendering for custom renderers. */
public class ChatGraphicsAccessProxy implements ChatComponent.ChatGraphicsAccess {

    private final ChatComponent.ChatGraphicsAccess delegate;
    private final SBEChatAccess chatAccess;
    private final GuiGraphics graphics;
    private final Font font;

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
    }

    @Override
    public boolean handleMessage(
            int textTop, float opacity, @NonNull FormattedCharSequence message) {
        CustomChatRenderer renderer = chatAccess.sbe$getRenderer(message);
        if (renderer != null) {
            renderer.render(graphics, font, message, 0, textTop, chatAccess.sbe$getScaledWidth(), opacity);
            return false;
        }
        return delegate.handleMessage(textTop, opacity, message);
    }

    @Override
    public void handleTag(
            int x0, int y0, int x1, int y1, float opacity, @NonNull GuiMessageTag tag) {
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
}