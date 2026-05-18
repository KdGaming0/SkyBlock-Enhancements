package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps {@link NeuItem} lore types to the {@code itemTypes} strings used in
 * {@code reforges.json} and {@code reforgestones.json}.
 */
public final class ReforgeTypeResolver {

    private ReforgeTypeResolver() {}

    /**
     * Maps a lore type (e.g. {@code "SWORD"}) to the reforge category strings it belongs to.
     * An item may match multiple categories (e.g. a sword matches both {@code "SWORD"} stones
     * and {@code "SWORD/ROD"} blacksmith reforges).
     */
    private static final Map<String, List<String>> LORE_TYPE_TO_REFORGE_TYPES = Map.ofEntries(
            Map.entry("SWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("DUNGEON SWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("LONGSWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("DUNGEON LONGSWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("BOW", List.of("BOW")),
            Map.entry("DUNGEON BOW", List.of("BOW")),
            Map.entry("HELMET", List.of("ARMOR", "HELMET")),
            Map.entry("CHESTPLATE", List.of("ARMOR", "CHESTPLATE")),
            Map.entry("LEGGINGS", List.of("ARMOR")),
            Map.entry("BOOTS", List.of("ARMOR")),
            Map.entry("DUNGEON HELMET", List.of("ARMOR", "HELMET")),
            Map.entry("DUNGEON CHESTPLATE", List.of("ARMOR", "CHESTPLATE")),
            Map.entry("DUNGEON LEGGINGS", List.of("ARMOR")),
            Map.entry("DUNGEON BOOTS", List.of("ARMOR")),
            Map.entry("PICKAXE", List.of("PICKAXE")),
            Map.entry("DRILL", List.of("PICKAXE")),
            Map.entry("HOE", List.of("FARMING_TOOL", "HOE")),
            Map.entry("SHOVEL", List.of("FARMING_TOOL")),
            Map.entry("SHEARS", List.of("FARMING_TOOL")),
            Map.entry("AXE", List.of("AXE")),
            Map.entry("ACCESSORY", List.of("EQUIPMENT")),
            Map.entry("HATCESSORY", List.of("EQUIPMENT")),
            Map.entry("BELT", List.of("EQUIPMENT", "BELT")),
            Map.entry("NECKLACE", List.of("EQUIPMENT")),
            Map.entry("CLOAK", List.of("EQUIPMENT", "CLOAK")),
            Map.entry("GLOVES", List.of("EQUIPMENT")),
            Map.entry("BRACELET", List.of("EQUIPMENT")),
            Map.entry("DUNGEON ACCESSORY", List.of("EQUIPMENT")),
            Map.entry("DUNGEON NECKLACE", List.of("EQUIPMENT")),
            Map.entry("DUNGEON BELT", List.of("EQUIPMENT", "BELT")),
            Map.entry("DUNGEON CLOAK", List.of("EQUIPMENT", "CLOAK")),
            Map.entry("DUNGEON GLOVES", List.of("EQUIPMENT")),
            Map.entry("CARNIVAL MASK", List.of("EQUIPMENT")),
            Map.entry("RIFT TIMECHARM", List.of("EQUIPMENT")),
            Map.entry("FISHING ROD", List.of("ROD", "SWORD/ROD")),
            Map.entry("FISHING NET", List.of("ROD", "SWORD/ROD")),
            Map.entry("VACUUM", List.of("VACUUM")),
            Map.entry("WATERING CAN", List.of("FARMING_TOOL")),
            Map.entry("CHISEL", List.of("PICKAXE"))
    );

    /** Reverse index: reforge type → lore types. Built once for O(1) lookups. */
    private static final Map<String, List<String>> REFORGE_TYPE_TO_LORE_TYPES = buildReverseIndex();

    private static Map<String, List<String>> buildReverseIndex() {
        Map<String, List<String>> map = new java.util.HashMap<>();
        for (var entry : LORE_TYPE_TO_REFORGE_TYPES.entrySet()) {
            String loreType = entry.getKey();
            for (String reforgeType : entry.getValue()) {
                map.computeIfAbsent(reforgeType, k -> new java.util.ArrayList<>()).add(loreType);
            }
        }
        // Defensive copy to immutable lists
        Map<String, List<String>> result = new java.util.HashMap<>(map.size());
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Returns the list of reforge type strings applicable to the given item.
     * Returns an empty list if the item is not reforgable.
     */
    public static List<String> resolve(NeuItem item) {
        String loreType = item.loreType != null ? item.loreType : SkyblockItemCategory.extractLoreType(item);
        if (loreType == null) return List.of();
        return LORE_TYPE_TO_REFORGE_TYPES.getOrDefault(loreType, List.of());
    }

    /**
     * Returns the lore types that map to a given reforge type string.
     * For example, {@code "ARMOR"} returns {@code ["HELMET", "CHESTPLATE", ...]}.
     */
    public static List<String> getLoreTypesForReforgeType(String reforgeType) {
        return REFORGE_TYPE_TO_LORE_TYPES.getOrDefault(reforgeType, List.of());
    }
}
