package com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Server-side representation of a 3×3 SkyBlock crafting recipe. Handles serialization to/from
 * NBT for RRV's internal server→client sync.
 */
public class SkyblockCraftingServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockCraftingServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath(
                            "skyblock_enhancements", "skyblock_crafting"),
                    () -> new SkyblockCraftingServerRecipe(new SlotContent[9], null));

    private SlotContent[] inputs; // length 9
    private SlotContent output;

    public SkyblockCraftingServerRecipe(SlotContent[] inputs, SlotContent output) {
        this.inputs = inputs;
        this.output = output;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        for (int i = 0; i < 9; i++) {
            if (inputs[i] != null && !inputs[i].isEmpty()) {
                tag.put("in" + i, TagUtil.encodeItemStackOnServer(inputs[i].getValidContents().getFirst()));
            }
        }
        if (output != null && !output.isEmpty()) {
            tag.put("out", TagUtil.encodeItemStackOnServer(output.getValidContents().getFirst()));
        }
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            CompoundTag ct = tag.getCompoundOrEmpty("in" + i);
            if (!ct.isEmpty()) {
                inputs[i] = SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
            }
        }
        CompoundTag outTag = tag.getCompoundOrEmpty("out");
        if (!outTag.isEmpty()) {
            output = SlotContent.of(TagUtil.decodeItemStackOnClient(outTag));
        }
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent[] getInputs() {
        return inputs;
    }

    public SlotContent getOutput() {
        return output;
    }
}