package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores parsed data from NEU repo {@code constants/} files. Loaded during the ZIP
 * stream-parse in {@link NeuRepoDownloader} alongside item data.
 *
 * <p>Currently loads:
 * <ul>
 *   <li>{@code parents.json} — parent→children item family mappings (690 entries)</li>
 *   <li>{@code essencecosts.json} — star upgrade costs for dungeon/crimson gear (528 entries)</li>
 *   <li>{@code museum.json} → {@code items} — skill-based museum categories (937 items)</li>
 *   <li>{@code pets.json} → {@code pet_types} — pet skill type mappings (73 entries)</li>
 * </ul>
 *
 * <p>All data is immutable after loading. Thread-safe reads are guaranteed by volatile publication.
 */
public final class NeuConstantsRegistry {

    // ── Parents ─────────────────────────────────────────────────────────────────

    /** Parent ID → list of child IDs (e.g. {@code "ACACIA_GENERATOR_1" → [_2, _3, ..., _11]}). */
    private static volatile Map<String, List<String>> parentToChildren = Map.of();

    /** Child ID → parent ID reverse index for O(1) lookups. */
    private static volatile Map<String, String> childToParent = Map.of();

    // ── Essence costs ───────────────────────────────────────────────────────────

    /** Item ID → essence upgrade data. */
    private static volatile Map<String, EssenceUpgrade> essenceUpgrades = Map.of();

    // ── Museum ──────────────────────────────────────────────────────────────────

    /** Museum wing name → list of item IDs in that wing. */
    private static volatile Map<String, List<String>> museumCategories = Map.of();

    /** Item ID → museum wing name (reverse index). */
    private static volatile Map<String, String> itemToMuseumWing = Map.of();

    // ── Pet types ───────────────────────────────────────────────────────────────

    /** Pet ID → skill type (e.g. {@code "ROCK" → "MINING"}). */
    private static volatile Map<String, String> petTypes = Map.of();

    // ── Raw JSON for cache persistence ──────────────────────────────────────────

    /** Raw JSON objects kept for re-serialization into the disk cache. */
    private static volatile JsonObject rawParents;
    private static volatile JsonObject rawEssenceCosts;
    private static volatile JsonObject rawMuseum;
    private static volatile JsonObject rawPets;

    private NeuConstantsRegistry() {}

    // ── Loading ─────────────────────────────────────────────────────────────────

    /**
     * Parses {@code constants/parents.json}. Structure: {@code { "PARENT_ID": ["CHILD_1", ...] }}.
     */
    public static void loadParents(JsonObject json) {
        rawParents = json;

        Map<String, List<String>> p2c = new HashMap<>(json.size());
        Map<String, String> c2p = new HashMap<>(json.size() * 3);

        for (var entry : json.entrySet()) {
            String parentId = entry.getKey();
            JsonElement val = entry.getValue();
            if (!val.isJsonArray()) continue;

            List<String> children = new ArrayList<>();
            for (JsonElement child : val.getAsJsonArray()) {
                String childId = child.getAsString();
                children.add(childId);
                c2p.put(childId, parentId);
            }
            if (!children.isEmpty()) {
                p2c.put(parentId, Collections.unmodifiableList(children));
            }
        }

        parentToChildren = Collections.unmodifiableMap(p2c);
        childToParent = Collections.unmodifiableMap(c2p);
        LOGGER.info("Loaded {} parent→children mappings ({} total children)",
                p2c.size(), c2p.size());
    }

    /**
     * Parses {@code constants/essencecosts.json}. Structure per entry:
     * <pre>{@code
     * "ITEM_ID": {
     *   "type": "Wither",
     *   "1": 10, "2": 25, ...,
     *   "items": { "4": ["SKYBLOCK_COIN:10000"], "5": ["SKYBLOCK_COIN:25000", "WITHER_SOUL:50"] }
     * }
     * }</pre>
     */
    public static void loadEssenceCosts(JsonObject json) {
        rawEssenceCosts = json;

        Map<String, EssenceUpgrade> upgrades = new HashMap<>(json.size());

        for (var entry : json.entrySet()) {
            String itemId = entry.getKey();
            JsonElement val = entry.getValue();
            if (!val.isJsonObject()) continue;

            JsonObject obj = val.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : null;
            if (type == null) continue;

            // Collect star levels and their essence costs
            Map<Integer, Integer> starCosts = new HashMap<>();
            Map<Integer, List<String>> starItems = new HashMap<>();
            int maxStar = 0;

            for (var field : obj.entrySet()) {
                String key = field.getKey();
                if ("type".equals(key) || "items".equals(key)) continue;

                try {
                    int star = Integer.parseInt(key);
                    int cost = field.getValue().getAsInt();
                    starCosts.put(star, cost);
                    if (star > maxStar) maxStar = star;
                } catch (NumberFormatException ignored) {
                    // Skip non-numeric keys we don't recognize
                }
            }

            // Parse companion items per star level
            if (obj.has("items") && obj.get("items").isJsonObject()) {
                for (var itemEntry : obj.getAsJsonObject("items").entrySet()) {
                    try {
                        int star = Integer.parseInt(itemEntry.getKey());
                        JsonArray arr = itemEntry.getValue().getAsJsonArray();
                        List<String> refs = new ArrayList<>(arr.size());
                        for (JsonElement e : arr) {
                            refs.add(e.getAsString());
                        }
                        starItems.put(star, Collections.unmodifiableList(refs));
                    } catch (NumberFormatException ignored) {
                        // Skip non-numeric item keys
                    }
                }
            }

            if (!starCosts.isEmpty()) {
                upgrades.put(itemId, new EssenceUpgrade(
                        type,
                        Collections.unmodifiableMap(starCosts),
                        Collections.unmodifiableMap(starItems),
                        maxStar));
            }
        }

        essenceUpgrades = Collections.unmodifiableMap(upgrades);
        LOGGER.info("Loaded {} essence upgrade entries", upgrades.size());
    }

    /**
     * Parses {@code constants/museum.json}. Only reads the {@code "items"} sub-object.
     * Structure: {@code { "items": { "combat": ["ITEM_1", ...], "farming": [...] } }}.
     */
    public static void loadMuseum(JsonObject json) {
        rawMuseum = json;

        if (!json.has("items") || !json.get("items").isJsonObject()) {
            LOGGER.warn("museum.json missing 'items' object");
            return;
        }

        JsonObject items = json.getAsJsonObject("items");
        Map<String, List<String>> cats = new HashMap<>(items.size());
        Map<String, String> reverse = new HashMap<>(1024);

        for (var entry : items.entrySet()) {
            String wing = entry.getKey();
            JsonElement val = entry.getValue();
            if (!val.isJsonArray()) continue;

            List<String> itemIds = new ArrayList<>();
            for (JsonElement e : val.getAsJsonArray()) {
                String id = e.getAsString();
                itemIds.add(id);
                reverse.put(id, wing);
            }
            cats.put(wing, Collections.unmodifiableList(itemIds));
        }

        museumCategories = Collections.unmodifiableMap(cats);
        itemToMuseumWing = Collections.unmodifiableMap(reverse);
        LOGGER.info("Loaded {} museum wings ({} total items)",
                cats.size(), reverse.size());
    }

    /**
     * Parses {@code constants/pets.json}. Only reads the {@code "pet_types"} sub-object.
     * Structure: {@code { "pet_types": { "ROCK": "MINING", "BAT": "MINING", ... } }}.
     */
    public static void loadPetTypes(JsonObject json) {
        rawPets = json;

        if (!json.has("pet_types") || !json.get("pet_types").isJsonObject()) {
            LOGGER.warn("pets.json missing 'pet_types' object");
            return;
        }

        JsonObject types = json.getAsJsonObject("pet_types");
        Map<String, String> map = new HashMap<>(types.size());
        for (var entry : types.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsString());
        }

        petTypes = Collections.unmodifiableMap(map);
        LOGGER.info("Loaded {} pet type mappings", map.size());
    }

    /** Clears all loaded constants data. Called when the NEU repo is re-downloaded. */
    public static void clear() {
        parentToChildren = Map.of();
        childToParent = Map.of();
        essenceUpgrades = Map.of();
        museumCategories = Map.of();
        itemToMuseumWing = Map.of();
        petTypes = Map.of();
        rawParents = null;
        rawEssenceCosts = null;
        rawMuseum = null;
        rawPets = null;
    }

    /**
     * Returns the raw JSON objects for cache serialization. Each key maps to the original
     * parsed {@link JsonObject} from the ZIP. Keys with {@code null} values are excluded.
     */
    public static JsonObject getRawConstantsForCache() {
        JsonObject obj = new JsonObject();
        if (rawParents != null) obj.add("parents", rawParents);
        if (rawEssenceCosts != null) obj.add("essencecosts", rawEssenceCosts);
        if (rawMuseum != null) obj.add("museum", rawMuseum);
        if (rawPets != null) obj.add("pets", rawPets);
        return obj;
    }

    // ── Queries ─────────────────────────────────────────────────────────────────

    /** Returns the children of a parent item, or an empty list if not a parent. */
    public static List<String> getChildren(String parentId) {
        return parentToChildren.getOrDefault(parentId, List.of());
    }

    /** Returns the parent of a child item, or {@code null} if not a child. */
    public static String getParent(String childId) {
        return childToParent.get(childId);
    }

    /** Returns {@code true} if the item is a child in any parent→children group. */
    public static boolean isChild(String itemId) {
        return childToParent.containsKey(itemId);
    }

    /** Returns the essence upgrade data for an item, or {@code null} if none. */
    public static EssenceUpgrade getEssenceUpgrade(String itemId) {
        return essenceUpgrades.get(itemId);
    }

    /** Returns the full essence upgrades map (immutable). */
    public static Map<String, EssenceUpgrade> getAllEssenceUpgrades() {
        return essenceUpgrades;
    }

    /** Returns the museum wing for an item (e.g. {@code "combat"}), or {@code null}. */
    public static String getMuseumWing(String itemId) {
        return itemToMuseumWing.get(itemId);
    }

    /** Returns the pet's skill type (e.g. {@code "MINING"}), or {@code null}. */
    public static String getPetType(String petId) {
        return petTypes.get(petId);
    }

    /** Returns {@code true} if any constants data has been loaded. */
    public static boolean isLoaded() {
        return !parentToChildren.isEmpty() || !essenceUpgrades.isEmpty();
    }

    // ── Data classes ────────────────────────────────────────────────────────────

    /**
     * Immutable essence upgrade data for a single item. Contains the essence type, per-star
     * costs, and optional companion item requirements per star level.
     */
    public record EssenceUpgrade(
            String essenceType,
            Map<Integer, Integer> starCosts,
            Map<Integer, List<String>> starItems,
            int maxStar) {

        /**
         * Returns the essence cost for a specific star level, or 0 if not defined.
         */
        public int getCost(int star) {
            return starCosts.getOrDefault(star, 0);
        }

        /**
         * Returns the companion item refs for a specific star level (e.g. {@code "SKYBLOCK_COIN:10000"}).
         */
        public List<String> getItems(int star) {
            return starItems.getOrDefault(star, List.of());
        }
    }
}