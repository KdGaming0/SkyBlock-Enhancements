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
 */
public final class SkyblockCategoryButtons {

    /** Pixel size (width = height) of each square icon button. */
    private static final int BTN_SIZE = 16;

    /** Horizontal gap between adjacent buttons in pixels. */
    private static final int BTN_GAP = 2;

    private static final SkyblockItemCategory[] CATEGORIES = {
            SkyblockItemCategory.ARMOR,
            SkyblockItemCategory.WEAPON,
            SkyblockItemCategory.TOOL,
            SkyblockItemCategory.ACCESSORY,
            SkyblockItemCategory.PET,
            SkyblockItemCategory.EQUIPMENT,
            SkyblockItemCategory.COSMETIC,
            SkyblockItemCategory.MATERIAL,
    };

    /**
     * Base sprite names — must exactly match the file names under
     * {@code assets/skyblock_enhancements/textures/gui/sprites/item_list/}
     * (without the {@code .png} extension or any state suffix).
     */
    private static final String[] SPRITE_NAMES = {
            "armour",
            "weaponry",
            "tools",
            "accessories",
            "pets",
            "equipment",
            "cosmetics",
            "materials",
    };

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

        int count = CATEGORIES.length;
        int totalWidth = count * BTN_SIZE + (count - 1) * BTN_GAP;
        int startX = searchBarX + (searchBarWidth - totalWidth) / 2;
        int btnY = searchBarY - BTN_SIZE - 2;

        @Nullable SkyblockItemCategory active = SkyblockCategoryState.getActiveCategory();
        List<CategoryIconButton> buttons = new ArrayList<>(count);

        int x = startX;
        for (int i = 0; i < count; i++) {
            SkyblockItemCategory category = CATEGORIES[i];
            CategoryIconButton btn = new CategoryIconButton(
                    x, btnY, BTN_SIZE,
                    SPRITE_NAMES[i],
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