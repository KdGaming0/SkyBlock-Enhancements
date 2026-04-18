package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Visual-only wiki card for items that have wiki URLs but no other recipe data. */
public class SkyblockWikiInfoServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockWikiInfoServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_wiki_info"),
                    () -> new SkyblockWikiInfoServerRecipe(ItemStack.EMPTY, new String[0]));

    private ItemStack displayItem;
    private String[] wikiUrls;

    public SkyblockWikiInfoServerRecipe(ItemStack displayItem, String[] wikiUrls) {
        this.displayItem = displayItem != null ? displayItem : ItemStack.EMPTY;
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeStack(tag, "item", displayItem);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        displayItem = RecipeTagCodec.readStack(tag, "item");
        wikiUrls    = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public ItemStack getDisplayItem() { return displayItem; }
    public String[]  getWikiUrls()    { return wikiUrls; }
}