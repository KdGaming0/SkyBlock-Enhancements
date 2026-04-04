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
public record SeparatorRenderer(int lineColor, @Nullable String middleText)
        implements CustomChatRenderer {

    /** The Hypixel block-separator character that warrants a 2px line. */
    private static final int BLOCK_CHAR = '▬';

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

        // Check for the block character via codepoint iteration to avoid allocating a String.
        int lineThickness = containsBlockChar(text) ? 2 : 1;

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

        // Left arm.
        int leftEnd = textX - TEXT_PADDING;
        if (leftEnd > lineX + 2) {
            graphics.fill(lineX + 2, lineY + 1, leftEnd, lineY + lineThickness + 1, shadow);
            graphics.fill(lineX + 1, lineY, leftEnd - 1, lineY + lineThickness, color);
        }

        // Right arm.
        int rightStart = textX + textWidth + TEXT_PADDING;
        if (rightStart < right - 2) {
            graphics.fill(rightStart + 1, lineY + 1, right - 1, lineY + lineThickness + 1, shadow);
            graphics.fill(rightStart, lineY, right - 2, lineY + lineThickness, color);
        }
    }

    /**
     * Returns {@code true} if the sequence contains {@link #BLOCK_CHAR}, short-circuiting on the
     * first match. Avoids the String allocation that {@code getString().contains()} would require.
     */
    private static boolean containsBlockChar(FormattedCharSequence text) {
        boolean[] found = {false};
        text.accept((index, style, cp) -> {
            if (cp == BLOCK_CHAR) {
                found[0] = true;
                return false; // stop iteration early
            }
            return true;
        });
        return found[0];
    }
}