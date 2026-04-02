package com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

/** Server-side mob drop recipe: mob info → drop items with chances. */
public class SkyblockDropsServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockDropsServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_drops"),
                    () -> new SkyblockDropsServerRecipe("", new SlotContent[0], new String[0], 0, 0));

    private static final int MAX_DROPS = 12;

    private String mobName;
    private SlotContent[] drops;
    private String[] chances;
    private int level;
    private int combatXp;

    public SkyblockDropsServerRecipe(
            String mobName, SlotContent[] drops, String[] chances, int level, int combatXp) {
        this.mobName = mobName;
        this.drops = drops;
        this.chances = chances;
        this.level = level;
        this.combatXp = combatXp;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        tag.putString("mob", mobName);
        tag.putInt("count", drops.length);
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] != null && !drops[i].isEmpty()) {
                tag.put("d" + i, TagUtil.encodeItemStackOnServer(drops[i].getValidContents().getFirst()));
            }
        }
        ListTag chanceList = new ListTag();
        for (String c : chances) {
            chanceList.add(StringTag.valueOf(c != null ? c : ""));
        }
        tag.put("chances", chanceList);
        tag.putInt("lvl", level);
        tag.putInt("xp", combatXp);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        mobName = tag.getStringOr("mob", "");
        int count = Math.min(tag.getIntOr("count", 0), MAX_DROPS);
        drops = new SlotContent[count];
        for (int i = 0; i < count; i++) {
            CompoundTag ct = tag.getCompoundOrEmpty("d" + i);
            drops[i] = ct.isEmpty() ? null : SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
        }
        ListTag chanceList = tag.getListOrEmpty("chances");
        chances = new String[count];
        for (int i = 0; i < count && i < chanceList.size(); i++) {
            chances[i] = chanceList.get(i).asString().orElse("");
        }
        level = tag.getIntOr("lvl", 0);
        combatXp = tag.getIntOr("xp", 0);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public String getMobName() {
        return mobName;
    }

    public SlotContent[] getDrops() {
        return drops;
    }

    public String[] getChances() {
        return chances;
    }

    public int getLevel() {
        return level;
    }

    public int getCombatXp() {
        return combatXp;
    }
}