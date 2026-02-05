package com.github.kd_gaming1.skyblockenhancements.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class SkyblockEnhancementsConfig extends MidnightConfig {
    public static final String SKYBLOCK_ENCHANTMENTS = "skyblock_enhancements";

    @Comment(category = SKYBLOCK_ENCHANTMENTS, centered = true)
    public static Comment text;

    @Entry(category = SKYBLOCK_ENCHANTMENTS)
    public static boolean showMissingEnchantments = true;

    @Entry(category = SKYBLOCK_ENCHANTMENTS)
    public static boolean showWhenPressingShift = true;

    @Entry(category = SKYBLOCK_ENCHANTMENTS)
    public static boolean disableCommandConfirmation = true;
}
