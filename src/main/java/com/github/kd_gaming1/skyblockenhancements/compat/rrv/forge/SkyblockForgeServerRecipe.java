package com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Server-side forge recipe: variable inputs → 1 output, with duration in seconds. */
public class SkyblockForgeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockForgeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_forge"),
                    () -> new SkyblockForgeServerRecipe(new SlotContent[0], null, 0));

    private static final int MAX_INPUTS = 6;

    private SlotContent[] inputs;
    private SlotContent output;
    private int durationSeconds;

    public SkyblockForgeServerRecipe(SlotContent[] inputs, SlotContent output, int durationSeconds) {
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        tag.putInt("count", inputs.length);
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null && !inputs[i].isEmpty()) {
                tag.put("in" + i, TagUtil.encodeItemStackOnServer(inputs[i].getValidContents().getFirst()));
            }
        }
        if (output != null && !output.isEmpty()) {
            tag.put("out", TagUtil.encodeItemStackOnServer(output.getValidContents().getFirst()));
        }
        tag.putInt("dur", durationSeconds);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        int count = Math.min(tag.getIntOr("count", 0), MAX_INPUTS);
        inputs = new SlotContent[count];
        for (int i = 0; i < count; i++) {
            CompoundTag ct = tag.getCompoundOrEmpty("in" + i);
            inputs[i] = ct.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
        }
        CompoundTag outTag = tag.getCompoundOrEmpty("out");
        output = outTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(outTag));
        durationSeconds = tag.getIntOr("dur", 0);
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

    public int getDurationSeconds() {
        return durationSeconds;
    }
}