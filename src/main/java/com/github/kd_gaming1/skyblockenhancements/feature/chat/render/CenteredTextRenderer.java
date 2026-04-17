package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/** Draws a chat line centered horizontally in the chat area. */
public final class CenteredTextRenderer implements CustomChatRenderer {

    public static final CenteredTextRenderer INSTANCE = new CenteredTextRenderer();

    private CenteredTextRenderer() {}

    @Override
    public void render(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int lineX,
            int textY,
            int lineWidth,
            float alpha) {
        int x = lineX + (lineWidth - font.width(text)) / 2;
        int color = ARGB.color(ARGB.as8BitChannel(alpha), 0xFFFFFF);
        graphics.drawString(font, text, x, textY, color, true);
    }

    @Override
    public HitTest hitTest(Font font, FormattedCharSequence text, int lineX, int lineWidth) {
        return HitTest.shifted((lineWidth - font.width(text)) / 2);
    }
}