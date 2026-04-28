package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

/**
 * Immutable representation of an active Kat pet-upgrade reminder.
 */
public record KatUpgradeReminder(String pet, String rarity, long readyAtMs) {
}
