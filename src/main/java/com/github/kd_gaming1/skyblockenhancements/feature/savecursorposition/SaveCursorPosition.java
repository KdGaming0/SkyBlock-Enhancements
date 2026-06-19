/*
 * Based on code from Firmament:
 * https://github.com/FirmamentMC/Firmament
 *
 * This file contains significant portions adapted from Firmament's
 * Save Cursor Position implementation.
 *
 * The original code is licensed under the GNU General Public License v3.0.
 *
 * Modifications:
 * - Translated from Kotlin to Java
 * - Modified for Skyblock Enhancements
 */

package com.github.kd_gaming1.skyblockenhancements.feature.savecursorposition;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class SaveCursorPosition {

    private SaveCursorPosition() {}

    private static CursorPosition pendingOriginal;
    private static SavedCursorPosition saved;

    /**
     * Called from {@code MouseHandler.grabMouse()} before the cursor is centred.
     * Captures the original cursor position.
     */
    public static void saveCursorOriginal(double cursorX, double cursorY) {
        if (!SkyblockEnhancementsConfig.saveCursorPosition) {
            return;
        }
        pendingOriginal = new CursorPosition(cursorX, cursorY);
    }

    /**
     * Called from {@code MouseHandler.grabMouse()} after the cursor is centred.
     * Stores the original position together with the screen centre.
     */
    public static void saveCursorMiddle(double middleX, double middleY) {
        if (!SkyblockEnhancementsConfig.saveCursorPosition) {
            pendingOriginal = null;
            return;
        }
        CursorPosition original = pendingOriginal;
        pendingOriginal = null;
        if (original == null) {
            return;
        }
        saved = new SavedCursorPosition(original.x, original.y, middleX, middleY, System.currentTimeMillis());
    }

    /**
     * Called from {@code MouseHandler.releaseMouse()} before GLFW is asked to move the cursor.
     * If the saved position is still fresh and the screen centre matches, returns the cursor
     * position that should be used instead of the centre.
     */
    public static CursorPosition loadCursor(double middleX, double middleY, Screen newScreen) {
        if (!SkyblockEnhancementsConfig.saveCursorPosition) {
            saved = null;
            return null;
        }

        SavedCursorPosition last = saved;
        saved = null;
        if (last == null) {
            return null;
        }

        if (newScreen instanceof AbstractContainerScreen<?> container && !passesFilter(container)) {
            return null;
        }

        long elapsed = System.currentTimeMillis() - last.savedAt;
        if (elapsed > SkyblockEnhancementsConfig.saveCursorPositionToleranceMs) {
            return null;
        }

        if (Math.abs(last.middleX - middleX) >= 1.0 || Math.abs(last.middleY - middleY) >= 1.0) {
            return null;
        }

        return new CursorPosition(last.cursorX, last.cursorY);
    }

    private static boolean passesFilter(AbstractContainerScreen<?> screen) {
        CursorFilterMode mode = SkyblockEnhancementsConfig.saveCursorPositionFilterMode;
        if (mode == CursorFilterMode.ALL) {
            return true;
        }

        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        List<String> list = SkyblockEnhancementsConfig.saveCursorPositionFilterList;
        if (list == null || list.isEmpty()) {
            return mode == CursorFilterMode.BLACKLIST;
        }

        for (String entry : list) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean matches = title.contains(trimmed);
            if (mode == CursorFilterMode.WHITELIST && matches) {
                return true;
            }
            if (mode == CursorFilterMode.BLACKLIST && matches) {
                return false;
            }
        }

        return mode == CursorFilterMode.BLACKLIST;
    }

    public record CursorPosition(double x, double y) {}

    private record SavedCursorPosition(
            double cursorX, double cursorY, double middleX, double middleY, long savedAt) {}
}
