package com.github.kd_gaming1.skyblockenhancements.repo.neu;

/**
 * Skyblock item rarities in ascending power order.
 * Used as a sort key for the RRV item list.
 */
public enum SkyblockRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC,
    DIVINE,
    SPECIAL,
    VERY_SPECIAL,
    SUPREME

    // Ordinal is used directly as a sort key (lower = weaker).
}