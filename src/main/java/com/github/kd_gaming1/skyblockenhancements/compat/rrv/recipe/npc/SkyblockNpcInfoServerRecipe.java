package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Info card for an NPC: head, display name, island location, lore lines, and wiki URLs.
 * Generated for every NPC item. Shop NPCs are reached from the shop page's "NPC Info" button,
 * non-shop NPCs by clicking the NPC item directly.
 */
public class SkyblockNpcInfoServerRecipe implements ReliableServerRecipe {

    public static final ReliableServerRecipeType<SkyblockNpcInfoServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_info"),
                    () -> new SkyblockNpcInfoServerRecipe(
                            ItemStack.EMPTY, "", "", "", 0, 0, 0, new String[0], new String[0]));

    private ItemStack npcHead;
    private String npcId;
    private String npcDisplayName;
    private String island;
    private int x;
    private int y;
    private int z;
    private String[] loreLines;
    private String[] wikiUrls;

    public SkyblockNpcInfoServerRecipe(ItemStack npcHead, String npcId, String npcDisplayName,
                                       String island, int x, int y, int z,
                                       String[] loreLines, String[] wikiUrls) {
        this.npcHead = npcHead != null ? npcHead : ItemStack.EMPTY;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.island = island != null ? island : "";
        this.x = x;
        this.y = y;
        this.z = z;
        this.loreLines = loreLines != null ? loreLines : new String[0];
        this.wikiUrls = wikiUrls;
    }

    @Override
    public void writeToTag(CompoundTag tag) {
        RecipeTagCodec.writeStack(tag, "head", npcHead);
        tag.putString("npc", npcId);
        tag.putString("displayName", npcDisplayName);
        tag.putString("island", island);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        RecipeTagCodec.writeStringArray(tag, "lore", loreLines);
        RecipeTagCodec.writeWikiUrls(tag, wikiUrls);
    }

    @Override
    public void loadFromTag(CompoundTag tag) {
        npcHead        = RecipeTagCodec.readStack(tag, "head");
        npcId          = tag.getStringOr("npc", "");
        npcDisplayName = tag.getStringOr("displayName", "");
        island         = tag.getStringOr("island", "");
        x              = tag.getIntOr("x", 0);
        y              = tag.getIntOr("y", 0);
        z              = tag.getIntOr("z", 0);

        ListTag loreTag = tag.getListOrEmpty("lore");
        loreLines = RecipeTagCodec.readStringArray(tag, "lore", loreTag.size());

        wikiUrls = RecipeTagCodec.readWikiUrls(tag);
    }

    @Override
    public ReliableServerRecipeType<? extends ReliableServerRecipe> getRecipeType() {
        return TYPE;
    }

    public ItemStack getNpcHead()        { return npcHead; }
    public String    getNpcId()          { return npcId; }
    public String    getNpcDisplayName() { return npcDisplayName; }
    public String    getIsland()         { return island; }
    public int       getX()              { return x; }
    public int       getY()              { return y; }
    public int       getZ()              { return z; }
    public String[]  getLoreLines()      { return loreLines; }
    public String[]  getWikiUrls()       { return wikiUrls; }
}