package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Kat pet upgrade: input pet + up to 4 materials + coins → output pet, with a duration. */
public class SkyblockKatUpgradeServerRecipe extends AbstractSkyblockServerRecipe {

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

    public SkyblockKatUpgradeServerRecipe(
            SlotContent input, SlotContent output, SlotContent[] materials,
            long coins, int timeSeconds, String[] wikiUrls) {
        super(wikiUrls);
        this.input = input;
        this.output = output;
        this.materials = materials;
        this.coins = coins;
        this.timeSeconds = timeSeconds;
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_INPUTS, input);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_OUTPUT, output);
        RecipeTagCodec.writeSlotArray(tag, "matCount", "m", materials);
        tag.putLong(RecipeTagCodec.KEY_COINS, coins);
        tag.putInt(RecipeTagCodec.KEY_TIME, timeSeconds);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        input       = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_INPUTS);
        output      = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_OUTPUT);
        materials   = RecipeTagCodec.readSlotArray(tag, "matCount", "m", MAX_MATERIALS);
        coins       = tag.getLongOr(RecipeTagCodec.KEY_COINS, 0);
        timeSeconds = tag.getIntOr(RecipeTagCodec.KEY_TIME, 0);
    }

    public SlotContent   getInput()       { return input; }
    public SlotContent   getOutput()      { return output; }
    public SlotContent[] getMaterials()   { return materials; }
    public long          getCoins()       { return coins; }
    public int           getTimeSeconds() { return timeSeconds; }
}
