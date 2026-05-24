package com.github.kd_gaming1.skyblockenhancements.util.tool;

import com.github.kd_gaming1.skyblockenhancements.util.StatValueType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Stats that can be extracted from a held tool's lore in Hypixel SkyBlock.
 * Ordered by relevance. Max 64 stats to fit inside the bitmask optimization.
 */
public enum ToolStat {

    // ── Combat ───────────────────────────────────────────────────────────────
    DAMAGE("damage", StatValueType.INT, "Damage"),
    STRENGTH("strength", StatValueType.INT, "Strength"),
    CRIT_CHANCE("crit_chance", StatValueType.FLOAT, "Crit Chance"),
    CRIT_DAMAGE("crit_damage", StatValueType.FLOAT, "Crit Damage"),
    ATTACK_SPEED("attack_speed", StatValueType.FLOAT, "Bonus Attack Speed", "Attack Speed"),
    HEALTH("health", StatValueType.INT, "Health"),
    DEFENSE("defense", StatValueType.INT, "Defense"),
    SPEED("speed", StatValueType.INT, "Speed"),
    INTELLIGENCE("intelligence", StatValueType.INT, "Intelligence"),
    TRUE_DEFENSE("true_defense", StatValueType.INT, "True Defense"),
    MAGIC_FIND("magic_find", StatValueType.INT, "Magic Find"),
    PET_LUCK("pet_luck", StatValueType.INT, "Pet Luck"),
    SEA_CREATURE_CHANCE("sea_creature_chance", StatValueType.FLOAT, "Sea Creature Chance"),
    FEROCITY("ferocity", StatValueType.INT, "Ferocity"),
    ABILITY_DAMAGE("ability_damage", StatValueType.FLOAT, "Ability Damage"),
    VITALITY("vitality", StatValueType.INT, "Vitality"),
    MENDING("mending", StatValueType.INT, "Mending"),
    HEALTH_REGEN("health_regen", StatValueType.INT, "Health Regen"),
    SWING_RANGE("swing_range", StatValueType.FLOAT, "Swing Range"),

    // ── Gathering ────────────────────────────────────────────────────────────
    MINING_SPEED("mining_speed", StatValueType.INT, "Mining Speed"),
    MINING_FORTUNE("mining_fortune", StatValueType.INT, "Mining Fortune", "Ore Fortune"),
    BLOCK_FORTUNE("block_fortune", StatValueType.INT, "Block Fortune"),
    GEMSTONE_FORTUNE("gemstone_fortune", StatValueType.INT, "Gemstone Fortune"),
    FARMING_FORTUNE("farming_fortune", StatValueType.INT, "Farming Fortune"),
    FORAGING_FORTUNE("foraging_fortune", StatValueType.INT, "Foraging Fortune"),
    GLOBAL_FORTUNE("global_fortune", StatValueType.FLOAT, "Global Fortune"),
    BREAKING_POWER("breaking_power", StatValueType.INT, "Breaking Power", "BP"),
    PRISTINE("pristine", StatValueType.FLOAT, "Pristine"),
    MINING_SPREAD("mining_spread", StatValueType.INT, "Mining Spread"),

    // ── Wisdom ───────────────────────────────────────────────────────────────
    COMBAT_WISDOM("combat_wisdom", StatValueType.FLOAT, "Combat Wisdom"),
    MINING_WISDOM("mining_wisdom", StatValueType.FLOAT, "Mining Wisdom"),
    FARMING_WISDOM("farming_wisdom", StatValueType.FLOAT, "Farming Wisdom"),
    FORAGING_WISDOM("foraging_wisdom", StatValueType.FLOAT, "Foraging Wisdom"),
    FISHING_WISDOM("fishing_wisdom", StatValueType.FLOAT, "Fishing Wisdom"),
    ENCHANTING_WISDOM("enchanting_wisdom", StatValueType.FLOAT, "Enchanting Wisdom"),
    ALCHEMY_WISDOM("alchemy_wisdom", StatValueType.FLOAT, "Alchemy Wisdom");

    // Cache values to avoid allocating a new array on ToolStat.values()
    public static final ToolStat[] VALUES = values();

    private final String key;
    private final StatValueType valueType;
    final String[] loreLabels;

    ToolStat(String key, StatValueType valueType, String... loreLabels) {
        this.key = key;
        this.valueType = valueType;
        this.loreLabels = loreLabels;
    }

    public String key() {
        return key;
    }

    public StatValueType valueType() {
        return valueType;
    }

    public static Optional<ToolStat> byKey(String key) {
        for (ToolStat stat : VALUES) {
            if (stat.key.equals(key)) return Optional.of(stat);
        }
        return Optional.empty();
    }

    public static List<ToolStat> byType(StatValueType type) {
        List<ToolStat> list = new ArrayList<>();
        for (ToolStat stat : VALUES) {
            if (stat.valueType == type) list.add(stat);
        }
        return Collections.unmodifiableList(list);
    }
}