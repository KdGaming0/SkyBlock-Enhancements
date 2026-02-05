package com.github.kd_gaming1.skyblockenhancements.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class SkyblockEnhancementsConfig extends MidnightConfig {
    public static final String SKYBLOCK_ENHANCEMENTS = "skyblock_enhancements";
    public static final String GENERAL_ENHANCEMENTS = "general_enhancements";

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment text;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean showMissingEnchantments = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean showWhenPressingShift = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean disableCommandConfirmation = true;

    @Comment(category = GENERAL_ENHANCEMENTS)
    public static Comment text2;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean noDoubleSneak = true;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean disableResourcePackCompatibilityWaring = true;
}
