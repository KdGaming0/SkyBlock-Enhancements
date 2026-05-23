package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable data model for a single garden mutation layout.
 *
 * <p>Parsed once from {@code mutations.json} and stored in {@link GardenMutationRegistry}.
 * All fields are final; instances are safe to share across threads.
 *
 * <p>Multi-block crop sizes are looked up from a static {@code cropSizes} table
 * that is populated once during mod initialisation (see {@link #setGlobalCropSizes}).
 * Crops not listed in the table default to 1×1.
 */
public final class GardenMutationLayout {

    public enum CellType {
        EMPTY,
        TARGET,
        INGREDIENT
    }

    /** A single cell in the mutation grid. */
    public record Cell(CellType type, @Nullable String itemId) {
        public static final Cell EMPTY = new Cell(CellType.EMPTY, null);
        public static final Cell TARGET = new Cell(CellType.TARGET, null);
    }

    public record SpreadingCondition(@Nullable String itemId, int count, String text) {}

    public record Effect(String name, String description) {}

    /** Physical size of a crop in blocks (width × height). */
    public record CropSize(int width, int height) {
        /** True when the crop occupies more than a single block. */
        public boolean isMultiblock() {
            return width > 1 || height > 1;
        }
    }

    // ── Global crop-size table (set once during registry init) ────────────────

    private static Map<String, CropSize> globalCropSizes = Map.of();

    /**
     * Sets the global crop-size lookup table. Must be called once before
     * any {@link GardenMutationLayout} is parsed — typically during
     * {@link GardenMutationRegistry} initialisation.
     */
    public static void setGlobalCropSizes(Map<String, CropSize> sizes) {
        globalCropSizes = Map.copyOf(sizes);
    }

    /** Parses a {@code cropSizes} JsonObject into a {@code Map<String, CropSize>}. */
    public static Map<String, CropSize> parseCropSizes(@Nullable JsonObject obj) {
        if (obj == null) return Map.of();
        Map<String, CropSize> map = new HashMap<>(obj.size());
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonObject sizeObj = entry.getValue().getAsJsonObject();
            int w = sizeObj.has("width")  ? sizeObj.get("width").getAsInt()  : 1;
            int h = sizeObj.has("height") ? sizeObj.get("height").getAsInt() : 1;
            map.put(entry.getKey(), new CropSize(w, h));
        }
        return Collections.unmodifiableMap(map);
    }

    /** Returns the physical size of a crop, or {@code null} if it defaults to 1×1. */
    @Nullable
    public static CropSize cropSize(@Nullable String cropId) {
        return cropId != null ? globalCropSizes.get(cropId) : null;
    }

    // ── Instance fields ───────────────────────────────────────────────────────

    private final String mutationId;
    private final String name;
    private final String rarity;
    private final int gridSize;
    private final String surface;
    private final boolean needsWater;
    private final int stages;
    private final long costCoins;
    private final int rewardCopper;
    private final Cell[] grid;
    private final List<SpreadingCondition> spreadingConditions;
    private final List<Effect> effects;
    private final List<String> requiredFor;
    @Nullable private final String specialMechanic;

    private GardenMutationLayout(
            String mutationId,
            String name,
            String rarity,
            int gridSize,
            String surface,
            boolean needsWater,
            int stages,
            long costCoins,
            int rewardCopper,
            Cell[] grid,
            List<SpreadingCondition> spreadingConditions,
            List<Effect> effects,
            List<String> requiredFor,
            @Nullable String specialMechanic) {
        this.mutationId = mutationId;
        this.name = name;
        this.rarity = rarity;
        this.gridSize = gridSize;
        this.surface = surface;
        this.needsWater = needsWater;
        this.stages = stages;
        this.costCoins = costCoins;
        this.rewardCopper = rewardCopper;
        this.grid = grid;
        this.spreadingConditions = spreadingConditions;
        this.effects = effects;
        this.requiredFor = requiredFor;
        this.specialMechanic = specialMechanic;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    static GardenMutationLayout fromJson(String mutationId, JsonObject root) {
        String name = getString(root, "name", mutationId);
        String rarity = getString(root, "rarity", "COMMON");
        int gridSize = root.getAsJsonPrimitive("gridSize").getAsInt();
        String surface = getString(root, "surface", "Farmland");
        boolean needsWater = root.has("needsWater") && root.get("needsWater").getAsBoolean();
        int stages = root.has("stages") ? root.get("stages").getAsInt() : 0;
        long costCoins = root.has("costCoins") ? root.get("costCoins").getAsLong() : 0L;
        int rewardCopper = root.has("rewardCopper") ? root.get("rewardCopper").getAsInt() : 0;

        Cell[] grid = parseGrid(root.getAsJsonArray("layout"), gridSize);
        List<SpreadingCondition> conditions = parseConditions(root.getAsJsonArray("spreadingConditions"));
        List<Effect> effects = parseEffects(root.getAsJsonArray("effects"));
        List<String> requiredFor = parseStringArray(root.getAsJsonArray("requiredFor"));
        String specialMechanic = parseNullableString(root, "specialMechanic");

        return new GardenMutationLayout(
                mutationId, name, rarity, gridSize, surface, needsWater,
                stages, costCoins, rewardCopper, grid, conditions, effects, requiredFor,
                specialMechanic);
    }

    private static Cell[] parseGrid(JsonArray layoutArray, int gridSize) {
        Cell[] cells = new Cell[81]; // Always 9×9, padded with EMPTY
        for (int i = 0; i < 81; i++) {
            cells[i] = Cell.EMPTY;
        }

        int offset = (9 - gridSize) / 2;
        for (int row = 0; row < gridSize && row < layoutArray.size(); row++) {
            JsonArray rowArray = layoutArray.get(row).getAsJsonArray();
            for (int col = 0; col < gridSize && col < rowArray.size(); col++) {
                String cellStr = rowArray.get(col).getAsString();
                int destRow = offset + row;
                int destCol = offset + col;
                cells[destRow * 9 + destCol] = parseCell(cellStr);
            }
        }
        return cells;
    }

    private static Cell parseCell(String raw) {
        if (raw == null || raw.isEmpty() || "EMPTY".equals(raw)) {
            return Cell.EMPTY;
        }
        if ("TARGET".equals(raw)) {
            return Cell.TARGET;
        }
        if (raw.startsWith("INGREDIENT:")) {
            return new Cell(CellType.INGREDIENT, raw.substring(11));
        }
        // Fallback: treat unknown as ingredient with full string
        return new Cell(CellType.INGREDIENT, raw);
    }

    private static List<SpreadingCondition> parseConditions(@Nullable JsonArray arr) {
        if (arr == null) return List.of();
        List<SpreadingCondition> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String itemId = obj.has("itemId") && !obj.get("itemId").isJsonNull()
                    ? obj.get("itemId").getAsString() : null;
            int count = obj.has("count") ? obj.get("count").getAsInt() : 0;
            String text = getString(obj, "text", "");
            out.add(new SpreadingCondition(itemId, count, text));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Effect> parseEffects(@Nullable JsonArray arr) {
        if (arr == null) return List.of();
        List<Effect> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String name = getString(obj, "name", "");
            String description = getString(obj, "description", "");
            out.add(new Effect(name, description));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<String> parseStringArray(@Nullable JsonArray arr) {
        if (arr == null) return List.of();
        List<String> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            out.add(el.getAsString());
        }
        return Collections.unmodifiableList(out);
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (!obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        return el.isJsonNull() ? fallback : el.getAsString();
    }

    /** Returns the string value for {@code key}, or {@code null} if absent or JSON null. */
    @Nullable
    private static String parseNullableString(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return el.isJsonNull() ? null : el.getAsString();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String mutationId() { return mutationId; }
    public String name() { return name; }
    public String rarity() { return rarity; }
    public int gridSize() { return gridSize; }
    public String surface() { return surface; }
    public boolean needsWater() { return needsWater; }
    public int stages() { return stages; }
    public long costCoins() { return costCoins; }
    public int rewardCopper() { return rewardCopper; }
    public Cell[] grid() { return grid; }
    public List<SpreadingCondition> spreadingConditions() { return spreadingConditions; }
    public List<Effect> effects() { return effects; }
    public List<String> requiredFor() { return requiredFor; }

    /** The special growth/harvest mechanic for this mutation, or {@code null} if none. */
    @Nullable
    public String specialMechanic() { return specialMechanic; }

    /**
     * Returns the number of grid cells that contain the given ingredient item ID.
     * Used by tooltips to show the quantity of an ingredient in the layout.
     */
    public int countIngredientOccurrences(String itemId) {
        if (itemId == null || itemId.isEmpty()) return 0;
        int count = 0;
        for (Cell cell : grid) {
            if (cell.type() == CellType.INGREDIENT && itemId.equals(cell.itemId())) {
                count++;
            }
        }
        return count;
    }
}
