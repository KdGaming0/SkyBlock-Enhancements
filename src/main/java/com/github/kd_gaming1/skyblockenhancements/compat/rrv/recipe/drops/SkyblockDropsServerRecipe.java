package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Mob drop table: up to 12 drop slots paired with chance strings, plus the NEU render ref. */
public class SkyblockDropsServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockDropsServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_drops"),
                    () -> new SkyblockDropsServerRecipe(
                            "", "", new SlotContent[0], new String[0], new String[0]));

    private static final int MAX_DROPS = 12;

    private String mobName;
    private String renderRef;
    private SlotContent[] drops;
    private String[] chances;

    public SkyblockDropsServerRecipe(String mobName, String renderRef, SlotContent[] drops,
                                     String[] chances, String[] wikiUrls) {
        super(wikiUrls);
        this.mobName = mobName != null ? mobName : "";
        this.renderRef = renderRef != null ? renderRef : "";
        this.drops = drops;
        this.chances = chances;
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        tag.putString(RecipeTagCodec.KEY_MOB, mobName);
        tag.putString(RecipeTagCodec.KEY_RENDER, renderRef);
        RecipeTagCodec.writeSlotArray(tag, RecipeTagCodec.KEY_COUNT, "d", drops);
        RecipeTagCodec.writeStringArray(tag, RecipeTagCodec.KEY_CHANCES, chances);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        mobName   = tag.getStringOr(RecipeTagCodec.KEY_MOB, "");
        renderRef = tag.getStringOr(RecipeTagCodec.KEY_RENDER, "");
        drops     = RecipeTagCodec.readSlotArray(tag, RecipeTagCodec.KEY_COUNT, "d", MAX_DROPS);
        chances   = RecipeTagCodec.readStringArray(tag, RecipeTagCodec.KEY_CHANCES, drops.length);
    }

    public String        getMobName()   { return mobName; }
    public String        getRenderRef() { return renderRef; }
    public SlotContent[] getDrops()     { return drops; }
    public String[]      getChances()   { return chances; }
}
