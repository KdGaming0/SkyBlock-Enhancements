package com.github.kd_gaming1.skyblockenhancements.gui.storage;

/**
 * Simple mutable rectangle for layout calculations.
 */
public final class Rect {
    public int x, y, width, height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
