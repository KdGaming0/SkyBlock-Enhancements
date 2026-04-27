package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** NPC shop purchase: up to {@link #MAX_COSTS} cost slots → 1 result. Tagged with the owning NPC's ID. */
public class SkyblockNpcShopServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockNpcShopServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop"),
                    () -> new SkyblockNpcShopServerRecipe(new SlotContent[0], null, "", "", new String[0]));

    static final int MAX_COSTS = 10;

    private SlotContent[] costs;
    private SlotContent result;
    private String npcId;
    private String npcDisplayName;

    public SkyblockNpcShopServerRecipe(SlotContent[] costs, SlotContent result,
                                       String npcId, String npcDisplayName, String[] wikiUrls) {
        super(wikiUrls);
        this.costs = costs;
        this.result = result;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeSlotArray(tag, RecipeTagCodec.KEY_COUNT, "c", costs);
        RecipeTagCodec.writeSlot(tag, RecipeTagCodec.KEY_OUTPUT, result);
        tag.putString(RecipeTagCodec.KEY_NPC, npcId);
        tag.putString(RecipeTagCodec.KEY_DISPLAY_NAME, npcDisplayName);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        costs          = RecipeTagCodec.readSlotArray(tag, RecipeTagCodec.KEY_COUNT, "c", MAX_COSTS);
        result         = RecipeTagCodec.readSlot(tag, RecipeTagCodec.KEY_OUTPUT);
        npcId          = tag.getStringOr(RecipeTagCodec.KEY_NPC, "");
        npcDisplayName = tag.getStringOr(RecipeTagCodec.KEY_DISPLAY_NAME, "");
    }

    public SlotContent[] getCosts()          { return costs; }
    public SlotContent   getResult()         { return result; }
    public String        getNpcId()          { return npcId; }
    public String        getNpcDisplayName() { return npcDisplayName; }
}
