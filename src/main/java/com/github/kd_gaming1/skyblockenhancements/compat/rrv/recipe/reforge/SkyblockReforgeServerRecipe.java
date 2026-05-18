package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

/**
 * Server-side reforge recipe for one specific rarity.
 *
 * <p>One recipe is created per (reforge, rarity) pair. The client shows a compact card
 * with the stat boosts that applying this reforge would give to an item of this rarity.
 *
 * <p>When a player clicks an item in RRV, only reforge recipes whose {@code rarity}
 * matches the item's rarity <em>and</em> whose {@code itemType} applies to the item
 * will match via {@link SkyblockReforgeClientRecipe#redirectsAsResult}.
 *
 * <p>Result internal names are pre-computed during generation so the client recipe never
 * has to scan the full item registry.
 */
public class SkyblockReforgeServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockReforgeServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_reforge"),
                    () -> new SkyblockReforgeServerRecipe(
                            "", true, "", "", "COMMON", List.of(),
                            Map.of(), 0, Optional.empty(),
                            List.of(), List.of(), Optional.empty(), new String[0], List.of()));

    private String reforgeName;
    private boolean isBlacksmith;
    private String stoneInternalName;
    private String itemType;
    private String rarity;
    private List<String> requiredRarities;
    private Map<String, Double> stats;
    private int cost;
    private Optional<String> ability;
    private List<String> specificInternalNames;
    private List<String> specificItemIds;
    private Optional<String> nbtModifier;

    /**
     * Pre-computed internal names of items that match this reforge recipe's criteria.
     * Built once during generation to eliminate registry scans on the client.
     */
    private List<String> resultInternalNames;

    public SkyblockReforgeServerRecipe(
            String reforgeName, boolean isBlacksmith, String stoneInternalName,
            String itemType, String rarity, List<String> requiredRarities,
            Map<String, Double> stats, int cost, Optional<String> ability,
            List<String> specificInternalNames, List<String> specificItemIds,
            Optional<String> nbtModifier, String[] wikiUrls,
            List<String> resultInternalNames) {
        super(wikiUrls);
        this.reforgeName = reforgeName != null ? reforgeName : "";
        this.isBlacksmith = isBlacksmith;
        this.stoneInternalName = stoneInternalName != null ? stoneInternalName : "";
        this.itemType = itemType != null ? itemType : "";
        this.rarity = rarity != null ? rarity : "COMMON";
        this.requiredRarities = requiredRarities != null ? List.copyOf(requiredRarities) : List.of();
        this.stats = stats != null ? Collections.unmodifiableMap(new HashMap<>(stats)) : Map.of();
        this.cost = cost;
        this.ability = ability;
        this.specificInternalNames = specificInternalNames != null ? List.copyOf(specificInternalNames) : List.of();
        this.specificItemIds = specificItemIds != null ? List.copyOf(specificItemIds) : List.of();
        this.nbtModifier = nbtModifier;
        this.resultInternalNames = resultInternalNames != null ? List.copyOf(resultInternalNames) : List.of();
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
        tag.putString(RecipeTagCodec.KEY_ITEM_RARITY, rarity);
        writeStringList(tag, "rar", requiredRarities);
        writeStats(tag, RecipeTagCodec.KEY_STATS, stats);
        tag.putInt(RecipeTagCodec.KEY_REFORGE_COST, cost);
        if (ability.isPresent()) {
            tag.putString(RecipeTagCodec.KEY_ABILITY, ability.get());
        }
        writeStringList(tag, "specIn", specificInternalNames);
        writeStringList(tag, "specId", specificItemIds);
        if (nbtModifier.isPresent()) {
            tag.putString(RecipeTagCodec.KEY_NBT_MODIFIER, nbtModifier.get());
        }
        writeStringList(tag, "resNames", resultInternalNames);
    }

    @Override
    protected void readFields(CompoundTag tag) {
        reforgeName = tag.getStringOr(RecipeTagCodec.KEY_REFORGE_NAME, "");
        isBlacksmith = tag.getBooleanOr(RecipeTagCodec.KEY_IS_BLACKSMITH, true);
        stoneInternalName = tag.getStringOr(RecipeTagCodec.KEY_STONE_NAME, "");
        itemType = tag.getStringOr(RecipeTagCodec.KEY_ITEM_TYPE, "");
        rarity = tag.getStringOr(RecipeTagCodec.KEY_ITEM_RARITY, "COMMON");
        requiredRarities = readStringList(tag, "rar");
        stats = readStats(tag, RecipeTagCodec.KEY_STATS);
        cost = tag.getIntOr(RecipeTagCodec.KEY_REFORGE_COST, 0);
        ability = tag.contains(RecipeTagCodec.KEY_ABILITY)
                ? Optional.of(tag.getStringOr(RecipeTagCodec.KEY_ABILITY, ""))
                : Optional.empty();
        specificInternalNames = readStringList(tag, "specIn");
        specificItemIds = readStringList(tag, "specId");
        nbtModifier = tag.contains(RecipeTagCodec.KEY_NBT_MODIFIER)
                ? Optional.of(tag.getStringOr(RecipeTagCodec.KEY_NBT_MODIFIER, ""))
                : Optional.empty();
        resultInternalNames = readStringList(tag, "resNames");
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

    /** Writes a flat stat map (rarity-specific, not nested). */
    private static void writeStats(CompoundTag tag, String key, Map<String, Double> stats) {
        CompoundTag root = new CompoundTag();
        for (var entry : stats.entrySet()) {
            root.putDouble(entry.getKey(), entry.getValue());
        }
        tag.put(key, root);
    }

    /** Reads a flat stat map. */
    private static Map<String, Double> readStats(CompoundTag tag, String key) {
        CompoundTag root = tag.getCompoundOrEmpty(key);
        Map<String, Double> out = new HashMap<>();
        for (String statKey : root.keySet()) {
            out.put(statKey, root.getDoubleOr(statKey, 0.0));
        }
        return Collections.unmodifiableMap(out);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getReforgeName()                    { return reforgeName; }
    public boolean isBlacksmith()                     { return isBlacksmith; }
    public String getStoneInternalName()              { return stoneInternalName; }
    public String getItemType()                       { return itemType; }
    public String getRarity()                         { return rarity; }
    public List<String> getRequiredRarities()         { return requiredRarities; }
    public Map<String, Double> getStats()             { return stats; }
    public int getCost()                              { return cost; }
    public Optional<String> getAbility()              { return ability; }
    public List<String> getSpecificInternalNames()    { return specificInternalNames; }
    public List<String> getSpecificItemIds()          { return specificItemIds; }
    public Optional<String> getNbtModifier()          { return nbtModifier; }
    public List<String> getResultInternalNames()      { return resultInternalNames; }
}
