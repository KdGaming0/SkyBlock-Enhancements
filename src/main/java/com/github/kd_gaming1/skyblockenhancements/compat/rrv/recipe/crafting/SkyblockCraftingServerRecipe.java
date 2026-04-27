package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** 3×3 SkyBlock crafting: 9 input slots + 1 output. */
public class SkyblockCraftingServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockCraftingServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_crafting"),
                    () -> new SkyblockCraftingServerRecipe(new SlotContent[9], null, new String[0]));

    private static final int GRID_SIZE = 9;

    private SlotContent[] inputs;
    private SlotContent output;

    public SkyblockCraftingServerRecipe(SlotContent[] inputs, SlotContent output, String[] wikiUrls) {
        super(wikiUrls);
        this.inputs = inputs;
        this.output = output;
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeFixedSlotArray(tag, RecipeTagCodec.KEY_INPUTS, inputs);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_OUTPUT, output);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        inputs = RecipeTagCodec.readFixedSlotArray(tag, RecipeTagCodec.KEY_INPUTS, GRID_SIZE);
        output = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_OUTPUT);
    }

    public SlotContent[] getInputs() { return inputs; }
    public SlotContent   getOutput() { return output; }
}
