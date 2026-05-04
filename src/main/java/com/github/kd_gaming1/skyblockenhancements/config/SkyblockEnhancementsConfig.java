package com.github.kd_gaming1.skyblockenhancements.config;

import com.github.kd_gaming1.skyblockenhancements.access.LightTextureAccessor;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;

/**
 * MidnightConfig-based configuration for Skyblock Enhancements.
 *
 * <p>All options are {@code public static} and annotated with {@link Entry}
 * or {@link Comment} so MidnightLib recognises them. The class implements
 * {@link ModSettings} so services receive a narrow interface instead of
 * touching static fields directly.
 */
public class SkyblockEnhancementsConfig extends MidnightConfig implements ModSettings {

    // ── Category IDs ────────────────────────────────────────────────────────────

    public static final String SKYBLOCK_ENHANCEMENTS = "skyblock_enhancements";
    public static final String RRV_INTEGRATION       = "rrv_integration";
    public static final String CHAT_ENHANCEMENTS     = "chat_enhancements";
    public static final String TOOLTIP_ENHANCEMENTS  = "tooltip_enhancements";
    public static final String GENERAL_ENHANCEMENTS  = "general_enhancements";
    public static final String DEV_TOOLS             = "dev_tools";

    // ═══════════════════════════════════════════════════════════════════════════
    //  Skyblock Enhancements
    // ═══════════════════════════════════════════════════════════════════════════

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment text;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean preventWeaponPlacement = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean hideCheapCoins = false;

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
        UI, BELL, CHIME, LEVEL_UP, EXPERIENCE, HARP, PLING, SUCCESS
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  RRV Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Entry(category = RRV_INTEGRATION)
    public static boolean enableRecipeViewer = true;

    @Entry(category = RRV_INTEGRATION, isSlider = true, min = 5, max = 512, precision = 1)
    public static int repoRefreshCheckMinutes = 15;

    @Entry(category = RRV_INTEGRATION)
    public static boolean compactItemList = true;

    @Entry(category = RRV_INTEGRATION)
    public static boolean enableRecipeDiagnostics = false;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Chat Enhancements
    // ═══════════════════════════════════════════════════════════════════════════

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

    @Entry(category = CHAT_ENHANCEMENTS, isSlider = true, min = 0, max = 60)
    public static int compactTimeWindowMinutes = 10;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean centerHypixelText = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean smoothSeparators = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean enableChatTabs = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean enableChatContextMenu = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean rightClickChatCopies = false;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean enableChatSearch = true;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean alwaysShowChatSearch = false;

    @Entry(category = CHAT_ENHANCEMENTS)
    public static boolean enableChatAnimation = false;

    @Entry(category = CHAT_ENHANCEMENTS, isSlider = true, min = 50, max = 500)
    public static int chatAnimationDurationMs = 150;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Tooltip Enhancements
    // ═══════════════════════════════════════════════════════════════════════════

    @Comment(category = TOOLTIP_ENHANCEMENTS, centered = true)
    public static Comment tooltipEnhancementsText;

    @Comment(category = TOOLTIP_ENHANCEMENTS)
    public static Comment enchantmentsText;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showMissingEnchantments = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showNotMaxedEnchantments = true;

    @Comment(category = TOOLTIP_ENHANCEMENTS)
    public static Comment priceTooltipsText;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean enablePriceTooltips = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS, isSlider = true, min = 5, max = 60)
    public static int priceRefreshIntervalMinutes = 15;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean enablePriceTickerText = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showBazaarBuySell = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showBazaarSpread = false;

    @Comment(category = TOOLTIP_ENHANCEMENTS)
    public static Comment tooltipScrollText;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean enableTooltipScroll = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean anchorTooltipToTop = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS, isSlider = true, min = 1, max = 30)
    public static int tooltipScrollSpeed = 10;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean invertTooltipScroll = false;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean enableHorizontalScroll = false;

    // ═══════════════════════════════════════════════════════════════════════════
    //  General Enhancements
    // ═══════════════════════════════════════════════════════════════════════════

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

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean hideTextureErrors = false;

    @Comment(category = GENERAL_ENHANCEMENTS)
    public static Comment fullbrightText;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean enableFullbright = true;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean fullbrightUseGamma = false;

    @Entry(category = GENERAL_ENHANCEMENTS, isSlider = true, min = 0, max = 100)
    public static double fullbrightStrength = 100.0;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Dev Tools
    // ═══════════════════════════════════════════════════════════════════════════

    @Entry(category = DEV_TOOLS)
    public static boolean devMode = FabricLoader.getInstance().isDevelopmentEnvironment();

    @Condition(requiredOption = "devMode", requiredValue = "true")
    @Comment(category = DEV_TOOLS, centered = true)
    public static Comment devToolsText;

    @Condition(requiredOption = "devMode", requiredValue = "true")
    @Entry(category = DEV_TOOLS)
    public static boolean enableGroundItemDebugHelper = false;

    @Condition(requiredOption = "devMode", requiredValue = "true")
    @Entry(category = DEV_TOOLS)
    public static boolean groundItemDebugOnlyNearby = true;

    @Condition(requiredOption = "devMode", requiredValue = "true")
    @Entry(category = DEV_TOOLS, isSlider = true, min = 4, max = 64)
    public static int groundItemDebugRadius = 16;

    @Condition(requiredOption = "devMode", requiredValue = "true")
    @Entry(category = DEV_TOOLS, isSlider = true, min = 1, max = 200)
    public static int groundItemDebugIntervalTicks = 20;

    // ═══════════════════════════════════════════════════════════════════════════
    //  ModSettings delegation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override public boolean enablePriceTooltips()        { return enablePriceTooltips; }
    @Override public int     priceRefreshIntervalMinutes() { return priceRefreshIntervalMinutes; }
    @Override public boolean enableReminderSound()          { return enableReminderSound; }
    @Override public ReminderSoundType reminderSound()      { return reminderSound; }
    @Override public double  reminderSoundVolume()          { return reminderSoundVolume; }
    @Override public double  reminderSoundPitch()           { return reminderSoundPitch; }
    @Override public boolean enablePriceTickerText()        { return enablePriceTickerText; }
    @Override public boolean showBazaarBuySell()            { return showBazaarBuySell; }
    @Override public boolean showBazaarSpread()             { return showBazaarSpread; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Lightmap dirty-flag on save (fullbright immediate refresh)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void writeChanges() {
        super.writeChanges();

        var mc = Minecraft.getInstance();
        //noinspection ConstantValue
        if (mc.gameRenderer == null) return;
        LightTexture lt = mc.gameRenderer.lightTexture();
        if (lt instanceof LightTextureAccessor accessor) {
            accessor.skyblockenhancements$markDirty();
        }
    }
}
