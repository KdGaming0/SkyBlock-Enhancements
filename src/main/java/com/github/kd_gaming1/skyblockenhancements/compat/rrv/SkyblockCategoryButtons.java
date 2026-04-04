package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Creates a compact row of category-filter toggle buttons for the RRV item list overlay.
 *
 * <p>Active state is stored in {@link SkyblockCategoryState}; no search-bar text is modified.
 * Clicking a button toggles the category and calls
 * {@link OverlayManager#updateOverlaysAndWidgets()}, which re-runs the query pipeline and
 * refreshes the buttons with updated styling — all in a single call.
 */
public final class SkyblockCategoryButtons {

    /** Pixel height of each compact button. */
    private static final int BTN_HEIGHT = 12;

    /** Horizontal gap between adjacent buttons. */
    private static final int BTN_GAP = 1;

    /** Horizontal label padding (each side). */
    private static final int LABEL_PAD = 3;

    /** Minimum button width in pixels. */
    private static final int MIN_WIDTH = 14;

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

    private static final String[] LABELS = {
            "Armor", "Wep", "Tool", "Acc", "Pet", "Equip", "Cos", "Mat"
    };

    private SkyblockCategoryButtons() {}

    /**
     * Builds and positions the toggle buttons centered above the search bar.
     *
     * @param searchBarX     left edge of the search bar
     * @param searchBarY     top edge of the search bar
     * @param searchBarWidth width of the search bar
     * @return buttons ready to be added as renderables
     */
    public static List<Button> create(int searchBarX, int searchBarY, int searchBarWidth) {
        int[] widths = computeWidths(searchBarWidth);
        int totalWidth = sum(widths) + BTN_GAP * (widths.length - 1);

        int startX = searchBarX + (searchBarWidth - totalWidth) / 2;
        int btnY = searchBarY - BTN_HEIGHT - 2;

        @Nullable SkyblockItemCategory active = SkyblockCategoryState.getActiveCategory();
        List<Button> buttons = new ArrayList<>(CATEGORIES.length);

        int x = startX;
        for (int i = 0; i < CATEGORIES.length; i++) {
            SkyblockItemCategory category = CATEGORIES[i];
            boolean isActive = category == active;

            MutableComponent label = Component.literal((isActive ? "§a" : "§7") + LABELS[i]);

            Button btn =
                    Button.builder(label, b -> onToggle(category))
                            .pos(x, btnY)
                            .size(widths[i], BTN_HEIGHT)
                            .build();

            buttons.add(btn);
            x += widths[i] + BTN_GAP;
        }

        return buttons;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Computes per-button widths, shrinking uniformly if the row would overflow the search bar.
     */
    private static int[] computeWidths(int searchBarWidth) {
        var font = Minecraft.getInstance().font;
        int[] widths = new int[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) {
            widths[i] = Math.max(MIN_WIDTH, font.width(LABELS[i]) + LABEL_PAD * 2);
        }

        // If total row is too wide, trim each button by 2 px (down to the minimum).
        if (sum(widths) + BTN_GAP * (widths.length - 1) > searchBarWidth) {
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.max(MIN_WIDTH, widths[i] - 2);
            }
        }

        return widths;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int v : values) total += v;
        return total;
    }

    /**
     * Toggles the category state then calls
     * {@link OverlayManager#updateOverlaysAndWidgets()}, which internally re-runs
     * {@code updateQuery} and refreshes all overlay widgets (including these buttons).
     */
    private static void onToggle(SkyblockItemCategory category) {
        SkyblockCategoryState.toggle(category);
        OverlayManager.INSTANCE.updateOverlaysAndWidgets();
    }
}