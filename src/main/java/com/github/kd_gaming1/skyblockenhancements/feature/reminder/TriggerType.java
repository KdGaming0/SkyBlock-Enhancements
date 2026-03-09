package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

/**
 * Defines when a reminder's countdown advances.
 */
public enum TriggerType {
    /** Countdown only ticks while the player is in-game. */
    WHILE_PLAYING,
    /** Countdown runs against wall-clock time, regardless of game state. */
    REAL_TIME
}