package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

/**
 * Server-side reforge template recipe. One recipe per reforge (blacksmith or stone).
 * The client dynamically resolves the viewed item's rarity and displays the matching stats.
 */
public class SkyblockReforgeServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockReforgeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_reforge"),
                    () -> new SkyblockReforgeServerRecipe(
                            "", true, "", "", List.of(),
                            Map.of(), Map.of(), Optional.empty(), Map.of(),
                            List.of(), List.of(), Optional.empty(), new String[0]));

    private String reforgeName;
    private boolean isBlacksmith;
    private String stoneInternalName;
    private String itemType;
    private List<String> requiredRarities;
    private Map<String, Map<String, Double>> stats;
    private Map<String, Integer> costs;
    private Optional<String> ability;
    private Map<String, String> abilitiesByRarity;
    private List<String> specificInternalNames;
    private List<String> specificItemIds;
    private Optional<String> nbtModifier;

    public SkyblockReforgeServerRecipe(
            String reforgeName, boolean isBlacksmith, String stoneInternalName,
            String itemType, List<String> requiredRarities,
            Map<String, Map<String, Double>> stats, Map<String, Integer> costs,
            Optional<String> ability, Map<String, String> abilitiesByRarity,
            List<String> specificInternalNames, List<String> specificItemIds,
            Optional<String> nbtModifier, String[] wikiUrls) {
        super(wikiUrls);
        this.reforgeName = reforgeName != null ? reforgeName : "";
        this.isBlacksmith = isBlacksmith;
        this.stoneInternalName = stoneInternalName != null ? stoneInternalName : "";
        this.itemType = itemType != null ? itemType : "";
        this.requiredRarities = requiredRarities != null ? List.copyOf(requiredRarities) : List.of();
        this.stats = stats != null ? deepCopyStats(stats) : Map.of();
        this.costs = costs != null ? Map.copyOf(costs) : Map.of();
        this.ability = ability;
        this.abilitiesByRarity = abilitiesByRarity != null ? Map.copyOf(abilitiesByRarity) : Map.of();
        this.specificInternalNames = specificInternalNames != null ? List.copyOf(specificInternalNames) : List.of();
        this.specificItemIds = specificItemIds != null ? List.copyOf(specificItemIds) : List.of();
        this.nbtModifier = nbtModifier;
    }

    private static Map<String, Map<String, Double>> deepCopyStats(Map<String, Map<String, Double>> src) {
        Map<String, Map<String, Double>> out = new HashMap<>();
        for (var e : src.entrySet()) {
            out.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        tag.putString(RecipeTagCodec.KEY_REFORGE_NAME, reforgeName);
        tag.putBoolean(RecipeTagCodec.KEY_IS_BLACKSMITH, isBlacksmith);
        tag.putString(RecipeTagCodec.KEY_STONE_NAME, stoneInternalName);
        tag.putString(RecipeTagCodec.KEY_ITEM_TYPE, itemType);
        writeStringList(tag, "rar", requiredRarities);
        writeStats(tag, RecipeTagCodec.KEY_STATS, stats);
        writeCosts(tag, RecipeTagCodec.KEY_REFORGE_COST, costs);
        if (ability.isPresent()) {
            tag.putString(RecipeTagCodec.KEY_ABILITY, ability.get());
        }
        writeAbilities(tag, "ablMap", abilitiesByRarity);
        writeStringList(tag, "specIn", specificInternalNames);
        writeStringList(tag, "specId", specificItemIds);
        if (nbtModifier.isPresent()) {
            tag.putString(RecipeTagCodec.KEY_NBT_MODIFIER, nbtModifier.get());
        }
    }

    @Override
    protected void readFields(CompoundTag tag) {
        reforgeName = tag.getStringOr(RecipeTagCodec.KEY_REFORGE_NAME, "");
        isBlacksmith = tag.getBooleanOr(RecipeTagCodec.KEY_IS_BLACKSMITH, true);
        stoneInternalName = tag.getStringOr(RecipeTagCodec.KEY_STONE_NAME, "");
        itemType = tag.getStringOr(RecipeTagCodec.KEY_ITEM_TYPE, "");
        requiredRarities = readStringList(tag, "rar");
        stats = readStats(tag, RecipeTagCodec.KEY_STATS);
        costs = readCosts(tag, RecipeTagCodec.KEY_REFORGE_COST);
        ability = tag.contains(RecipeTagCodec.KEY_ABILITY)
                ? Optional.of(tag.getStringOr(RecipeTagCodec.KEY_ABILITY, ""))
                : Optional.empty();
        abilitiesByRarity = readAbilities(tag, "ablMap");
        specificInternalNames = readStringList(tag, "specIn");
        specificItemIds = readStringList(tag, "specId");
        nbtModifier = tag.contains(RecipeTagCodec.KEY_NBT_MODIFIER)
                ? Optional.of(tag.getStringOr(RecipeTagCodec.KEY_NBT_MODIFIER, ""))
                : Optional.empty();
    }

    // ── NBT helpers ────────────────────────────────────────────────────────────

    private static void writeStringList(CompoundTag tag, String key, List<String> list) {
        ListTag arr = new ListTag();
        for (String s : list) arr.add(StringTag.valueOf(s));
        tag.put(key, arr);
    }

    private static List<String> readStringList(CompoundTag tag, String key) {
        ListTag list = tag.getListOrEmpty(key);
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i).asString().orElse("");
        }
        return List.of(out);
    }

    private static void writeStats(CompoundTag tag, String key, Map<String, Map<String, Double>> stats) {
        CompoundTag root = new CompoundTag();
        for (var rarityEntry : stats.entrySet()) {
            CompoundTag statTag = new CompoundTag();
            for (var statEntry : rarityEntry.getValue().entrySet()) {
                statTag.putDouble(statEntry.getKey(), statEntry.getValue());
            }
            root.put(rarityEntry.getKey(), statTag);
        }
        tag.put(key, root);
    }

    private static Map<String, Map<String, Double>> readStats(CompoundTag tag, String key) {
        CompoundTag root = tag.getCompoundOrEmpty(key);
        Map<String, Map<String, Double>> out = new HashMap<>();
        for (String rarityKey : root.keySet()) {
            CompoundTag statTag = root.getCompoundOrEmpty(rarityKey);
            Map<String, Double> statMap = new HashMap<>();
            for (String statKey : statTag.keySet()) {
                statMap.put(statKey, statTag.getDoubleOr(statKey, 0.0));
            }
            out.put(rarityKey, statMap);
        }
        return Collections.unmodifiableMap(out);
    }

    private static void writeCosts(CompoundTag tag, String key, Map<String, Integer> costs) {
        CompoundTag root = new CompoundTag();
        for (var e : costs.entrySet()) {
            root.putInt(e.getKey(), e.getValue());
        }
        tag.put(key, root);
    }

    private static Map<String, Integer> readCosts(CompoundTag tag, String key) {
        CompoundTag root = tag.getCompoundOrEmpty(key);
        Map<String, Integer> out = new HashMap<>();
        for (String k : root.keySet()) {
            out.put(k, root.getIntOr(k, 0));
        }
        return Collections.unmodifiableMap(out);
    }

    private static void writeAbilities(CompoundTag tag, String key, Map<String, String> abilities) {
        CompoundTag root = new CompoundTag();
        for (var e : abilities.entrySet()) {
            root.putString(e.getKey(), e.getValue());
        }
        tag.put(key, root);
    }

    private static Map<String, String> readAbilities(CompoundTag tag, String key) {
        CompoundTag root = tag.getCompoundOrEmpty(key);
        Map<String, String> out = new HashMap<>();
        for (String k : root.keySet()) {
            out.put(k, root.getStringOr(k, ""));
        }
        return Collections.unmodifiableMap(out);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getReforgeName()                    { return reforgeName; }
    public boolean isBlacksmith()                     { return isBlacksmith; }
    public String getStoneInternalName()              { return stoneInternalName; }
    public String getItemType()                       { return itemType; }
    public List<String> getRequiredRarities()         { return requiredRarities; }
    public Map<String, Map<String, Double>> getStats() { return stats; }
    public Map<String, Integer> getCosts()            { return costs; }
    public Optional<String> getAbility()              { return ability; }
    public Map<String, String> getAbilitiesByRarity() { return abilitiesByRarity; }
    public List<String> getSpecificInternalNames()    { return specificInternalNames; }
    public List<String> getSpecificItemIds()          { return specificItemIds; }
    public Optional<String> getNbtModifier()          { return nbtModifier; }
}
