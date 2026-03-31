package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a separator line: centered text flanked by smooth horizontal lines.
 *
 * <p>If there is no middle text (all dashes), a single line is drawn across the full width.
 *
 * <p>Inspired by BetterHypixelChat by viciscat (GPLv3). This is an original implementation.
 */
public record SeparatorRenderer(int lineColor, @Nullable String middleText)
        implements CustomChatRenderer {

    private static final int LINE_THICKNESS = 1;
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
        int alphaInt = ARGB.as8BitChannel(alpha);
        int color = ARGB.color(alphaInt, lineColor);
        int shadowColor = ARGB.scaleRGB(ARGB.color(alphaInt, lineColor), 0.25f);
        int lineY = textY + (font.lineHeight - LINE_THICKNESS) / 2;

        if (middleText == null || middleText.isEmpty()) {
            // Full-width separator line.
            graphics.fill(lineX + 1, lineY + 1, lineX + lineWidth - 1, lineY + LINE_THICKNESS + 1,
                    shadowColor);
            graphics.fill(lineX, lineY, lineX + lineWidth - 2, lineY + LINE_THICKNESS, color);
            return;
        }

        // Draw centered text.
        int textWidth = font.width(middleText);
        int textX = lineX + (lineWidth - textWidth) / 2;
        int textColor = ARGB.color(alphaInt, 0xFFFFFF);
        graphics.drawString(font, middleText, textX, textY, textColor, true);

        // Left line.
        int leftEnd = textX - TEXT_PADDING;
        if (leftEnd > lineX + 2) {
            graphics.fill(lineX + 2, lineY + 1, leftEnd, lineY + LINE_THICKNESS + 1, shadowColor);
            graphics.fill(lineX + 1, lineY, leftEnd - 1, lineY + LINE_THICKNESS, color);
        }

        // Right line.
        int rightStart = textX + textWidth + TEXT_PADDING;
        int rightEnd = lineX + lineWidth;
        if (rightStart < rightEnd - 2) {
            graphics.fill(rightStart + 1, lineY + 1, rightEnd - 1, lineY + LINE_THICKNESS + 1,
                    shadowColor);
            graphics.fill(rightStart, lineY, rightEnd - 2, lineY + LINE_THICKNESS, color);
        }
    }
}