package com.github.kd_gaming1.skyblockenhancements.feature.slotmanage;

/** Controls when a locked item may be dropped with the in-world drop key ({@code Q}). */
public enum LockedDropMode {
    /** The lock never blocks the drop key — locked items can always be dropped with Q. */
    ALWAYS,
    /** Locked items can be dropped with Q only while in a SkyBlock dungeon; blocked elsewhere. */
    IN_DUNGEONS,
    /** The lock always blocks the drop key — locked items can never be dropped with Q. */
    NEVER
}
