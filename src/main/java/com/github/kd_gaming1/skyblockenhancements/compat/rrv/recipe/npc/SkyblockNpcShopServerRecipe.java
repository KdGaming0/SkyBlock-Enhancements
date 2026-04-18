package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** NPC shop purchase: up to 5 cost slots → 1 result. Tagged with the owning NPC's ID. */
public class SkyblockNpcShopServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockNpcShopServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop"),
                    () -> new SkyblockNpcShopServerRecipe(new SlotContent[0], null, "", "", new String[0]));

    private static final int MAX_COSTS = 5;

    private SlotContent[] costs;
    private SlotContent result;
    private String npcId;
    private String npcDisplayName;
    private String[] wikiUrls;

    public SkyblockNpcShopServerRecipe(SlotContent[] costs, SlotContent result,
                                       String npcId, String npcDisplayName, String[] wikiUrls) {
        this.costs = costs;
        this.result = result;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeSlotArray(tag, "count", "c", costs);
        RecipeTagCodec.writeSlot(tag, "out", result);
        tag.putString("npc", npcId);
        tag.putString("displayName", npcDisplayName);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        costs          = RecipeTagCodec.readSlotArray(tag, "count", "c", MAX_COSTS);
        result         = RecipeTagCodec.readSlot(tag, "out");
        npcId          = tag.getStringOr("npc", "");
        npcDisplayName = tag.getStringOr("displayName", "");
        wikiUrls       = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent[] getCosts()          { return costs; }
    public SlotContent   getResult()         { return result; }
    public String        getNpcId()          { return npcId; }
    public String        getNpcDisplayName() { return npcDisplayName; }
    public String[]      getWikiUrls()       { return wikiUrls; }
}