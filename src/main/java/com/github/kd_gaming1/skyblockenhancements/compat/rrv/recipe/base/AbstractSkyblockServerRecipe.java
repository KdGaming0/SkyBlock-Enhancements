package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import net.minecraft.nbt.CompoundTag;

/**
 * Base class for all SkyBlock server recipes. Centralises wiki-URL serialization
 * and the {@link #getRecipeType()} boilerplate so concrete recipes only declare
 * their own fields.
 *
 * <p>Subclasses implement {@link #writeFields} and {@link #readFields} for their
 * custom data; wiki URLs are handled automatically by this base.
 */
public abstract class AbstractSkyblockServerRecipe implements ReliableServerRecipe {

    private String[] wikiUrls;

    protected AbstractSkyblockServerRecipe(String[] wikiUrls) {
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    /** The static {@link ReliableServerRecipeType} registered for this recipe class. */
    protected abstract ReliableServerRecipeType<?> getType();

    /** Write recipe-specific fields (excluding wiki URLs) to the tag. */
    protected abstract void writeFields(CompoundTag tag);

    /** Read recipe-specific fields (excluding wiki URLs) from the tag. */
    protected abstract void readFields(CompoundTag tag);

    @Override
    public final void writeToTag(CompoundTag tag) {
        writeFields(tag);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public final void loadFromTag(CompoundTag tag) {
        readFields(tag);
        wikiUrls = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public final ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return getType();
    }

    public final String[] getWikiUrls() {
        return wikiUrls;
    }
}
