package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Single-cost → single-result trade. */
public class SkyblockTradeServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockTradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_trade"),
                    () -> new SkyblockTradeServerRecipe(null, null, new String[0]));

    private SlotContent cost;
    private SlotContent result;

    public SkyblockTradeServerRecipe(SlotContent cost, SlotContent result, String[] wikiUrls) {
        super(wikiUrls);
        this.cost = cost;
        this.result = result;
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_COST, cost);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_OUTPUT, result);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        cost     = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_COST);
        result   = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_OUTPUT);
    }

    public SlotContent getCost()     { return cost; }
    public SlotContent getResult()   { return result; }
}
