package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Item + essence (+ optional companions) → starred item. One recipe per star level per item.
 */
public class SkyblockEssenceUpgradeServerRecipe extends AbstractSkyblockServerRecipe {

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

    public SkyblockEssenceUpgradeServerRecipe(
            SlotContent input, SlotContent output, SlotContent essence,
            SlotContent[] companions, int starLevel, String essenceType, String[] wikiUrls) {
        super(wikiUrls);
        this.input = input;
        this.output = output;
        this.essence = essence;
        this.companions = companions;
        this.starLevel = starLevel;
        this.essenceType = essenceType != null ? essenceType : "";
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_INPUTS, input);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_OUTPUT, output);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_ESSENCE, essence);
        RecipeTagCodec.writeSlotArray(tag, "compCount", "comp", companions);
        tag.putInt(RecipeTagCodec.KEY_STAR, starLevel);
        tag.putString(RecipeTagCodec.KEY_ESSENCE_TYPE, essenceType);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        input       = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_INPUTS);
        output      = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_OUTPUT);
        essence     = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_ESSENCE);
        companions  = RecipeTagCodec.readSlotArray(tag, "compCount", "comp", MAX_COMPANIONS);
        starLevel   = tag.getIntOr(RecipeTagCodec.KEY_STAR, 0);
        essenceType = tag.getStringOr(RecipeTagCodec.KEY_ESSENCE_TYPE, "");
    }

    public SlotContent   getInput()       { return input; }
    public SlotContent   getOutput()      { return output; }
    public SlotContent   getEssence()     { return essence; }
    public SlotContent[] getCompanions()  { return companions; }
    public int           getStarLevel()   { return starLevel; }
    public String        getEssenceType() { return essenceType; }
}
