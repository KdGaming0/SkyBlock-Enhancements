package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses individual NEU repo entries into {@link NeuItem} POJOs. Handles both
 * {@code items/*.json} files and {@code itemsOverlay/*.snbt} companion files.
 *
 * <p>Stateless — all methods are static and side-effect-free. Category and rarity
 * resolution is performed separately after all items are parsed.
 */
public final class NeuItemParser {

    public static final Gson GSON = new GsonBuilder().create();

    private static final Pattern ITEM_MODEL_PATTERN = Pattern.compile("ItemModel:\"([^\"]+)\"");
    private static final Pattern TEXTURE_VALUE_PATTERN = Pattern.compile("Value:\"([^\"]+)\"");
    private static final Pattern DISPLAY_COLOR_PATTERN = Pattern.compile("display:\\{.*?color:(\\d+)");

    static final Pattern SNBT_ID_PATTERN =
            Pattern.compile("^\\s*id:\\s*\"(minecraft:[^\"]+)\"", Pattern.MULTILINE);

    static final Pattern SNBT_GLINT_PATTERN =
            Pattern.compile("\"minecraft:enchantment_glint_override\"\\s*:\\s*1b");

    private NeuItemParser() {}

    // ── JSON → NeuItem ──────────────────────────────────────────────────────────

    /**
     * Parses a single {@code items/*.json} entry into a {@link NeuItem}.
     * Does not resolve category/rarity — that happens in a batch pass after all items are parsed.
     */
    public static NeuItem parseItemJson(String internalName, String raw) {
        JsonObject json = GSON.fromJson(raw, JsonObject.class);

        NeuItem item = new NeuItem();
        item.internalName = internalName;
        item.itemId = str(json, "itemid", "minecraft:barrier");
        item.displayName = str(json, "displayname", internalName);
        item.damage = json.has("damage") ? json.get("damage").getAsInt() : 0;

        if (json.has("lore") && json.get("lore").isJsonArray()) {
            item.lore = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("lore")) {
                item.lore.add(e.getAsString());
            }
        } else {
            item.lore = List.of();
        }

        if (json.has("nbttag")) {
            String nbtStr = json.get("nbttag").getAsString();
            Matcher m = ITEM_MODEL_PATTERN.matcher(nbtStr);
            item.itemModel = m.find() ? m.group(1) : null;

            Matcher texMatcher = TEXTURE_VALUE_PATTERN.matcher(nbtStr);
            if (texMatcher.find()) {
                StringBuilder b64 = new StringBuilder(texMatcher.group(1).replaceAll("[^A-Za-z0-9+/=]", ""));
                while (b64.length() % 4 != 0) b64.append("=");
                item.skullTexture = b64.toString();
            }

            Matcher colorMatcher = DISPLAY_COLOR_PATTERN.matcher(nbtStr);
            if (colorMatcher.find()) {
                item.leatherColor = Integer.parseInt(colorMatcher.group(1));
            }
        }

        item.island = str(json, "island", "");
        item.x = json.has("x") ? json.get("x").getAsInt() : 0;
        item.y = json.has("y") ? json.get("y").getAsInt() : 0;
        item.z = json.has("z") ? json.get("z").getAsInt() : 0;
        item.infoType = str(json, "infoType", "");

        if (json.has("info") && json.get("info").isJsonArray()) {
            item.info = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("info")) {
                item.info.add(e.getAsString());
            }
        } else {
            item.info = List.of();
        }

        item.clickcommand = str(json, "clickcommand", "");
        item.parent = json.has("parent") ? json.get("parent").getAsString() : null;

        // Requirement metadata
        item.crafttext = str(json, "crafttext", "");
        item.slayerReq = str(json, "slayer_req", "");

        if (json.has("recipe") && json.get("recipe").isJsonObject()) {
            item.recipe = new LinkedHashMap<>();
            for (var e : json.getAsJsonObject("recipe").entrySet()) {
                item.recipe.put(e.getKey(), e.getValue().getAsString());
            }
        }

        if (json.has("recipes") && json.get("recipes").isJsonArray()) {
            item.recipes = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("recipes")) {
                item.recipes.add(e.getAsJsonObject().deepCopy());
            }
        }

        return item;
    }

    // ── SNBT parsing ────────────────────────────────────────────────────────────

    /**
     * Extracts the modern item ID (e.g. {@code "minecraft:cod"}) from a {@code .snbt} file.
     *
     * @return the item ID, or {@code null} if not found
     */
    public static String parseSnbtId(String snbt) {
        Matcher m = SNBT_ID_PATTERN.matcher(snbt);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Returns {@code true} if the SNBT content contains an enchantment glint override.
     */
    public static boolean hasGlintOverride(String snbt) {
        return SNBT_GLINT_PATTERN.matcher(snbt).find();
    }

    // ── Category + rarity resolution ────────────────────────────────────────────

    /**
     * Resolves both {@link SkyblockItemCategory} and {@link SkyblockRarity} on every
     * item, once, at parse time. Downstream consumers can treat these fields as stable
     * (though still nullable for items that genuinely have no category or rarity).
     */
    public static void resolveAllCategoryAndRarity(Map<String, NeuItem> items) {
        for (NeuItem item : items.values()) {
            item.category = SkyblockItemCategory.fromNeuItem(item);
            item.rarity = SkyblockItemCategory.extractRarity(item);
        }
    }

    // ── Pet stat placeholder resolution ─────────────────────────────────────────

    /**
     * Resolves {@code {STAT_NAME}} and {@code {N}} placeholders in pet lore using
     * level-100 values from {@code petnums.json}. Must be called after constants are
     * loaded and after category resolution (so we can identify pets).
     */
    public static void resolvePetStats(Map<String, NeuItem> items) {
        if (!PetStatResolver.isLoaded()) {
            LOGGER.warn("petnums not loaded — pet stat placeholders will remain unresolved");
            return;
        }

        int resolved = 0;
        for (NeuItem item : items.values()) {
            if (item.category != SkyblockItemCategory.PET) continue;
            PetStatResolver.resolve(item);
            resolved++;
        }
        LOGGER.info("Resolved stat placeholders on {} pet items", resolved);
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    static String str(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }
}