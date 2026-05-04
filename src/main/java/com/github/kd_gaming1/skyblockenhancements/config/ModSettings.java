package com.github.kd_gaming1.skyblockenhancements.config;

/**
 * Abstraction over the mod's configuration so services can be instantiated
 * with settings instead of reading static fields directly.
 *
 * <p>This is intentionally narrow for the pilot conversion (pricing + reminders).
 * Expand the interface incrementally as more services are decoupled.
 */
public interface ModSettings {

    // ── Pricing ────────────────────────────────────────────────────────────────

    boolean enablePriceTooltips();

    int priceRefreshIntervalMinutes();

    // ── Reminder sound ─────────────────────────────────────────────────────────

    boolean enableReminderSound();

    SkyblockEnhancementsConfig.ReminderSoundType reminderSound();

    double reminderSoundVolume();

    double reminderSoundPitch();

    // ── Tooltip price formatting ───────────────────────────────────────────────

    boolean enablePriceTickerText();

    boolean showBazaarBuySell();

    boolean showBazaarSpread();
}
