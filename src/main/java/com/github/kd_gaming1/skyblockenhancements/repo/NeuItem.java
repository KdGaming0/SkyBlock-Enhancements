package com.github.kd_gaming1.skyblockenhancements.repo;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Lightweight representation of a single NEU repo item. Stored in-memory and serialized to
 * the consolidated cache. No Minecraft types — those are created lazily by {@link
 * ItemStackBuilder}.
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

    /** Legacy 3×3 crafting recipe (keys A1–C3, values "INTERNAL_NAME:count" or ""). */
    public Map<String, String> recipe;

    /** Modern recipes array, stored as raw JSON for extensibility across recipe types. */
    public List<JsonObject> recipes;

    public boolean hasCraftingRecipe() {
        if (recipe != null && !recipe.isEmpty()) return true;
        if (recipes == null) return false;
        return recipes.stream()
                .anyMatch(
                        r -> r.has("type") && "crafting".equals(r.get("type").getAsString()));
    }

    public boolean hasForgeRecipe() {
        if (recipes == null) return false;
        return recipes.stream()
                .anyMatch(r -> r.has("type") && "forge".equals(r.get("type").getAsString()));
    }
}