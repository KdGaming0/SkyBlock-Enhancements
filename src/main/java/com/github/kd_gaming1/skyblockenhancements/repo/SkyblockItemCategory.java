package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User-facing filter categories for the RRV item list overlay. Each category maps to one or more
 * raw type strings extracted from the last lore line of NEU repo items (e.g. {@code "SWORD"},
 * {@code "DUNGEON HELMET"}).
 *
 * <p>The special categories {@link #PET} and {@link #NPC} are not derived from lore but from
 * display-name patterns and internal-name suffixes respectively.
 */
public enum SkyblockItemCategory {

    ARMOR(Set.of(
            "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
            "DUNGEON HELMET", "DUNGEON CHESTPLATE", "DUNGEON LEGGINGS", "DUNGEON BOOTS")),

    WEAPON(Set.of(
            "SWORD", "BOW", "WAND", "LONGSWORD",
            "DUNGEON SWORD", "DUNGEON BOW", "DUNGEON LONGSWORD")),

    TOOL(Set.of(
            "HOE", "AXE", "PICKAXE", "DRILL", "SHOVEL", "SHEARS",
            "FISHING ROD", "FISHING NET", "VACUUM", "WATERING CAN", "CHISEL")),

    ACCESSORY(Set.of(
            "ACCESSORY", "HATCESSORY", "BELT", "NECKLACE", "CLOAK", "GLOVES", "BRACELET",
            "DUNGEON ACCESSORY", "DUNGEON NECKLACE", "CARNIVAL MASK", "RIFT TIMECHARM")),

    PET(Set.of()),

    NPC(Set.of()),

    EQUIPMENT(Set.of(
            "DEPLOYABLE", "POWER STONE", "REFORGE STONE", "PET ITEM", "ARROW", "BAIT",
            "ROD PART", "LASSO", "TRAP", "GARDEN CHIP")),

    COSMETIC(Set.of(
            "COSMETIC", "DYE", "TRAVEL SCROLL", "PORTAL", "MEMENTO", "BOOSTER")),

    MATERIAL(Set.of(
            "GEMSTONE", "ORE", "DWARVEN METAL", "BLOCK", "SALT", "DUNGEON ITEM",
            "TROPHY FISH", "MUTATION"));

    // ── Reverse lookup ──────────────────────────────────────────────────────────

    private static final Map<String, SkyblockItemCategory> LORE_TYPE_TO_CATEGORY;

    static {
        LORE_TYPE_TO_CATEGORY = new HashMap<>();
        for (SkyblockItemCategory cat : values()) {
            for (String loreType : cat.loreTypes) {
                LORE_TYPE_TO_CATEGORY.put(loreType, cat);
            }
        }
    }

    private final Set<String> loreTypes;

    SkyblockItemCategory(Set<String> loreTypes) {
        this.loreTypes = loreTypes;
    }

    /**
     * Resolves the category for a {@link NeuItem}. Checks special-case categories (PET, NPC)
     * first, then falls back to the lore-type mapping.
     *
     * @return the matching category, or {@code null} if the item doesn't fit any bucket
     */
    public static SkyblockItemCategory fromNeuItem(NeuItem item) {
        if (isPet(item)) return PET;
        if (isNpc(item)) return NPC;

        String loreType = extractLoreType(item);
        if (loreType == null) return null;
        return LORE_TYPE_TO_CATEGORY.get(loreType);
    }

    /**
     * Case-insensitive lookup by enum name. Used for parsing the {@code %} search prefix.
     *
     * @return the matching category, or {@code null} if no match
     */
    public static SkyblockItemCategory fromName(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────────

    private static boolean isPet(NeuItem item) {
        return item.displayName != null && item.displayName.contains("[Lvl ");
    }

    private static boolean isNpc(NeuItem item) {
        return item.internalName != null && item.internalName.endsWith("_NPC");
    }

    /**
     * Extracts the item type from the last lore line. NEU items use the format
     * {@code "§<rarity_color>§l<RARITY> <TYPE>"} — e.g. {@code "§6§lLEGENDARY SWORD"}.
     * Returns the uppercase type portion ({@code "SWORD"}), or {@code null} if the lore
     * doesn't follow this pattern.
     */
    static String extractLoreType(NeuItem item) {
        if (item.lore == null || item.lore.isEmpty()) return null;

        String lastLine = item.lore.getLast();
        // Strip all section-sign formatting codes (§ followed by any character)
        String clean = lastLine.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return null;

        // The first word is the rarity, everything after is the type
        int space = clean.indexOf(' ');
        if (space < 0) return null; // Rarity-only line (e.g. pets, materials)

        // Handle "SPECIAL a" anomaly — treat as just the first word after rarity
        String type = clean.substring(space + 1).trim();
        if (type.isEmpty()) return null;

        // Normalize: some types contain trailing lowercase junk (e.g. "SPECIAL a")
        if (type.chars().anyMatch(Character::isLowerCase)) {
            // Only keep the uppercase portion at the start
            int end = 0;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (Character.isUpperCase(c) || c == ' ') {
                    end = i + 1;
                } else {
                    break;
                }
            }
            type = type.substring(0, end).trim();
        }

        return type.isEmpty() ? null : type;
    }
}