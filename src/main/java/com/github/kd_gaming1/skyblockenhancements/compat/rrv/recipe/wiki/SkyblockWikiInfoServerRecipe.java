package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Visual-only wiki card for items that have wiki URLs but no other recipe data. */
public class SkyblockWikiInfoServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockWikiInfoServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_wiki_info"),
                    () -> new SkyblockWikiInfoServerRecipe(ItemStack.EMPTY, "", new String[0]));

    private ItemStack displayItem;
    private String displayName;

    public SkyblockWikiInfoServerRecipe(ItemStack displayItem, String displayName, String[] wikiUrls) {
        super(wikiUrls);
        this.displayItem = displayItem != null ? displayItem : ItemStack.EMPTY;
        this.displayName = displayName != null ? displayName : "";
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeStack(tag, RecipeTagCodec.KEY_ITEM, displayItem);
        tag.putString(RecipeTagCodec.KEY_NAME, displayName);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        displayItem = RecipeTagCodec.readStack(tag, RecipeTagCodec.KEY_ITEM);
        displayName = tag.getStringOr(RecipeTagCodec.KEY_NAME, "");
    }

    public ItemStack getDisplayItem()  { return displayItem; }
    public String    getDisplayName()  { return displayName; }
}
