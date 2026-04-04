package com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

/** Server-side Kat pet upgrade recipe: input pet + materials + coins → output pet. */
public class SkyblockKatUpgradeServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockKatUpgradeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_katgrade"),
                    () -> new SkyblockKatUpgradeServerRecipe(
                            null, null, new SlotContent[0], 0, 0, new String[0]));

    private static final int MAX_ITEMS = 4;

    private SlotContent input;
    private SlotContent output;
    private SlotContent[] materials;
    private long coins;
    private int timeSeconds;
    private String[] wikiUrls;

    public SkyblockKatUpgradeServerRecipe(
            SlotContent input, SlotContent output, SlotContent[] materials, long coins,
            int timeSeconds, String[] wikiUrls) {
        this.input = input;
        this.output = output;
        this.materials = materials;
        this.coins = coins;
        this.timeSeconds = timeSeconds;
        this.wikiUrls = wikiUrls != null ? wikiUrls : new String[0];
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        if (input != null && !input.isEmpty()) {
            tag.put("in", TagUtil.encodeItemStackOnServer(input.getValidContents().getFirst()));
        }
        if (output != null && !output.isEmpty()) {
            tag.put("out", TagUtil.encodeItemStackOnServer(output.getValidContents().getFirst()));
        }
        tag.putInt("matCount", materials.length);
        for (int i = 0; i < materials.length; i++) {
            if (materials[i] != null && !materials[i].isEmpty()) {
                tag.put(
                        "m" + i, TagUtil.encodeItemStackOnServer(materials[i].getValidContents().getFirst()));
            }
        }
        tag.putLong("coins", coins);
        tag.putInt("time", timeSeconds);
        SkyblockRecipeUtil.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        CompoundTag inTag = tag.getCompoundOrEmpty("in");
        input = inTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(inTag));
        CompoundTag outTag = tag.getCompoundOrEmpty("out");
        output = outTag.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(outTag));
        int count = Math.min(tag.getIntOr("matCount", 0), MAX_ITEMS);
        materials = new SlotContent[count];
        for (int i = 0; i < count; i++) {
            CompoundTag ct = tag.getCompoundOrEmpty("m" + i);
            materials[i] = ct.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
        }
        coins = tag.getLongOr("coins", 0);
        timeSeconds = tag.getIntOr("time", 0);
        ListTag urlsTag = tag.getListOrEmpty("wikiUrls");
        wikiUrls = new String[urlsTag.size()];
        for (int i = 0; i < urlsTag.size(); i++) {
            wikiUrls[i] = urlsTag.get(i).asString().orElse("");
        }
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public SlotContent getInput() { return input; }
    public SlotContent getOutput() { return output; }
    public SlotContent[] getMaterials() { return materials; }
    public long getCoins() { return coins; }
    public int getTimeSeconds() { return timeSeconds; }
    public String[] getWikiUrls() { return wikiUrls; }
}