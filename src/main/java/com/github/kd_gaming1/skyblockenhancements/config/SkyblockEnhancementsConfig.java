package com.github.kd_gaming1.skyblockenhancements.config;

import com.github.kd_gaming1.skyblockenhancements.access.LightTextureAccessor;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;

public class SkyblockEnhancementsConfig extends MidnightConfig {
    public static final String SKYBLOCK_ENHANCEMENTS = "skyblock_enhancements";
    public static final String CHAT_ENHANCEMENTS = "chat_enhancements";
    public static final String GENERAL_ENHANCEMENTS = "general_enhancements";

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment text;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean showMissingEnchantments = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean showNotMaxedEnchantments = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean preventWeaponPlacement = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean setKatReminderForPetUpgrades = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean showWhenPressingShift = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enterToConfirmSign = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enterToConfirmAllSigns = false;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableItemGlowOutline = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean showThroughWalls = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isColor = true)
    public static String defaultGlowColor = "#ff9900";

    @Comment(category = SKYBLOCK_ENHANCEMENTS)
    public static Comment reminderSoundText;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableReminderSound = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static ReminderSoundType reminderSound = ReminderSoundType.EXPERIENCE;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 0, max = 3)
    public static double reminderSoundVolume = 1.5;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 0.5, max = 2)
    public static double reminderSoundPitch = 1.0;

    public enum ReminderSoundType {
        UI,
        BELL,
        CHIME,
        LEVEL_UP,
        EXPERIENCE,
        HARP,
        PLING,
        SUCCESS
    }

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableRecipeViewer = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 1, max = 168)
    public static int repoRefreshIntervalHours = 24;

    @Comment(category = CHAT_ENHANCEMENTS, centered = true)
    public static Comment chatEnhancementsText;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean extendedChatHistory = true;

    @Entry(category = CHAT_ENHANCEMENTS, isSlider = true, min = 100, max = 2048)
    public static int chatHistorySize = 1024;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean compactDuplicateMessages = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean onlyCompactConsecutive = false;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean centerHypixelText = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean smoothSeparators = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean enableChatTabs = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean enableChatAnimation = false;

    @Entry(category = CHAT_ENHANCEMENTS, isSlider = true, min = 50, max = 500)
    public static int chatAnimationDurationMs = 150;

    @Comment(category = GENERAL_ENHANCEMENTS, centered = true)
    public static Comment text2;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean noDoubleSneak = true;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean disableResourcePackCompatibilityWaring = true;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean disableCommandConfirmation = true;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean hideItemFrames = true;

    @Comment(category = GENERAL_ENHANCEMENTS)
    public static Comment fullbrightText;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean enableFullbright = true;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean fullbrightUseGamma = false;

    @Entry(category = GENERAL_ENHANCEMENTS, isSlider = true, min = 0, max = 100)
    public static double fullbrightStrength = 100.0;

    @Comment(category = GENERAL_ENHANCEMENTS)
    public static Comment tooltipScrollText;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean enableTooltipScroll = true;

    @Entry(category = GENERAL_ENHANCEMENTS, isSlider = true, min = 1, max = 30)
    public static int tooltipScrollSpeed = 10;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean invertTooltipScroll = false;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean enableHorizontalScroll = false;

    @Override
    public void writeChanges() {
        super.writeChanges();
        // force update lightmap to fix lightmap not updating
        // when badoptimisations light caching is enabled
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        LightTexture lt = mc.gameRenderer.lightTexture();
        if (lt instanceof LightTextureAccessor accessor) {
            accessor.skyblockenhancements$markDirty();
        }
    }
}