package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Server-side NPC shop recipe: cost items → 1 result, tagged with the owning NPC's internal ID. */
public class SkyblockNpcShopServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockNpcShopServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop"),
                    () -> new SkyblockNpcShopServerRecipe(new SlotContent[0], null, ""));

    private static final int MAX_COSTS = 5;

    private SlotContent[] costs;
    private SlotContent result;
    /** Internal name of the NPC item that owns this shop (e.g. {@code "ADVENTURER_NPC"}). */
    private String npcId;

    public SkyblockNpcShopServerRecipe(SlotContent[] costs, SlotContent result, String npcId) {
        this.costs = costs;
        this.result = result;
        this.npcId = npcId != null ? npcId : "";
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        tag.putInt("count", costs.length);
        for (int i = 0; i < costs.length; i++) {
            if (costs[i] != null && !costs[i].isEmpty()) {
                tag.put("c" + i, TagUtil.encodeItemStackOnServer(costs[i].getValidContents().getFirst()));
            }
        }
        if (result != null && !result.isEmpty()) {
            tag.put("out", TagUtil.encodeItemStackOnServer(result.getValidContents().getFirst()));
        }
        tag.putString("npc", npcId);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        int count = Math.min(tag.getIntOr("count", 0), MAX_COSTS);
        costs = new SlotContent[count];
        for (int i = 0; i < count; i++) {
            CompoundTag ct = tag.getCompoundOrEmpty("c" + i);
            costs[i] = ct.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
        }
        CompoundTag outTag = tag.getCompoundOrEmpty("out");
        result = outTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(outTag));
        npcId = tag.getStringOr("npc", "");
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent[] getCosts() {
        return costs;
    }

    public SlotContent getResult() {
        return result;
    }

    public String getNpcId() {
        return npcId;
    }
}