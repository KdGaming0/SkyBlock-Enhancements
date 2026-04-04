package com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side recipe for items that have wiki URLs but no other recipe types.
 * Provides a visual-only display so the item is still clickable in the recipe viewer.
 */
public class SkyblockWikiInfoServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockWikiInfoServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_wiki_info"),
                    () -> new SkyblockWikiInfoServerRecipe(ItemStack.EMPTY, new String[0]));

    private ItemStack displayItem;
    private String[] wikiUrls;

    public SkyblockWikiInfoServerRecipe(ItemStack displayItem, String[] wikiUrls) {
        this.displayItem = displayItem != null ? displayItem : ItemStack.EMPTY;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        if (!displayItem.isEmpty()) {
            tag.put("item", TagUtil.encodeItemStackOnServer(displayItem));
        }
        SkyblockRecipeUtil.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        CompoundTag itemTag = tag.getCompoundOrEmpty("item");
        displayItem = itemTag.isEmpty() ? ItemStack.EMPTY : TagUtil.decodeItemStackOnClient(itemTag);
        wikiUrls = SkyblockRecipeUtil.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public ItemStack getDisplayItem() { return displayItem; }
    public String[] getWikiUrls() { return wikiUrls; }
}