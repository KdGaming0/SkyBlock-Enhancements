package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Forge recipe: variable-length inputs → 1 output, with a duration in seconds. */
public class SkyblockForgeServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockForgeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_forge"),
                    () -> new SkyblockForgeServerRecipe(new SlotContent[0], null, 0, new String[0]));

    static final int MAX_INPUTS = 8;

    private SlotContent[] inputs;
    private SlotContent output;
    private int durationSeconds;

    public SkyblockForgeServerRecipe(SlotContent[] inputs, SlotContent output,
                                     int durationSeconds, String[] wikiUrls) {
        super(wikiUrls);
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeSlotArray(tag, RecipeTagCodec.KEY_COUNT, RecipeTagCodec.KEY_INPUTS, inputs);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_OUTPUT, output);
        tag.putInt(RecipeTagCodec.KEY_DURATION, durationSeconds);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        inputs = RecipeTagCodec.readSlotArray(tag, RecipeTagCodec.KEY_COUNT, RecipeTagCodec.KEY_INPUTS, MAX_INPUTS);
        output = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_OUTPUT);
        durationSeconds = tag.getIntOr(RecipeTagCodec.KEY_DURATION, 0);
    }

    public SlotContent[] getInputs()          { return inputs; }
    public SlotContent   getOutput()          { return output; }
    public int           getDurationSeconds() { return durationSeconds; }
}
