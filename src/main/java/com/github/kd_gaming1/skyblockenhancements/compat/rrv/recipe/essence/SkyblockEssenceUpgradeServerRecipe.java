package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Item + essence (+ optional companions) → starred item. One recipe per star level per item.
 */
public class SkyblockEssenceUpgradeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockEssenceUpgradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_essence_upgrade"),
                    () -> new SkyblockEssenceUpgradeServerRecipe(
                            null, null, null, new SlotContent[0], 0, "", new String[0]));

    /** Coins, souls, mats, etc. — capped defensively at decode time. */
    private static final int MAX_COMPANIONS = 4;

    private SlotContent input;
    private SlotContent output;
    private SlotContent essence;
    private SlotContent[] companions;
    private int starLevel;
    private String essenceType;
    private String[] wikiUrls;

    public SkyblockEssenceUpgradeServerRecipe(
            SlotContent input, SlotContent output, SlotContent essence,
            SlotContent[] companions, int starLevel, String essenceType, String[] wikiUrls) {
        this.input = input;
        this.output = output;
        this.essence = essence;
        this.companions = companions;
        this.starLevel = starLevel;
        this.essenceType = essenceType != null ? essenceType : "";
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeSlot(tag, "in",  input);
        RecipeTagCodec.writeSlot(tag, "out", output);
        RecipeTagCodec.writeSlot(tag, "ess", essence);
        RecipeTagCodec.writeSlotArray(tag, "compCount", "comp", companions);
        tag.putInt("star", starLevel);
        tag.putString("essType", essenceType);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        input       = RecipeTagCodec.readSlot(tag, "in");
        output      = RecipeTagCodec.readSlot(tag, "out");
        essence     = RecipeTagCodec.readSlot(tag, "ess");
        companions  = RecipeTagCodec.readSlotArray(tag, "compCount", "comp", MAX_COMPANIONS);
        starLevel   = tag.getIntOr("star", 0);
        essenceType = tag.getStringOr("essType", "");
        wikiUrls    = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent   getInput()       { return input; }
    public SlotContent   getOutput()      { return output; }
    public SlotContent   getEssence()     { return essence; }
    public SlotContent[] getCompanions()  { return companions; }
    public int           getStarLevel()   { return starLevel; }
    public String        getEssenceType() { return essenceType; }
    public String[]      getWikiUrls()    { return wikiUrls; }
}