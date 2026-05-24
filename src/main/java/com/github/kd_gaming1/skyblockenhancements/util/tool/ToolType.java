package com.github.kd_gaming1.skyblockenhancements.util.tool;

import java.util.*;

/**
 * Categories of tools in Hypixel SkyBlock.
 *
 * <p>Detection is driven by pattern matching on the item's SkyBlock ID
 * (e.g. {@code "TITANIUM_PICKAXE"} contains {@code "PICKAXE"} →
 * {@link #PICKAXE}) with the display name as a fallback for non-SkyBlock
 * or custom items.
 *
 * <p>To check if the player is holding a tool of a specific type:
 * <pre>{@code
 *   if (ToolDetector.isHolding(ToolType.PICKAXE)) { ... }
 * }</pre>
 */
public enum ToolType {

    // ── Mining tools ──────────────────────────────────────────────────────────

    PICKAXE(
            "pickaxe",
            new String[]{"PICKAXE"},
            new String[]{"Pickaxe", "Pick"}
    ),

    DRILL(
            "drill",
            new String[]{"DRILL"},
            new String[]{"Drill"}
    ),

    CHISEL(
            "chisel",
            new String[]{"CHISEL"},
            new String[]{"Chisel"}
    ),

    /** Gauntlets that function as both pickaxe and weapon. */
    GAUNTLET(
            "gauntlet",
            new String[]{"GAUNTLET"},
            new String[]{"Gauntlet"}
    ),

    // ── Farming tools ─────────────────────────────────────────────────────────

    HOE(
            "hoe",
            new String[]{"HOE", "DICER", "CHOPPER", "KNIFE"},
            new String[]{"Hoe", "Dicer", "Chopper", "Knife"}
    ),

    FARMING_AXE(
            "farming_axe",
            new String[]{"GARDENING_AXE"},
            new String[]{"Gardening Axe"}
    ),

    WATERING_CAN(
            "watering_can",
            new String[]{"WATERING_CAN", "HYDROCAN", "AQUAMASTER"},
            new String[]{"Watering Can", "HydroCan", "AquaMaster"}
    ),

    // ── Combat / other tools ──────────────────────────────────────────────────

    FISHING_ROD(
            "fishing_rod",
            new String[]{"ROD"},
            new String[]{"Rod", "Fishing"}
    ),

    WAND(
            "wand",
            new String[]{"WAND", "STAFF"},
            new String[]{"Wand", "Staff", "Sceptre"}
    ),

    SHOVEL(
            "shovel",
            new String[]{"SHOVEL"},
            new String[]{"Shovel"}
    ),

    /** Items that don't match any known category. */
    UNKNOWN("unknown", new String[0], new String[0]);

    // ── Internal ──────────────────────────────────────────────────────────────

    private final String key;
    private final String[] idFragments;
    private final String[] nameFragments;

    ToolType(String key, String[] idFragments, String[] nameFragments) {
        this.key = key;
        this.idFragments = idFragments;
        this.nameFragments = nameFragments;
    }

    /** Canonical key used in configs. */
    public String key() {
        return key;
    }

    /**
     * Returns {@code true} if the given SkyBlock ID (uppercase) contains any
     * of this tool type's identifying fragments.
     * E.g. "TITANIUM_PICKAXE" contains "PICKAXE" → matches {@link #PICKAXE}.
     */
    boolean matchesId(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        for (String frag : idFragments) {
            if (skyblockId.contains(frag)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the given display name contains any of this
     * tool type's identifying name fragments. Used as a fallback when the
     * SkyBlock ID is unavailable.
     */
    boolean matchesName(String displayName) {
        if (displayName == null || displayName.isEmpty()) return false;
        String upper = displayName.toUpperCase(Locale.ROOT);
        for (String frag : nameFragments) {
            if (upper.contains(frag.toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Returns all tool types except {@link #UNKNOWN}. */
    public static List<ToolType> knownValues() {
        List<ToolType> list = new ArrayList<>(List.of(values()));
        list.remove(UNKNOWN);
        return Collections.unmodifiableList(list);
    }
}
