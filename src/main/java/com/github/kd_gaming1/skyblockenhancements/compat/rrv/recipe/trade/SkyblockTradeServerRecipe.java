package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Single-cost → single-result trade. */
public class SkyblockTradeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockTradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_trade"),
                    () -> new SkyblockTradeServerRecipe(null, null, new String[0]));

    private SlotContent cost;
    private SlotContent result;
    private String[] wikiUrls;

    public SkyblockTradeServerRecipe(SlotContent cost, SlotContent result, String[] wikiUrls) {
        this.cost = cost;
        this.result = result;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeSlot(tag, "cost", cost);
        RecipeTagCodec.writeSlot(tag, "out",  result);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        cost     = RecipeTagCodec.readSlot(tag, "cost");
        result   = RecipeTagCodec.readSlot(tag, "out");
        wikiUrls = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent getCost()     { return cost; }
    public SlotContent getResult()   { return result; }
    public String[]    getWikiUrls() { return wikiUrls; }
}