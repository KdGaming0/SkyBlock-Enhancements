package com.github.kd_gaming1.skyblockenhancements.repo;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Lightweight representation of a single NEU repo item. Stored in-memory and serialized to the
 * consolidated cache. Free of Minecraft types — those are created lazily by {@link ItemStackBuilder}.
 */
public class NeuItem {

    public String internalName;
    public String itemId;
    public String displayName;
    public int damage;
    public List<String> lore;
    public String itemModel;
    public String skullTexture;
    public int leatherColor = -1;

    /**
     * Filter category resolved from structural checks and lore-type parsing.
     * {@code null} for items that don't fit any defined category bucket.
     * Populated eagerly during parsing and cached with the item data.
     */
    public transient SkyblockItemCategory category;

    /**
     * Modern item ID from the companion {@code .snbt} file (e.g. {@code "minecraft:cod"}).
     * Takes priority over {@link #itemId} + {@link #damage} because SNBT files reflect the
     * actual 1.21 item, not a legacy numeric ID.
     */
    public String snbtItemId;

    /** Legacy 3×3 crafting recipe (keys A1–C3, values "INTERNAL_NAME:count" or ""). */
    public Map<String, String> recipe;

    /** Modern recipes array, stored as raw JSON for extensibility across recipe types. */
    public List<JsonObject> recipes;

    // ── NPC location data (only present on _NPC items) ──────────────────────────

    /** Internal island identifier, e.g. {@code "crimson_isle"}, {@code "hub"}. */
    public String island;

    public int x;
    public int y;
    public int z;

    /**
     * When {@code "WIKI_URL"}, the {@link #info} list contains clickable wiki URLs
     * (fandom first, then the official Hypixel wiki).
     */
    public String infoType;

    /** External links for this item. Populated when {@link #infoType} is {@code "WIKI_URL"}. */
    public List<String> info;

    /**
     * Click command associated with this item (e.g. {@code "viewrecipe"}, {@code "viewpotion"}).
     * Used for structural category detection — potions use {@code "viewpotion"}.
     */
    public String clickcommand;

    /**
     * Parent item ID from the NEU repo's {@code parent} field (e.g. {@code "ENCHANTED_DIAMOND"}).
     * Links this item to a parent in the item family hierarchy. Only present on ~469 items.
     */
    public String parent;

    /**
     * Whether this item should render with an enchantment glint, as specified by
     * {@code "minecraft:enchantment_glint_override": 1b} in the companion .snbt file.
     */
    public boolean enchantmentGlint = false;

    // ── Recipe queries ───────────────────────────────────────────────────────────

    public boolean hasCraftingRecipe() {
        if (recipe != null && !recipe.isEmpty()) return true;
        return hasRecipeOfType("crafting");
    }

    public boolean hasForgeRecipe() {
        return hasRecipeOfType("forge");
    }

    public boolean hasNpcShopRecipes() {
        return hasRecipeOfType("npc_shop");
    }

    /**
     * Returns {@code true} if this item has any recipe data at all — a legacy crafting grid
     * or at least one entry in the modern recipes array.
     */
    public boolean hasAnyRecipe() {
        if (recipe != null && !recipe.isEmpty()) return true;
        return recipes != null && !recipes.isEmpty();
    }

    public boolean hasWikiUrls() {
        return "WIKI_URL".equals(infoType) && info != null && !info.isEmpty();
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    /**
     * Scans the modern recipes array for at least one entry matching the given type string.
     */
    private boolean hasRecipeOfType(String type) {
        if (recipes == null) return false;
        for (JsonObject r : recipes) {
            if (r.has("type") && type.equals(r.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }
}