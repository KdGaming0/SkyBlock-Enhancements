package com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Server-side trade recipe: 1 cost → 1 result. */
public class SkyblockTradeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockTradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_trade"),
                    () -> new SkyblockTradeServerRecipe(null, null));

    private SlotContent cost;
    private SlotContent result;

    public SkyblockTradeServerRecipe(SlotContent cost, SlotContent result) {
        this.cost = cost;
        this.result = result;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        if (cost != null && !cost.isEmpty()) {
            tag.put("cost", TagUtil.encodeItemStackOnServer(cost.getValidContents().getFirst()));
        }
        if (result != null && !result.isEmpty()) {
            tag.put("out", TagUtil.encodeItemStackOnServer(result.getValidContents().getFirst()));
        }
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        CompoundTag costTag = tag.getCompoundOrEmpty("cost");
        cost = costTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(costTag));
        CompoundTag outTag = tag.getCompoundOrEmpty("out");
        result = outTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(outTag));
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent getCost() {
        return cost;
    }

    public SlotContent getResult() {
        return result;
    }
}