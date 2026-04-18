package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Mob drop table: up to 12 drop slots paired with chance strings. */
public class SkyblockDropsServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockDropsServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_drops"),
                    () -> new SkyblockDropsServerRecipe(
                            "", new SlotContent[0], new String[0], 0, 0, new String[0]));

    private static final int MAX_DROPS = 12;

    private String mobName;
    private SlotContent[] drops;
    private String[] chances;
    private int level;
    private int combatXp;
    private String[] wikiUrls;

    public SkyblockDropsServerRecipe(String mobName, SlotContent[] drops, String[] chances,
                                     int level, int combatXp, String[] wikiUrls) {
        this.mobName = mobName;
        this.drops = drops;
        this.chances = chances;
        this.level = level;
        this.combatXp = combatXp;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        tag.putString("mob", mobName);
        RecipeTagCodec.writeSlotArray(tag, "count", "d", drops);
        RecipeTagCodec.writeStringArray(tag, "chances", chances);
        tag.putInt("lvl", level);
        tag.putInt("xp", combatXp);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        mobName   = tag.getStringOr("mob", "");
        drops     = RecipeTagCodec.readSlotArray(tag, "count", "d", MAX_DROPS);
        chances   = RecipeTagCodec.readStringArray(tag, "chances", drops.length);
        level     = tag.getIntOr("lvl", 0);
        combatXp  = tag.getIntOr("xp",  0);
        wikiUrls  = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public String        getMobName()  { return mobName; }
    public SlotContent[] getDrops()    { return drops; }
    public String[]      getChances()  { return chances; }
    public int           getLevel()    { return level; }
    public int           getCombatXp() { return combatXp; }
    public String[]      getWikiUrls() { return wikiUrls; }
}