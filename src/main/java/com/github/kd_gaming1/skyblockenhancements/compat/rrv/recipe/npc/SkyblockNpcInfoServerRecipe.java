package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
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
public class SkyblockNpcInfoServerRecipe extends AbstractSkyblockServerRecipe {

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

    public SkyblockNpcInfoServerRecipe(ItemStack npcHead, String npcId, String npcDisplayName,
                                       String island, int x, int y, int z,
                                       String[] loreLines, String[] wikiUrls) {
        super(wikiUrls);
        this.npcHead = npcHead != null ? npcHead : ItemStack.EMPTY;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.island = island != null ? island : "";
        this.x = x;
        this.y = y;
        this.z = z;
        this.loreLines = loreLines != null ? loreLines : new String[0];
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        RecipeTagCodec.writeStack(tag, RecipeTagCodec.KEY_HEAD, npcHead);
        tag.putString(RecipeTagCodec.KEY_NPC, npcId);
        tag.putString(RecipeTagCodec.KEY_DISPLAY_NAME, npcDisplayName);
        tag.putString(RecipeTagCodec.KEY_ISLAND, island);
        tag.putInt(RecipeTagCodec.KEY_X, x);
        tag.putInt(RecipeTagCodec.KEY_Y, y);
        tag.putInt(RecipeTagCodec.KEY_Z, z);
        RecipeTagCodec.writeStringArray(tag, RecipeTagCodec.KEY_LORE, loreLines);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        npcHead        = RecipeTagCodec.readStack(tag, RecipeTagCodec.KEY_HEAD);
        npcId          = tag.getStringOr(RecipeTagCodec.KEY_NPC, "");
        npcDisplayName = tag.getStringOr(RecipeTagCodec.KEY_DISPLAY_NAME, "");
        island         = tag.getStringOr(RecipeTagCodec.KEY_ISLAND, "");
        x              = tag.getIntOr(RecipeTagCodec.KEY_X, 0);
        y              = tag.getIntOr(RecipeTagCodec.KEY_Y, 0);
        z              = tag.getIntOr(RecipeTagCodec.KEY_Z, 0);

        ListTag loreTag = tag.getListOrEmpty(RecipeTagCodec.KEY_LORE);
        loreLines = RecipeTagCodec.readStringArray(tag, RecipeTagCodec.KEY_LORE, loreTag.size());
    }

    public ItemStack getNpcHead()        { return npcHead; }
    public String    getNpcId()          { return npcId; }
    public String    getNpcDisplayName() { return npcDisplayName; }
    public String    getIsland()         { return island; }
    public int       getX()              { return x; }
    public int       getY()              { return y; }
    public int       getZ()              { return z; }
    public String[]  getLoreLines()      { return loreLines; }
}
