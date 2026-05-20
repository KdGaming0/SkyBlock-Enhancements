package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * Parses slot references of the form {@code "INTERNAL_NAME"} or {@code "INTERNAL_NAME:count"}
 * into {@link SlotContent} instances. Shared by every recipe parser so the null/empty/count
 * handling is defined in exactly one place.
 */
public final class SlotRefParser {

    /** Cached empty-slot instance — avoids allocating a new SlotContent per empty grid cell. */
    private static final SlotContent EMPTY_SLOT = SlotContent.of(ItemStack.EMPTY);

    private SlotRefParser() {}

    /**
     * Parses a slot reference string (e.g. {@code "ENCHANTED_DIAMOND:64"}) into a
     * {@link SlotContent}. Returns a shared empty-slot sentinel for {@code null} or empty refs.
     *
     * <p>Count parsing is resilient to both integer ({@code "ITEM:2"}) and decimal
     * ({@code "ITEM:2.0"}) formatting, which the NEU repo uses inconsistently.
     */
    public static SlotContent parse(String ref) {
        if (ref == null || ref.isEmpty()) return EMPTY_SLOT;

        int colon = ref.indexOf(':');
        if (colon < 0) {
            return SlotContent.of(ItemStackBuilder.buildIngredient(ref, 1));
        }

        String id = ref.substring(0, colon);
        int count = parseCount(ref.substring(colon + 1), ref);
        return SlotContent.of(ItemStackBuilder.buildIngredient(id, count));
    }

    /**
     * Parses a count suffix, handling both integer and decimal NEU repo formats.
     * Fast-paths integers to avoid {@code Double.parseDouble} overhead for the common case.
     * Falls back to 1 and logs on genuinely malformed input.
     */
    private static int parseCount(String countStr, String fullRef) {
        if (countStr.isEmpty()) return 1;

        try {
            int count = (countStr.indexOf('.') >= 0)
                    ? (int) Double.parseDouble(countStr)
                    : Integer.parseInt(countStr);
            return Math.max(1, count);
        } catch (NumberFormatException e) {
            LOGGER.debug("Malformed ingredient count '{}' in '{}', defaulting to 1", countStr, fullRef);
            return 1;
        }
    }

    /** Returns the shared empty-slot sentinel for callers building slot arrays. */
    public static SlotContent empty() {
        return EMPTY_SLOT;
    }
}