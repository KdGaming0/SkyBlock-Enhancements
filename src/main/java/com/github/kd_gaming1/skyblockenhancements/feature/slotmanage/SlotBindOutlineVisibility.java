package com.github.kd_gaming1.skyblockenhancements.feature.slotmanage;

/** Controls when the coloured outlines on bound slots are drawn. */
public enum SlotBindOutlineVisibility {
    /** Always show outlines on bound slots. */
    ALWAYS,
    /** Only while the Slot Edit key or Shift is held. */
    ACTIVE_ONLY,
    /** Never show bound-slot outlines (the pending-source highlight still shows while editing). */
    HIDDEN
}
