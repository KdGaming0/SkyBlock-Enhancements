package com.github.kd_gaming1.skyblockenhancements.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * SkyBlock item rarity, in ascending order so {@link #ordinal()} doubles as a severity rank
 * ({@code COMMON} lowest, {@code ADMIN} highest). This lets features compare rarities with a simple
 * threshold (e.g. "block dropping anything at or above {@code RARE}").
 */
public enum ItemRarity {
    COMMON("COMMON", "Common"),
    UNCOMMON("UNCOMMON", "Uncommon"),
    RARE("RARE", "Rare"),
    EPIC("EPIC", "Epic"),
    LEGENDARY("LEGENDARY", "Legendary"),
    MYTHIC("MYTHIC", "Mythic"),
    DIVINE("DIVINE", "Divine"),
    SPECIAL("SPECIAL", "Special"),
    VERY_SPECIAL("VERY SPECIAL", "Very Special"),
    ULTIMATE("ULTIMATE", "Ultimate"),
    ADMIN("ADMIN", "Admin");

    private final String keyword;
    private final String displayName;

    ItemRarity(String keyword, String displayName) {
        this.keyword = keyword;
        this.displayName = displayName;
    }

    /** The uppercase lore keyword that identifies this rarity. */
    public String keyword() {
        return keyword;
    }

    /** Human-readable name for feedback messages (e.g. {@code "Very Special"}). */
    public String displayName() {
        return displayName;
    }

    /**
     * Keywords tested longest-first so a shorter keyword can't shadow a longer one that contains it
     * — {@code UNCOMMON} must win over {@code COMMON}, and {@code VERY SPECIAL} over {@code SPECIAL}.
     */
    private static final ItemRarity[] BY_KEYWORD_LENGTH_DESC =
            Arrays.stream(values())
                    .sorted(Comparator.comparingInt((ItemRarity r) -> r.keyword.length()).reversed())
                    .toArray(ItemRarity[]::new);

    /**
     * Scans lore bottom-up (rarity lives at the bottom) and returns the first recognised rarity,
     * or empty if none of the lines carry a known rarity keyword.
     */
    public static Optional<ItemRarity> fromLore(List<Component> lines) {
        if (lines == null) return Optional.empty();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).getString().toUpperCase(Locale.ROOT);
            for (ItemRarity rarity : BY_KEYWORD_LENGTH_DESC) {
                if (line.contains(rarity.keyword)) {
                    return Optional.of(rarity);
                }
            }
        }
        return Optional.empty();
    }

    /** Resolves an item's rarity from its lore component, or empty if unknown. */
    public static Optional<ItemRarity> fromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore != null ? fromLore(lore.lines()) : Optional.empty();
    }
}
