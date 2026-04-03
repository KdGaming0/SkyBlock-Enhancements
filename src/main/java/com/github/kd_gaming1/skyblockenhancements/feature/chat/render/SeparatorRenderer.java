package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a separator line: centered text flanked by smooth horizontal lines. If there is no middle
 * text (all dashes), a single line is drawn across the full width.
 */
public record SeparatorRenderer(int lineColor, @Nullable String middleText) implements CustomChatRenderer {

    private static final int TEXT_PADDING = 4;

    @Override
    public void render(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int lineX,
            int textY,
            int lineWidth,
            float alpha) {

        int lineThickness = ChatTextHelper.getString(text).contains("▬") ? 2 : 1;

        int alphaInt = ARGB.as8BitChannel(alpha);
        int color = ARGB.color(alphaInt, lineColor);
        int shadow = ARGB.scaleRGB(ARGB.color(alphaInt, lineColor), 0.25f);
        int lineY = textY + (font.lineHeight - lineThickness) / 2;
        int right = lineX + lineWidth;

        if (middleText == null || middleText.isEmpty()) {
            graphics.fill(lineX + 1, lineY + 1, right - 1, lineY + lineThickness + 1, shadow);
            graphics.fill(lineX, lineY, right - 2, lineY + lineThickness, color);
            return;
        }

        int textWidth = font.width(middleText);
        int textX = lineX + (lineWidth - textWidth) / 2;
        graphics.drawString(font, middleText, textX, textY, ARGB.color(alphaInt, 0xFFFFFF), true);

        // Left line.
        int leftEnd = textX - TEXT_PADDING;
        if (leftEnd > lineX + 2) {
            graphics.fill(lineX + 2, lineY + 1, leftEnd, lineY + lineThickness + 1, shadow);
            graphics.fill(lineX + 1, lineY, leftEnd - 1, lineY + lineThickness, color);
        }

        // Right line.
        int rightStart = textX + textWidth + TEXT_PADDING;
        if (rightStart < right - 2) {
            graphics.fill(rightStart + 1, lineY + 1, right - 1, lineY + lineThickness + 1, shadow);
            graphics.fill(rightStart, lineY, right - 2, lineY + lineThickness, color);
        }
    }
}