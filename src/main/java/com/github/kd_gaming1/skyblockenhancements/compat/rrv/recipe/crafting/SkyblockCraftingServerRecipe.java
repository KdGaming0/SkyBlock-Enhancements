package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** 3×3 SkyBlock crafting: 9 input slots + 1 output. */
public class SkyblockCraftingServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockCraftingServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_crafting"),
                    () -> new SkyblockCraftingServerRecipe(new SlotContent[9], null, new String[0]));

    private static final int GRID_SIZE = 9;

    private SlotContent[] inputs;
    private SlotContent output;
    private String[] wikiUrls;

    public SkyblockCraftingServerRecipe(SlotContent[] inputs, SlotContent output, String[] wikiUrls) {
        this.inputs = inputs;
        this.output = output;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeFixedSlotArray(tag, "in", inputs);
        RecipeTagCodec.writeSlot(tag, "out", output);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        inputs = RecipeTagCodec.readFixedSlotArray(tag, "in", GRID_SIZE);
        output = RecipeTagCodec.readSlot(tag, "out");
        wikiUrls = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent[] getInputs()   { return inputs; }
    public SlotContent   getOutput()   { return output; }
    public String[]      getWikiUrls() { return wikiUrls; }
}