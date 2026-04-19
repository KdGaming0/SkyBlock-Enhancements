package com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.internal;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;

/**
 * Locates and nulls out Skyblocker's QuickNav button-list field on an {@link AbstractContainerScreen}.
 *
 * <p>Skyblocker injects a {@code List<QuickNavButton>} into the screen via a private field added
 * by its own mixin. When RRV's {@code RecipeViewScreen} opens, that list still renders on top of
 * the recipe UI. We can't depend on Skyblocker at compile time, so we find the field by inspecting
 * every {@code List} declared on {@link AbstractContainerScreen} and checking the first element's
 * fully-qualified class name against the known QuickNav package.
 */
public final class QuickNavListFinder {

    /** Fully-qualified prefix of Skyblocker's QuickNav classes. */
    public static final String QUICK_NAV_PACKAGE = "de.hysky.skyblocker.skyblock.quicknav.QuickNav";

    private QuickNavListFinder() {}

    /**
     * Nulls out every field on {@link AbstractContainerScreen} whose populated {@code List}
     * contains QuickNav entries. Returns {@code true} if any field was cleared.
     *
     * @throws IllegalAccessException if reflection is denied — caller should fall back to a warning
     */
    public static void clearQuickNavList(Object screenInstance) throws IllegalAccessException {
        boolean cleared = false;
        for (Field field : AbstractContainerScreen.class.getDeclaredFields()) {
            if (field.getType() != List.class) continue;
            clearIfQuickNavList(field, screenInstance);
        }
    }

    private static void clearIfQuickNavList(Field field, Object instance) throws IllegalAccessException {
        field.setAccessible(true);
        Object value = field.get(instance);

        Object first = firstElement(value);
        if (first == null) return;
        if (!first.getClass().getName().startsWith(QUICK_NAV_PACKAGE)) return;

        field.set(instance, null);
    }

    @Nullable
    private static Object firstElement(@Nullable Object listLike) {
        if (!(listLike instanceof List<?> list) || list.isEmpty()) return null;
        return list.getFirst();
    }
}