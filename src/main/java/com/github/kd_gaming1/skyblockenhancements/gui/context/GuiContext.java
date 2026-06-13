package com.github.kd_gaming1.skyblockenhancements.gui.context;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Fast, low-overhead GUI title matching for container screens.
 *
 * <p>Screen titles are cached per-instance in a {@link WeakHashMap} so
 * {@code startsWith} checks run at most once per screen. Closed screens are
 * garbage-collected automatically — no explicit cleanup required.
 *
 * <p>The caller (feature code) provides the title prefix it cares about;
 * this class has no hard-coded knowledge of specific SkyBlock GUIs.
 */
public final class GuiContext {

    private GuiContext() {}

    private static final WeakHashMap<Screen, String> TITLE_CACHE = new WeakHashMap<>();

    /**
     * Returns the cached raw title string for the given screen, or
     * {@link Optional#empty()} if the screen is not a container screen.
     */
    public static Optional<String> getTitle(Screen screen) {
        return Optional.ofNullable(TITLE_CACHE.computeIfAbsent(screen, GuiContext::extractTitle));
    }

    /**
     * Checks whether the given container screen's title starts with the
     * supplied prefix. Returns {@code false} for non-container screens.
     */
    public static boolean matches(Screen screen, String prefix) {
        return getTitle(screen).filter(title -> title.startsWith(prefix)).isPresent();
    }

    /**
     * Checks whether the given container screen's title contains the supplied
     * substring. Returns {@code false} for non-container screens.
     */
    public static boolean contains(Screen screen, String substring) {
        return getTitle(screen).filter(title -> title.contains(substring)).isPresent();
    }

    /**
     * Executes the given action only if the screen's title starts with the
     * supplied prefix.
     */
    public static void ifMatches(Screen screen, String prefix, Runnable action) {
        if (matches(screen, prefix)) {
            action.run();
        }
    }

    /**
     * Manually removes a screen from the cache. Rarely needed; useful if a
     * screen title mutates after initial detection.
     */
    public static void invalidate(Screen screen) {
        TITLE_CACHE.remove(screen);
    }

    private static String extractTitle(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return null;
        }
        String title = container.getTitle().getString();
        return (title == null || title.isEmpty()) ? null : title;
    }
}
