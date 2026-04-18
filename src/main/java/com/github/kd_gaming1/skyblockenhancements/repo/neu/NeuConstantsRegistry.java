package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.RepoDiskCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory store for parsed NEU repo {@code constants/*.json} data.
 *
 * <p>Currently models:
 * <ul>
 *   <li>{@code parents.json}       → parent→children family map + reverse index</li>
 *   <li>{@code essencecosts.json}  → per-item star upgrade costs ({@link EssenceUpgrade})</li>
 *   <li>{@code museum.json}        → wing→items map + reverse index</li>
 *   <li>{@code pets.json}          → pet ID → skill type</li>
 *   <li>{@code petnums.json}       → delegated to {@link PetStatResolver}</li>
 * </ul>
 *
 * <p>All state is replaced atomically via volatile publish; reads are lock-free.
 * Raw JSON for cache round-tripping lives in {@link RepoDiskCache}, not here.
 */
public final class NeuConstantsRegistry {

    // ── Parents ─────────────────────────────────────────────────────────────────

    private static volatile Map<String, List<String>> parentToChildren = Map.of();
    private static volatile Map<String, String> childToParent = Map.of();

    // ── Essence costs ───────────────────────────────────────────────────────────

    private static volatile Map<String, EssenceUpgrade> essenceUpgrades = Map.of();

    // ── Museum ──────────────────────────────────────────────────────────────────

    private static volatile Map<String, List<String>> museumCategories = Map.of();
    private static volatile Map<String, String> itemToMuseumWing = Map.of();

    // ── Pet types ───────────────────────────────────────────────────────────────

    private static volatile Map<String, String> petTypes = Map.of();

    private NeuConstantsRegistry() {}

    // ── Loaders ─────────────────────────────────────────────────────────────────

    /** Parses {@code { "PARENT_ID": ["CHILD_1", ...], ... }}. */
    public static void loadParents(JsonObject json) {
        Map<String, List<String>> p2c = new HashMap<>(json.size());
        Map<String, String> c2p = new HashMap<>(json.size() * 3);

        for (var entry : json.entrySet()) {
            JsonElement val = entry.getValue();
            if (!val.isJsonArray()) continue;

            String parentId = entry.getKey();
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
        LOGGER.info("Loaded {} parent→children mappings ({} total children)", p2c.size(), c2p.size());
    }

    /**
     * Parses the essence-costs file. Each entry:
     * <pre>{@code
     * "ITEM_ID": {
     *   "type": "Wither",
     *   "1": 10, "2": 25, ...,
     *   "items": { "4": ["SKYBLOCK_COIN:10000"], ... }
     * }
     * }</pre>
     */
    public static void loadEssenceCosts(JsonObject json) {
        Map<String, EssenceUpgrade> upgrades = new HashMap<>(json.size());

        for (var entry : json.entrySet()) {
            JsonElement val = entry.getValue();
            if (!val.isJsonObject()) continue;

            EssenceUpgrade parsed = parseEssenceUpgrade(val.getAsJsonObject());
            if (parsed != null) upgrades.put(entry.getKey(), parsed);
        }

        essenceUpgrades = Collections.unmodifiableMap(upgrades);
        LOGGER.info("Loaded {} essence upgrade entries", upgrades.size());
    }

    private static EssenceUpgrade parseEssenceUpgrade(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : null;
        if (type == null) return null;

        Map<Integer, Integer> starCosts = new HashMap<>();
        Map<Integer, List<String>> starItems = new HashMap<>();
        int maxStar = 0;

        for (var field : obj.entrySet()) {
            String key = field.getKey();
            if ("type".equals(key) || "items".equals(key)) continue;
            try {
                int star = Integer.parseInt(key);
                starCosts.put(star, field.getValue().getAsInt());
                if (star > maxStar) maxStar = star;
            } catch (NumberFormatException ignored) {
                // not a numeric star key
            }
        }

        if (obj.has("items") && obj.get("items").isJsonObject()) {
            for (var itemEntry : obj.getAsJsonObject("items").entrySet()) {
                try {
                    int star = Integer.parseInt(itemEntry.getKey());
                    JsonArray arr = itemEntry.getValue().getAsJsonArray();
                    List<String> refs = new ArrayList<>(arr.size());
                    for (JsonElement e : arr) refs.add(e.getAsString());
                    starItems.put(star, Collections.unmodifiableList(refs));
                } catch (NumberFormatException ignored) {
                    // not a numeric star key
                }
            }
        }

        if (starCosts.isEmpty()) return null;

        return new EssenceUpgrade(
                type,
                Collections.unmodifiableMap(starCosts),
                Collections.unmodifiableMap(starItems),
                maxStar);
    }

    /** Parses {@code { "items": { "combat": [...], "farming": [...], ... } }}. */
    public static void loadMuseum(JsonObject json) {
        if (!json.has("items") || !json.get("items").isJsonObject()) {
            LOGGER.warn("museum.json missing 'items' object");
            return;
        }

        JsonObject items = json.getAsJsonObject("items");
        Map<String, List<String>> cats = new HashMap<>(items.size());
        Map<String, String> reverse = new HashMap<>(1024);

        for (var entry : items.entrySet()) {
            JsonElement val = entry.getValue();
            if (!val.isJsonArray()) continue;

            String wing = entry.getKey();
            List<String> ids = new ArrayList<>();
            for (JsonElement e : val.getAsJsonArray()) {
                String id = e.getAsString();
                ids.add(id);
                reverse.put(id, wing);
            }
            cats.put(wing, Collections.unmodifiableList(ids));
        }

        museumCategories = Collections.unmodifiableMap(cats);
        itemToMuseumWing = Collections.unmodifiableMap(reverse);
        LOGGER.info("Loaded {} museum wings ({} total items)", cats.size(), reverse.size());
    }

    /** Parses {@code { "pet_types": { "ROCK": "MINING", ... } }}. */
    public static void loadPetTypes(JsonObject json) {
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

    /** Delegates to {@link PetStatResolver#load}. */
    public static void loadPetNums(JsonObject json) {
        PetStatResolver.load(json);
    }

    /** Clears all loaded data. */
    public static void clear() {
        parentToChildren = Map.of();
        childToParent = Map.of();
        essenceUpgrades = Map.of();
        museumCategories = Map.of();
        itemToMuseumWing = Map.of();
        petTypes = Map.of();
        PetStatResolver.clear();
    }

    // ── Queries ─────────────────────────────────────────────────────────────────

    public static List<String> getChildren(String parentId) {
        return parentToChildren.getOrDefault(parentId, List.of());
    }

    public static String getParent(String childId) {
        return childToParent.get(childId);
    }

    public static boolean isChild(String itemId) {
        return childToParent.containsKey(itemId);
    }

    public static EssenceUpgrade getEssenceUpgrade(String itemId) {
        return essenceUpgrades.get(itemId);
    }

    public static Map<String, EssenceUpgrade> getAllEssenceUpgrades() {
        return essenceUpgrades;
    }

    public static String getMuseumWing(String itemId) {
        return itemToMuseumWing.get(itemId);
    }

    public static String getPetType(String petId) {
        return petTypes.get(petId);
    }

    public static boolean isLoaded() {
        return !parentToChildren.isEmpty() || !essenceUpgrades.isEmpty();
    }

    // ── Data record ─────────────────────────────────────────────────────────────

    /**
     * Immutable per-item essence upgrade data.
     *
     * @param essenceType label (e.g. {@code "Wither"}, {@code "Ice"})
     * @param starCosts   star level → essence amount required
     * @param starItems   star level → companion item refs (e.g. {@code "SKYBLOCK_COIN:10000"})
     * @param maxStar     highest defined star (not necessarily {@code starCosts.size()})
     */
    public record EssenceUpgrade(
            String essenceType,
            Map<Integer, Integer> starCosts,
            Map<Integer, List<String>> starItems,
            int maxStar) {

        public int getCost(int star) {
            return starCosts.getOrDefault(star, 0);
        }

        public List<String> getItems(int star) {
            return starItems.getOrDefault(star, List.of());
        }
    }
}