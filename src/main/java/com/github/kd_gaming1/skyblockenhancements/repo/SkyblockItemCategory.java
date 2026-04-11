package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * User-facing filter categories for the RRV item list overlay. Each category maps to one or more
 * raw type strings extracted from the last lore line of NEU repo items (e.g. {@code "SWORD"},
 * {@code "DUNGEON HELMET"}).
 *
 * <p>Categories are resolved in priority order:
 * <ol>
 *   <li>Structural checks — itemId, internalName pattern, displayName pattern (most reliable)</li>
 *   <li>Lore-type lookup — the {@code RARITY TYPE} suffix from the last lore line</li>
 *   <li>Catch-all — {@link #MISC} for items with lore but no matched type</li>
 * </ol>
 *
 * <p>Each category optionally carries a {@link #spriteName} for the button bar. Categories with
 * a {@code null} sprite (e.g. {@link #NPC}) are hidden from the button bar but still usable via
 * the {@code %CATEGORY} search prefix.
 */
public enum SkyblockItemCategory {

    ARMOR(Set.of(
            "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
            "DUNGEON HELMET", "DUNGEON CHESTPLATE", "DUNGEON LEGGINGS", "DUNGEON BOOTS"),
            "armour"),

    WEAPON(Set.of(
            "SWORD", "BOW", "WAND", "LONGSWORD",
            "DUNGEON SWORD", "DUNGEON BOW", "DUNGEON LONGSWORD"),
            "weaponry"),

    TOOL(Set.of(
            "HOE", "AXE", "PICKAXE", "DRILL", "SHOVEL", "SHEARS",
            "FISHING ROD", "FISHING NET", "VACUUM", "WATERING CAN", "CHISEL"),
            "tools"),

    ACCESSORY(Set.of(
            "ACCESSORY", "HATCESSORY",
            "BELT", "NECKLACE", "CLOAK", "GLOVES", "BRACELET",
            "DUNGEON ACCESSORY", "DUNGEON NECKLACE", "DUNGEON BELT",
            "DUNGEON CLOAK", "DUNGEON GLOVES",
            "CARNIVAL MASK", "RIFT TIMECHARM"),
            "accessories"),

    PET(Set.of(), "pets"),

    NPC(Set.of(), "npcs"),

    ENCHANTMENT(Set.of(), "enchants"),

    MINION(Set.of(), "minions"),

    POTION(Set.of(), null),

    EQUIPMENT(Set.of(
            "DEPLOYABLE", "POWER STONE", "REFORGE STONE", "PET ITEM",
            "ARROW", "ARROW POISON", "BAIT",
            "ROD PART", "LASSO", "TRAP", "GARDEN CHIP"),
            "equipment"),

    COSMETIC(Set.of(
            "COSMETIC", "DYE", "TRAVEL SCROLL", "PORTAL", "MEMENTO", "BOOSTER"),
            null),

    MATERIAL(Set.of(
            "GEMSTONE", "ORE", "DWARVEN METAL", "BLOCK", "SALT", "DUNGEON ITEM",
            "TROPHY FISH", "MUTATION",
            "COMBAT SHARD", "WATER SHARD", "FOREST SHARD"),
            "materials"),

    /** Catch-all for items with lore but no matched type (bare-rarity materials, blocks, etc.). */
    MISC(Set.of(), null);

    // ── Reverse lookup ──────────────────────────────────────────────────────────

    /**
     * Maps every known lore-type string to its category. Built once from all enum values'
     * {@link #loreTypes} sets. Also serves as a validation whitelist — extracted types not
     * present in this map are treated as unrecognized and discarded.
     */
    private static final Map<String, SkyblockItemCategory> LORE_TYPE_TO_CATEGORY;

    static {
        Map<String, SkyblockItemCategory> map = new HashMap<>();
        for (SkyblockItemCategory cat : values()) {
            for (String loreType : cat.loreTypes) {
                map.put(loreType, cat);
            }
        }
        LORE_TYPE_TO_CATEGORY = Collections.unmodifiableMap(map);
    }

    /** Pre-compiled pattern for minion internal names (e.g. {@code ACACIA_GENERATOR_11}). */
    private static final Pattern GENERATOR_PATTERN = Pattern.compile(".*_GENERATOR_\\d+");

    private final Set<String> loreTypes;
    @Nullable private final String spriteName;

    SkyblockItemCategory(Set<String> loreTypes, @Nullable String spriteName) {
        this.loreTypes = loreTypes;
        this.spriteName = spriteName;
    }

    /**
     * Returns the sprite base name for the button bar, or {@code null} if this category
     * should not appear as a button.
     */
    @Nullable
    public String getSpriteName() {
        return spriteName;
    }

    /**
     * All categories that have a sprite and should appear in the button bar.
     * Order matches enum declaration order.
     */
    public static final List<SkyblockItemCategory> BUTTON_CATEGORIES =
            Arrays.stream(values())
                    .filter(c -> c.spriteName != null)
                    .toList();

    // ── Category resolution ─────────────────────────────────────────────────────

    /**
     * Resolves the category for a {@link NeuItem} using a prioritized detection pipeline:
     * structural checks first (most reliable), then lore-type lookup, then catch-all.
     *
     * @return the matching category, or {@code null} if the item has no lore at all
     */
    public static SkyblockItemCategory fromNeuItem(NeuItem item) {
        // Priority 1: structural checks — reliable, independent of lore format
        if (isPet(item)) return PET;
        if (isNpc(item)) return NPC;
        if (isEnchantedBook(item)) return ENCHANTMENT;
        if (isMinion(item)) return MINION;
        if (isPotion(item)) return POTION;

        // Priority 2: lore-type lookup from last lore line
        String loreType = extractLoreType(item);
        if (loreType != null) {
            SkyblockItemCategory mapped = LORE_TYPE_TO_CATEGORY.get(loreType);
            if (mapped != null) return mapped;
        }

        // Priority 3: catch-all for items that have lore but no recognized type
        if (item.lore != null && !item.lore.isEmpty()) return MISC;

        return null;
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

    // ── Structural detectors ─────────────────────────────────────────────────────

    private static boolean isPet(NeuItem item) {
        return item.displayName != null && item.displayName.contains("[Lvl ");
    }

    private static boolean isNpc(NeuItem item) {
        return item.internalName != null && item.internalName.endsWith("_NPC");
    }

    private static boolean isEnchantedBook(NeuItem item) {
        return "minecraft:enchanted_book".equals(item.itemId);
    }

    private static boolean isMinion(NeuItem item) {
        if (item.internalName == null) return false;
        // Fast pre-check before compiling regex match
        if (!item.internalName.contains("_GENERATOR_")) return false;
        return GENERATOR_PATTERN.matcher(item.internalName).matches();
    }

    private static boolean isPotion(NeuItem item) {
        if ("minecraft:potion".equals(item.itemId)) return true;
        return "viewpotion".equals(item.clickcommand);
    }

    // ── Lore-type extraction ─────────────────────────────────────────────────────

    /**
     * Extracts the item type from the last lore line. NEU items use the format
     * {@code "§<rarity_color>§l<RARITY> <TYPE>"} — e.g. {@code "§6§lLEGENDARY SWORD"}.
     * Returns the uppercase type portion ({@code "SWORD"}), or {@code null} if the lore
     * doesn't follow this pattern or yields an unrecognized type.
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

        String type = clean.substring(space + 1).trim();
        if (type.isEmpty()) return null;

        // Normalize: some types contain trailing lowercase junk (e.g. "SPECIAL a")
        if (type.chars().anyMatch(Character::isLowerCase)) {
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

        if (type.isEmpty()) return null;

        if (!LORE_TYPE_TO_CATEGORY.containsKey(type)) return null;

        return type;
    }

    /**
     * Extracts the Skyblock rarity from the last lore line.
     * The lore format is {@code "§<color>§l<RARITY> [TYPE]"} — e.g. {@code "§6§lLEGENDARY SWORD"}.
     *
     * @return the parsed rarity, or {@code null} if none could be extracted
     */
    @Nullable
    public static SkyblockRarity extractRarity(NeuItem item) {
        if (item.lore == null || item.lore.isEmpty()) return null;

        String lastLine = item.lore.getLast();
        String clean = lastLine.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return null;

        // First word is always the rarity (may be the only word for bare-rarity items)
        int space = clean.indexOf(' ');
        String rarityStr = space >= 0 ? clean.substring(0, space) : clean;

        // "VERY SPECIAL" is two words — handle it
        if ("VERY".equals(rarityStr) && space >= 0) {
            String rest = clean.substring(space + 1).trim();
            if (rest.startsWith("SPECIAL")) {
                rarityStr = "VERY_SPECIAL";
            }
        }

        try {
            return SkyblockRarity.valueOf(rarityStr);
        } catch (IllegalArgumentException e) {
            return null; // Unrecognized rarity string
        }
    }
}