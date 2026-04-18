package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Kat pet upgrade: input pet + up to 4 materials + coins → output pet, with a duration. */
public class SkyblockKatUpgradeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockKatUpgradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_katgrade"),
                    () -> new SkyblockKatUpgradeServerRecipe(
                            null, null, new SlotContent[0], 0, 0, new String[0]));

    private static final int MAX_MATERIALS = 4;

    private SlotContent input;
    private SlotContent output;
    private SlotContent[] materials;
    private long coins;
    private int timeSeconds;
    private String[] wikiUrls;

    public SkyblockKatUpgradeServerRecipe(
            SlotContent input, SlotContent output, SlotContent[] materials,
            long coins, int timeSeconds, String[] wikiUrls) {
        this.input = input;
        this.output = output;
        this.materials = materials;
        this.coins = coins;
        this.timeSeconds = timeSeconds;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeSlot(tag, "in",  input);
        RecipeTagCodec.writeSlot(tag, "out", output);
        RecipeTagCodec.writeSlotArray(tag, "matCount", "m", materials);
        tag.putLong("coins", coins);
        tag.putInt("time", timeSeconds);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        input     = RecipeTagCodec.readSlot(tag, "in");
        output    = RecipeTagCodec.readSlot(tag, "out");
        materials = RecipeTagCodec.readSlotArray(tag, "matCount", "m", MAX_MATERIALS);
        coins     = tag.getLongOr("coins", 0);
        timeSeconds = tag.getIntOr("time", 0);
        wikiUrls  = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent   getInput()       { return input; }
    public SlotContent   getOutput()      { return output; }
    public SlotContent[] getMaterials()   { return materials; }
    public long          getCoins()       { return coins; }
    public int           getTimeSeconds() { return timeSeconds; }
    public String[]      getWikiUrls()    { return wikiUrls; }
}