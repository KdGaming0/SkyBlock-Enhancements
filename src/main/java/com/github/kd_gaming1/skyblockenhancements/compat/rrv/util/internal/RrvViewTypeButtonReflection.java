package com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.internal;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Reflection facade for rebuilding {@code RecipeViewScreen.viewTypeButtons} at a new top position.
 *
 * <p>RRV hardcodes the button Y relative to a fixed {@code topPos = 32} in its private
 * {@code checkGui} method. To centre the recipe viewer vertically we modify that constant and
 * rebuild the buttons relative to the new {@code topPos}. That rebuild requires access to a
 * package-private field, a private record constructor, and a menu method on an RRV class — all
 * hidden behind reflection so the mixin stays readable.
 *
 * <p>Reflection handles are resolved lazily on first use and cached. On any reflective failure
 * the helper latches {@link #isBroken()} so subsequent calls are cheap no-ops — the mixin logs
 * once and gives up.
 */
public final class RrvViewTypeButtonReflection {

    private static final String BUTTONS_FIELD_NAME       = "viewTypeButtons";
    private static final String VIEW_TYPE_BUTTON_CLASS   = "cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen$ViewTypeButton";
    private static final String VIEW_TYPE_ORDER_METHOD   = "getViewTypeOrder";

    /** Dimensions of each rendered tab button (px). */
    private static final int BTN_W = 24;
    private static final int BTN_H = 24;
    /** Horizontal gap between buttons. */
    private static final int BTN_GAP = 2;
    /** Number of buttons in one row before wrapping (RRV's hard cap per row). */
    private static final int BUTTONS_PER_ROW = 5;

    @Nullable private static Field buttonsField;
    @Nullable private static Constructor<?> buttonConstructor;
    @Nullable private static Method orderMethod;

    private static boolean broken;

    private RrvViewTypeButtonReflection() {}

    public static boolean isBroken() {
        return broken;
    }

    /**
     * Rebuilds {@code viewTypeButtons} on the given screen using {@code newTopPos} as the
     * anchor. Returns {@code false} (and latches {@link #isBroken()}) if reflection failed.
     */
    public static boolean rebuild(RecipeViewScreen screen, RecipeViewMenu menu,
                                  int screenWidth, int newTopPos) {
        if (broken) return false;

        try {
            ensureInitialised(menu);
            assert orderMethod != null;
            List<?> order = (List<?>) orderMethod.invoke(menu);

            List<Object> rebuilt = new ArrayList<>(order.size());
            int buttonY = newTopPos - BTN_H - 1;
            int rowWidth = BUTTONS_PER_ROW * BTN_W + (BUTTONS_PER_ROW - 1) * BTN_GAP + 4;
            int rowStartX = screenWidth / 2 - rowWidth / 2;

            for (int i = 0; i < order.size(); i++) {
                int column = i % BUTTONS_PER_ROW;
                int x = rowStartX + column * (BTN_W + BTN_GAP);
                assert buttonConstructor != null;
                rebuilt.add(buttonConstructor.newInstance(
                        screen, x, buttonY, BTN_W, BTN_H, order.get(i), i));
            }

            assert buttonsField != null;
            buttonsField.set(screen, rebuilt);
            return true;

        } catch (Throwable t) {
            broken = true;
            throw new ReflectionFailure(t);
        }
    }

    private static void ensureInitialised(RecipeViewMenu menu) throws Exception {
        if (buttonsField != null) return;

        buttonsField = RecipeViewScreen.class.getDeclaredField(BUTTONS_FIELD_NAME);
        buttonsField.setAccessible(true);

        Class<?> viewTypeButton = Class.forName(VIEW_TYPE_BUTTON_CLASS);
        buttonConstructor = viewTypeButton.getDeclaredConstructors()[0];
        buttonConstructor.setAccessible(true);

        orderMethod = menu.getClass().getMethod(VIEW_TYPE_ORDER_METHOD);
    }

    /** Propagated so the mixin can log once and stop trying. */
    public static final class ReflectionFailure extends RuntimeException {
        ReflectionFailure(Throwable cause) {
            super(cause);
        }
    }
}