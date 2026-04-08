package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/**
 * Replaces the default left-aligned chat line drawing with custom rendering (centered text,
 * separator lines, etc.).
 */
public interface CustomChatRenderer {

    /**
     * Draws one chat line.
     *
     * @param graphics current GuiGraphics
     * @param font current Font
     * @param text the wrapped line content
     * @param lineX left edge of the chat area (usually 0 in scaled space)
     * @param textY baseline Y for text drawing
     * @param lineWidth chat width in scaled pixels
     * @param alpha opacity (0–1)
     */
    void render(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int lineX,
            int textY,
            int lineWidth,
            float alpha);

    /**
     * Returns the horizontal offset (in scaled pixels) from the left edge of the chat area
     * where the primary text content was drawn. Used by the graphics access proxy to align
     * the delegate's hit-test region with the actual rendered position.
     */
    default int getTextOffsetX(Font font, FormattedCharSequence text, int lineX, int lineWidth) {
        return 0;
    }
}