package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Creates a row of icon-only category-filter toggle buttons for the RRV item list overlay.
 *
 * <p>Active state is stored in {@link SkyblockCategoryState}. Clicking a button toggles the
 * category and triggers {@link OverlayManager#updateOverlaysAndWidgets()}, which re-runs the
 * query pipeline and re-creates these buttons with updated toggled state.
 *
 * <p>Button categories and sprite names are derived directly from
 * {@link SkyblockItemCategory#BUTTON_CATEGORIES} — no parallel arrays to keep in sync.
 */
public final class SkyblockCategoryButtons {

    /** Pixel size (width = height) of each square icon button. */
    private static final int BTN_SIZE = 16;

    /** Horizontal gap between adjacent buttons in pixels. */
    private static final int BTN_GAP = 2;

    private SkyblockCategoryButtons() {}

    /**
     * Builds and positions icon buttons centered above the search bar.
     *
     * @param searchBarX     left edge of the search bar in screen pixels
     * @param searchBarY     top edge of the search bar in screen pixels
     * @param searchBarWidth width of the search bar in pixels
     * @return buttons ready to be registered as renderables
     */
    public static List<CategoryIconButton> create(
            int searchBarX, int searchBarY, int searchBarWidth) {

        List<SkyblockItemCategory> categories = SkyblockItemCategory.BUTTON_CATEGORIES;
        int count = categories.size();
        int totalWidth = count * BTN_SIZE + (count - 1) * BTN_GAP;
        int startX = searchBarX + (searchBarWidth - totalWidth) / 2;
        int btnY = searchBarY - BTN_SIZE - 2;

        @Nullable SkyblockItemCategory active = SkyblockCategoryState.getActiveCategory();
        List<CategoryIconButton> buttons = new ArrayList<>(count);

        int x = startX;
        for (SkyblockItemCategory category : categories) {
            CategoryIconButton btn = new CategoryIconButton(
                    x, btnY, BTN_SIZE,
                    category.getSpriteName(),
                    category == active,
                    b -> onToggle(category));
            buttons.add(btn);
            x += BTN_SIZE + BTN_GAP;
        }

        return buttons;
    }

    private static void onToggle(SkyblockItemCategory category) {
        SkyblockCategoryState.toggle(category);
        OverlayManager.INSTANCE.updateOverlaysAndWidgets();
    }
}