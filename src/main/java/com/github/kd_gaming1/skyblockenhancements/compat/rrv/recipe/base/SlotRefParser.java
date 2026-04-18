package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

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
     */
    public static SlotContent parse(String ref) {
        if (ref == null || ref.isEmpty()) return EMPTY_SLOT;

        int colon = ref.indexOf(':');
        if (colon < 0) {
            return SlotContent.of(ItemStackBuilder.buildIngredient(ref, 1));
        }

        String id = ref.substring(0, colon);
        int count = 1;
        try {
            count = Integer.parseInt(ref.substring(colon + 1));
        } catch (NumberFormatException ignored) {
            // malformed count → default to 1
        }
        return SlotContent.of(ItemStackBuilder.buildIngredient(id, count));
    }

    /** Returns the shared empty-slot sentinel for callers building slot arrays. */
    public static SlotContent empty() {
        return EMPTY_SLOT;
    }
}