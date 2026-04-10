package com.github.kd_gaming1.skyblockenhancements.compat.rrv.essence;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Server-side essence upgrade recipe: Item + Essence (+ optional companion items) → Starred Item.
 * One recipe is created per star level per item (e.g. ★1, ★2, ... ★10).
 */
public class SkyblockEssenceUpgradeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockEssenceUpgradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_essence_upgrade"),
                    () -> new SkyblockEssenceUpgradeServerRecipe(
                            null, null, null, new SlotContent[0], 0, "", new String[0]));

    /** Maximum companion items (coins, souls, etc.) per star level. */
    private static final int MAX_COMPANIONS = 4;

    private SlotContent input;
    private SlotContent output;
    private SlotContent essence;
    private SlotContent[] companions;
    private int starLevel;
    private String essenceType;
    private String[] wikiUrls;

    public SkyblockEssenceUpgradeServerRecipe(
            SlotContent input, SlotContent output, SlotContent essence,
            SlotContent[] companions, int starLevel, String essenceType, String[] wikiUrls) {
        this.input = input;
        this.output = output;
        this.essence = essence;
        this.companions = companions;
        this.starLevel = starLevel;
        this.essenceType = essenceType != null ? essenceType : "";
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        if (input != null && !input.isEmpty()) {
            tag.put("in", TagUtil.encodeItemStackOnServer(input.getValidContents().getFirst()));
        }
        if (output != null && !output.isEmpty()) {
            tag.put("out", TagUtil.encodeItemStackOnServer(output.getValidContents().getFirst()));
        }
        if (essence != null && !essence.isEmpty()) {
            tag.put("ess", TagUtil.encodeItemStackOnServer(essence.getValidContents().getFirst()));
        }
        tag.putInt("compCount", companions.length);
        for (int i = 0; i < companions.length; i++) {
            if (companions[i] != null && !companions[i].isEmpty()) {
                tag.put("comp" + i, TagUtil.encodeItemStackOnServer(
                        companions[i].getValidContents().getFirst()));
            }
        }
        tag.putInt("star", starLevel);
        tag.putString("essType", essenceType);
        SkyblockRecipeUtil.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        CompoundTag inTag = tag.getCompoundOrEmpty("in");
        input = inTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(inTag));

        CompoundTag outTag = tag.getCompoundOrEmpty("out");
        output = outTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(outTag));

        CompoundTag essTag = tag.getCompoundOrEmpty("ess");
        essence = essTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(essTag));

        int compCount = Math.min(tag.getIntOr("compCount", 0), MAX_COMPANIONS);
        companions = new SlotContent[compCount];
        for (int i = 0; i < compCount; i++) {
            CompoundTag ct = tag.getCompoundOrEmpty("comp" + i);
            companions[i] = ct.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
        }

        starLevel = tag.getIntOr("star", 0);
        essenceType = tag.getStringOr("essType", "");
        wikiUrls = SkyblockRecipeUtil.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent getInput() { return input; }
    public SlotContent getOutput() { return output; }
    public SlotContent getEssence() { return essence; }
    public SlotContent[] getCompanions() { return companions; }
    public int getStarLevel() { return starLevel; }
    public String getEssenceType() { return essenceType; }
    public String[] getWikiUrls() { return wikiUrls; }
}