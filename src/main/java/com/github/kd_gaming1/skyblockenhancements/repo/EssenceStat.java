package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Catalog of Hypixel stats that can appear on an essence upgrade.
 *
 * <p>The Hypixel API uses two casings for stat names: {@code tiered_stats} keys are
 * SCREAMING_SNAKE ({@code "CRITICAL_DAMAGE"}) while {@code stats} keys are lower_snake
 * ({@code "critical_damage"}). Both resolve to the same entry here.
 *
 * <p>Display labels match SkyBlock in-game wording (e.g. {@code "Crit Damage"}) and are
 * used to render the before/after lore on essence upgrade recipe items.
 */
public enum EssenceStat {

    DAMAGE          ("Damage",              "§c", "+"),
    STRENGTH        ("Strength",            "§c", "+"),
    DEFENSE         ("Defense",             "§a", "+"),
    HEALTH          ("Health",              "§a", "+"),
    INTELLIGENCE    ("Intelligence",        "§b", "+"),
    CRITICAL_DAMAGE ("Crit Damage",         "§c", "+"),
    CRITICAL_CHANCE ("Crit Chance",         "§c", "+", "%"),
    WALK_SPEED      ("Speed",               "§f", "+"),
    ATTACK_SPEED    ("Bonus Attack Speed",  "§c", "+", "%"),
    FEROCITY        ("Ferocity",            "§c", "+"),
    MAGIC_FIND      ("Magic Find",          "§a", "+"),
    PET_LUCK        ("Pet Luck",            "§a", "+"),
    TRUE_DEFENSE    ("True Defense",        "§f", "+"),
    SEA_CREATURE_CHANCE ("Sea Creature Chance", "§3", "+", "%"),
    MINING_FORTUNE  ("Mining Fortune",      "§6", "+"),
    FARMING_FORTUNE ("Farming Fortune",     "§6", "+"),
    FORAGING_FORTUNE("Foraging Fortune",    "§6", "+"),
    MINING_SPEED    ("Mining Speed",        "§6", "+"),
    ABILITY_DAMAGE  ("Ability Damage",      "§c", "+", "%");

    private final String displayLabel;
    private final String valueColor;
    private final String prefix;
    private final String suffix;

    EssenceStat(String displayLabel, String valueColor, String prefix) {
        this(displayLabel, valueColor, prefix, "");
    }

    EssenceStat(String displayLabel, String valueColor, String prefix, String suffix) {
        this.displayLabel = displayLabel;
        this.valueColor   = valueColor;
        this.prefix       = prefix;
        this.suffix       = suffix;
    }

    public String displayLabel() { return displayLabel; }
    public String valueColor()   { return valueColor; }
    public String prefix()       { return prefix; }
    public String suffix()       { return suffix; }

    // ── Lookup ─────────────────────────────────────────────────────────────────

    /** Pre-built lookup that accepts both API casings (UPPER and lower). */
    private static final Map<String, EssenceStat> BY_KEY;
    static {
        Map<String, EssenceStat> map = new HashMap<>(values().length * 2);
        for (EssenceStat s : values()) {
            map.put(s.name(),                         s); // UPPER_SNAKE
            map.put(s.name().toLowerCase(Locale.ROOT), s); // lower_snake
        }
        BY_KEY = Map.copyOf(map);
    }

    /**
     * Resolves a Hypixel API stat key (any casing) to an {@link EssenceStat}, or
     * {@code null} if we don't model that stat for display.
     */
    @Nullable
    public static EssenceStat fromKey(String apiKey) {
        if (apiKey == null) return null;
        return BY_KEY.get(apiKey);
    }
}