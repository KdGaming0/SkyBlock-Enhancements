package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Forge recipe: variable-length inputs → 1 output, with a duration in seconds. */
public class SkyblockForgeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockForgeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_forge"),
                    () -> new SkyblockForgeServerRecipe(new SlotContent[0], null, 0, new String[0]));

    private static final int MAX_INPUTS = 6;

    private SlotContent[] inputs;
    private SlotContent output;
    private int durationSeconds;
    private String[] wikiUrls;

    public SkyblockForgeServerRecipe(SlotContent[] inputs, SlotContent output,
                                     int durationSeconds, String[] wikiUrls) {
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeSlotArray(tag, "count", "in", inputs);
        RecipeTagCodec.writeSlot(tag, "out", output);
        tag.putInt("dur", durationSeconds);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        inputs = RecipeTagCodec.readSlotArray(tag, "count", "in", MAX_INPUTS);
        output = RecipeTagCodec.readSlot(tag, "out");
        durationSeconds = tag.getIntOr("dur", 0);
        wikiUrls = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent[] getInputs()          { return inputs; }
    public SlotContent   getOutput()          { return output; }
    public int           getDurationSeconds() { return durationSeconds; }
    public String[]      getWikiUrls()        { return wikiUrls; }
}