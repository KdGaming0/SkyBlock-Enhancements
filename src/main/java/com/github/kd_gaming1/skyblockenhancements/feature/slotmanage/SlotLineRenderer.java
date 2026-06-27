package com.github.kd_gaming1.skyblockenhancements.feature.slotmanage;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a 1px diagonal line between two screen-space points using Bresenham's algorithm, emitting one
 * {@code fill(x, y, x + 1, y + 1, color)} per pixel step. {@link GuiGraphicsExtractor} has no
 * diagonal-line primitive, so a connecting line between two slot centres must be traced this way.
 */
public final class SlotLineRenderer {

    private SlotLineRenderer() {}

    public static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1;
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x0 += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += stepY;
            }
        }
    }
}
