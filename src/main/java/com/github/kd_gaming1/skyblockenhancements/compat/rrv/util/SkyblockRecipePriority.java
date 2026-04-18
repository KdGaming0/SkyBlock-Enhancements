package com.github.kd_gaming1.skyblockenhancements.compat.rrv.util;

/**
 * Recipe display priority constants. RRV sorts recipe tabs by
 * {@link cc.cassian.rrv.api.recipe.ReliableClientRecipe#getPriority()} — higher values
 * appear first. These constants ensure the most actionable recipe types (crafting, forge)
 * are shown before informational ones (wiki, NPC info).
 *
 * <p>Values are spaced by 10 to allow future recipe types to be inserted between
 * existing ones without renumbering.
 */
public final class SkyblockRecipePriority {

    /** Direct crafting — most actionable, shown first. */
    public static final int CRAFTING = 100;

    /** Forge recipes — second most actionable. */
    public static final int FORGE = 90;

    /** NPC shop purchases. */
    public static final int NPC_SHOP = 80;

    /** Villager-style trades. */
    public static final int TRADE = 70;

    /** Essence star upgrades. */
    public static final int ESSENCE_UPGRADE = 60;

    /** Kat pet upgrades. */
    public static final int KAT_UPGRADE = 50;

    /** Mob drop tables. */
    public static final int DROPS = 40;

    /** NPC information cards. */
    public static final int NPC_INFO = 20;

    /** Wiki-only fallback info cards — least actionable, shown last. */
    public static final int WIKI_INFO = 10;

    private SkyblockRecipePriority() {}
}