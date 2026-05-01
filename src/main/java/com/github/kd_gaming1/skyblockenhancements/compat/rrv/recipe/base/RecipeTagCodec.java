package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import cc.cassian.rrv.api.TagUtil;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Shared NBT serialization primitives for {@link cc.cassian.rrv.api.recipe.ReliableServerRecipe}
 * implementations. Centralises the encode/decode dance so every recipe doesn't repeat it.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Empty / null slots are omitted from the tag rather than written as empty.</li>
 *   <li>Arrays use {@code <countKey>} + {@code <prefix><i>} to preserve ordering and sparsity.</li>
 *   <li>Read paths clamp against {@code maxSize} so malformed or oversized payloads never allocate
 *       unbounded buffers.</li>
 * </ul>
 */
public final class RecipeTagCodec {

    // ── Common NBT keys ─────────────────────────────────────────────────────────

    public static final String KEY_INPUTS      = "in";
    public static final String KEY_OUTPUT      = "out";
    public static final String KEY_COST        = "cost";
    public static final String KEY_DURATION    = "dur";
    public static final String KEY_TIME        = "time";
    public static final String KEY_COINS       = "coins";
    public static final String KEY_COUNT       = "count";
    public static final String KEY_MOB         = "mob";
    public static final String KEY_RENDER      = "render";
    public static final String KEY_CHANCES     = "chances";
    public static final String KEY_NPC         = "npc";
    public static final String KEY_DISPLAY_NAME = "displayName";
    public static final String KEY_HEAD        = "head";
    public static final String KEY_ISLAND      = "island";
    public static final String KEY_X           = "x";
    public static final String KEY_Y           = "y";
    public static final String KEY_Z           = "z";
    public static final String KEY_LORE        = "lore";
    public static final String KEY_ITEM        = "item";
    public static final String KEY_NAME        = "name";
    public static final String KEY_ESSENCE     = "ess";
    public static final String KEY_STAR        = "star";
    public static final String KEY_ESSENCE_TYPE = "essType";
    public static final String KEY_WIKI_URLS   = "wiki";

    // Reforge recipe keys
    public static final String KEY_REFORGE_NAME   = "rfn";
    public static final String KEY_IS_BLACKSMITH  = "blk";
    public static final String KEY_STONE_NAME     = "stn";
    public static final String KEY_ITEM_RARITY    = "rar";
    public static final String KEY_STATS          = "sts";
    public static final String KEY_REFORGE_COST   = "cst";
    public static final String KEY_ABILITY        = "abl";
    public static final String KEY_ITEM_TYPE      = "itp";
    public static final String KEY_NBT_MODIFIER   = "nbt";

    // Array prefixes / count keys for recipe-specific slot arrays
    public static final String KEY_DROPS_PREFIX      = "d";
    public static final String KEY_COSTS_PREFIX      = "c";
    public static final String KEY_COMPANIONS_COUNT  = "compCount";
    public static final String KEY_COMPANIONS_PREFIX = "comp";
    public static final String KEY_MATERIALS_COUNT   = "matCount";
    public static final String KEY_MATERIALS_PREFIX  = "m";

    private RecipeTagCodec() {}

    // ── Single slots ─────────────────────────────────────────────────────────────

    public static void writeSlot(CompoundTag tag, String key, @Nullable SlotContent slot) {
        if (slot == null || slot.isEmpty()) return;
        tag.put(key, TagUtil.encodeItemStackOnServer(slot.getValidContents().getFirst()));
    }

    @Nullable
    public static SlotContent readSlot(CompoundTag tag, String key) {
        CompoundTag ct = tag.getCompoundOrEmpty(key);
        if (ct.isEmpty()) return null;
        return SlotContent.of(TagUtil.decodeItemStackOnClient(ct));
    }

    // ── Raw item stacks (for display-only payloads without slot semantics) ──────

    public static void writeStack(CompoundTag tag, String key, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        tag.put(key, TagUtil.encodeItemStackOnServer(stack));
    }

    public static ItemStack readStack(CompoundTag tag, String key) {
        CompoundTag ct = tag.getCompoundOrEmpty(key);
        return ct.isEmpty() ? ItemStack.EMPTY : TagUtil.decodeItemStackOnClient(ct);
    }

    // ── Slot arrays ──────────────────────────────────────────────────────────────

    public static void writeSlotArray(CompoundTag tag, String countKey, String prefix, SlotContent[] slots) {
        tag.putInt(countKey, slots.length);
        for (int i = 0; i < slots.length; i++) {
            writeSlot(tag, prefix + i, slots[i]);
        }
    }

    /**
     * Reads an array whose length was persisted under {@code countKey}, clamped to {@code maxSize}.
     * Missing indices remain {@code null} so callers can treat them as "empty slot".
     */
    public static SlotContent[] readSlotArray(CompoundTag tag, String countKey, String prefix, int maxSize) {
        int count = Math.min(tag.getIntOr(countKey, 0), maxSize);
        SlotContent[] out = new SlotContent[count];
        for (int i = 0; i < count; i++) {
            out[i] = readSlot(tag, prefix + i);
        }
        return out;
    }

    /** Variant for fixed-size grids (e.g. 3×3 crafting) where the length is known statically. */
    public static SlotContent[] readFixedSlotArray(CompoundTag tag, String prefix, int size) {
        SlotContent[] out = new SlotContent[size];
        for (int i = 0; i < size; i++) {
            out[i] = readSlot(tag, prefix + i);
        }
        return out;
    }

    public static void writeFixedSlotArray(CompoundTag tag, String prefix, SlotContent[] slots) {
        for (int i = 0; i < slots.length; i++) {
            writeSlot(tag, prefix + i, slots[i]);
        }
    }

    // ── String arrays ────────────────────────────────────────────────────────────

    public static void writeStringArray(CompoundTag tag, String key, String[] values) {
        ListTag list = new ListTag();
        for (String v : values) {
            list.add(StringTag.valueOf(v != null ? v : ""));
        }
        tag.put(key, list);
    }

    public static String[] readStringArray(CompoundTag tag, String key, int size) {
        ListTag list = tag.getListOrEmpty(key);
        String[] out = new String[size];
        for (int i = 0; i < size && i < list.size(); i++) {
            out[i] = list.get(i).asString().orElse("");
        }
        return out;
    }

    // ── Wiki URLs ────────────────────────────────────────────────────────────────

    /** Convenience for the universal wiki-URL field pattern. */
    public static void writeWikiUrls(CompoundTag tag, String[] urls) {
        SkyblockRecipeUtil.writeWikiUrls(tag, urls);
    }

    public static String[] readWikiUrls(CompoundTag tag) {
        return SkyblockRecipeUtil.readWikiUrls(tag);
    }
}