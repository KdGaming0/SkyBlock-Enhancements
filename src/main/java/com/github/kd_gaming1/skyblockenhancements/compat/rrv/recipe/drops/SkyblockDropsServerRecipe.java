package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Mob drop table: up to 12 drop slots paired with chance strings, plus the NEU render ref. */
public class SkyblockDropsServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockDropsServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_drops"),
                    () -> new SkyblockDropsServerRecipe(
                            "", "", new SlotContent[0], new String[0], new String[0]));

    private static final int MAX_DROPS = 12;

    private String mobName;
    private String renderRef;
    private SlotContent[] drops;
    private String[] chances;
    private String[] wikiUrls;

    public SkyblockDropsServerRecipe(String mobName, String renderRef, SlotContent[] drops,
                                     String[] chances, String[] wikiUrls) {
        this.mobName = mobName != null ? mobName : "";
        this.renderRef = renderRef != null ? renderRef : "";
        this.drops = drops;
        this.chances = chances;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        tag.putString("mob", mobName);
        tag.putString("render", renderRef);
        RecipeTagCodec.writeSlotArray(tag, "count", "d", drops);
        RecipeTagCodec.writeStringArray(tag, "chances", chances);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        mobName   = tag.getStringOr("mob", "");
        renderRef = tag.getStringOr("render", "");
        drops     = RecipeTagCodec.readSlotArray(tag, "count", "d", MAX_DROPS);
        chances   = RecipeTagCodec.readStringArray(tag, "chances", drops.length);
        wikiUrls  = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public String        getMobName()   { return mobName; }
    public String        getRenderRef() { return renderRef; }
    public SlotContent[] getDrops()     { return drops; }
    public String[]      getChances()   { return chances; }
    public String[]      getWikiUrls()  { return wikiUrls; }
}