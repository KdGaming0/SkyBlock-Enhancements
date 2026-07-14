package com.github.kd_gaming1.skyblockenhancements.config;

import com.github.kd_gaming1.skyblockenhancements.feature.savecursorposition.CursorFilterMode;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.LockCornerPosition;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.LockedDropMode;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.SlotBindOutlineVisibility;
import com.github.kd_gaming1.skyblockenhancements.util.ItemRarity;
import eu.midnightdust.lib.config.MidnightConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public class SkyblockEnhancementsConfig extends MidnightConfig implements ModSettings {

    // ── Category IDs ────────────────────────────────────────────────────────────

    public static final String SKYBLOCK_ENHANCEMENTS = "skyblock_enhancements";
    public static final String TOOLTIP_ENHANCEMENTS  = "tooltip_enhancements";
    public static final String GENERAL_ENHANCEMENTS  = "general_enhancements";
    public static final String MINING_ENHANCEMENTS   = "mining_enhancements";
    public static final String DEV_TOOLS             = "dev_tools";

    // ═══════════════════════════════════════════════════════════════════════════
    //  Skyblock Enhancements
    // ═══════════════════════════════════════════════════════════════════════════

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment text;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean preventWeaponPlacement = true;

    @Hidden
    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean hideCheapCoins = false;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean setKatReminderForPetUpgrades = true;

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment slotLockingText;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableSlotLocking = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static LockCornerPosition lockCornerPosition = LockCornerPosition.TOP_RIGHT;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static LockedDropMode dropLockedItemsWithQ = LockedDropMode.IN_DUNGEONS;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableSlotBinding = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static SlotBindOutlineVisibility slotBindOutlineVisibility = SlotBindOutlineVisibility.ALWAYS;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isColor = true)
    public static String slotBindSourceColor = "#FFFF00";

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isColor = true)
    public static String slotBindTargetColor = "#55FF55";

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isColor = true)
    public static String slotBindLineColor = "#FFFF00";

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment rarityDropGuardText;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableRarityDropGuard = false;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static ItemRarity rarityDropGuardMinRarity = ItemRarity.RARE;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean rarityDropGuardTripleDrop = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean rarityDropGuardBlockOutsideDrop = true;

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment divider1;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean saveCursorPosition = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 10, max = 5000)
    public static int saveCursorPositionToleranceMs = 500;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static CursorFilterMode saveCursorPositionFilterMode = CursorFilterMode.ALL;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static List<String> saveCursorPositionFilterList = new ArrayList<>();

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment divider2;

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

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment reminderSoundText;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enableReminderSound = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static ReminderSoundType reminderSound = ReminderSoundType.EXPERIENCE;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 0, max = 3)
    public static double reminderSoundVolume = 1.5;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 0.5, max = 2)
    public static double reminderSoundPitch = 1.0;

    @Comment(category = SKYBLOCK_ENHANCEMENTS, centered = true)
    public static Comment potionOverlayText;

    @Entry(category = SKYBLOCK_ENHANCEMENTS)
    public static boolean enablePotionOverlay = true;

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isColor = true)
    public static String enabledPotionOverlayColor = "#44FF44";

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isColor = true)
    public static String disabledPotionOverlayColor = "#FF4444";

    @Entry(category = SKYBLOCK_ENHANCEMENTS, isSlider = true, min = 0, max = 255)
    public static int potionOverlayAlpha = 102;

    public enum ReminderSoundType {
        UI, BELL, CHIME, LEVEL_UP, EXPERIENCE, HARP, PLING, SUCCESS
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Mining Enhancements
    // ═══════════════════════════════════════════════════════════════════════════

    @Comment(category = MINING_ENHANCEMENTS, centered = true)
    public static Comment pingOffsetMiningText;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean enablePingOffsetMining = false;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean pingOffsetShowOnLook = true;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean pingOffsetShowHighlight = true;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean pingOffsetShowOutline = true;

    @Entry(category = MINING_ENHANCEMENTS, isColor = true)
    public static String pingOffsetColorStart = "#FF0000";

    @Entry(category = MINING_ENHANCEMENTS, isColor = true)
    public static String pingOffsetColorMid = "#FFFF00";

    @Entry(category = MINING_ENHANCEMENTS, isColor = true)
    public static String pingOffsetColorEnd = "#00FF00";

    @Entry(category = MINING_ENHANCEMENTS, isSlider = true, min = 0.5, max = 2.0)
    public static double pingOffsetLineWidth = 4.0;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean pingOffsetColorUseMid = false;

    @Entry(category = MINING_ENHANCEMENTS, isSlider = true, min = 0, max = 100)
    public static int pingOffsetHighlightAlpha = 16;

    @Entry(category = MINING_ENHANCEMENTS, isSlider = true, min = -100, max = 100, precision = 1)
    public static int pingOffsetMarginMs = 0;

    @Comment(category = MINING_ENHANCEMENTS, centered = true)
    public static Comment pickaxeAbilityText;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean notifyPickaxeAbilityReady = false;

    @Entry(category = MINING_ENHANCEMENTS)
    public static boolean pickaxeAbilityReadySound = false;

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

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showWhenPressingShift = true;

    @Comment(category = TOOLTIP_ENHANCEMENTS, centered = true)
    public static Comment priceTooltipsText;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean enablePriceTooltips = false;

    @Entry(category = TOOLTIP_ENHANCEMENTS, isSlider = true, min = 5, max = 60)
    public static int priceRefreshIntervalMinutes = 20;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean enablePriceTickerText = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean roundPriceNumbers = false;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showBazaarBuySell = true;

    @Entry(category = TOOLTIP_ENHANCEMENTS)
    public static boolean showBazaarSpread = false;

    @Comment(category = TOOLTIP_ENHANCEMENTS, centered = true)
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
    public static boolean hideItemFrames = false;

    @Entry(category = GENERAL_ENHANCEMENTS)
    public static boolean hideTextureErrors = false;

    @Comment(category = GENERAL_ENHANCEMENTS, centered = true)
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
    @Override public boolean roundPriceNumbers()            { return roundPriceNumbers; }
    @Override public boolean showBazaarBuySell()            { return showBazaarBuySell; }
    @Override public boolean showBazaarSpread()             { return showBazaarSpread; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Lightmap dirty-flag on save (fullbright immediate refresh)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void writeChanges() {
        super.writeChanges();
        // In 26.1 the lightmap render state is recalculated each frame, so no explicit
        // dirty notification is required. The fullbright mixin in LightTextureMixin
        // reads the config directly during Lightmap#render.
    }
}
