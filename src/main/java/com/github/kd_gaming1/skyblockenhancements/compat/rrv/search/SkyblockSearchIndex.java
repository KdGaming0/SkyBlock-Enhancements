package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeStoneData;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Pre-computed inverted search index for the SkyBlock item list.
 *
 * <p>Built once on the background thread when the repo cache is populated. Search is performed
 * via {@link #search(SearchQuery)} using {@link BitSet} intersections — O(k·log T + n/64) where
 * k = query terms and T = distinct tokens.
 *
 * <p>The index supports:
 * <ul>
 *   <li>Keyword search across names, lore, categories, pet types, reforge names, stats, etc.</li>
 *   <li>Prefix matching via a sorted token array ({@code min} → {@code mining}).</li>
 *   <li>Stat threshold queries ({@code mining_speed>50}) via per-stat {@link TreeMap}s.</li>
 *   <li>Category filtering at the index level (avoids post-search {@code removeIf}).</li>
 * </ul>
 */
public final class SkyblockSearchIndex {

    /** Maximum tokens expanded for a single prefix query. Guards against {@code "a"} → everything. */
    private static final int MAX_PREFIX_EXPANSION = 128;
    /** Maximum cached query results. */
    private static final int QUERY_CACHE_CAPACITY = 64;

    private final int itemCount;
    private final List<ItemStack> items;
    private final Map<String, BitSet> nameTokenIndex;
    private final Map<String, BitSet> anyTokenIndex;
    private final String[] sortedTokens;
    private final Map<String, TreeMap<Integer, BitSet>> statIndex;
    private final Map<String, SearchResult> queryCache;
    private final BitSet allItems;
    private final Map<SkyblockItemCategory, BitSet> categoryIndex;
    private final @Nullable SearchAutocomplete autocomplete;
    private final @Nullable FuzzyTokenMatcher fuzzyMatcher;

    // -- Expanded structured indices -------------------------------------------
    private final Map<String, BitSet> rarityIndex;
    private final Map<String, BitSet> loreTypeIndex;
    private final Map<String, BitSet> booleanFlagIndex;
    private final Map<String, TreeMap<Integer, BitSet>> slayerLevelIndex;
    private final Map<String, TreeMap<Integer, BitSet>> skillLevelIndex;
    private final Map<String, TreeMap<Integer, BitSet>> catacombsLevelIndex;

    // -- Dedicated requirement type indices (precise filter matching) -----------
    private final Map<String, BitSet> slayerTypeIndex;
    private final Map<String, BitSet> skillTypeIndex;
    private final Map<String, BitSet> catacombsTypeIndex;

    /** Maps SkyBlock internal name → every search token belonging to that item. */
    public final Map<String, Set<String>> itemIdToTokens;

    public SkyblockSearchIndex(List<ItemStack> items, Map<ItemStack, NeuItem> neuItems) {
        this.items = List.copyOf(items);
        this.itemCount = this.items.size();

        this.nameTokenIndex = new java.util.HashMap<>(4096);
        this.anyTokenIndex = new java.util.HashMap<>(8192);
        this.statIndex = new java.util.HashMap<>(256);
        this.categoryIndex = new EnumMap<>(SkyblockItemCategory.class);
        this.rarityIndex = new java.util.HashMap<>(16);
        this.loreTypeIndex = new java.util.HashMap<>(64);
        this.booleanFlagIndex = new java.util.HashMap<>(8);
        this.slayerLevelIndex = new java.util.HashMap<>(8);
        this.skillLevelIndex = new java.util.HashMap<>(16);
        this.catacombsLevelIndex = new java.util.HashMap<>(8);
        this.slayerTypeIndex = new java.util.HashMap<>(8);
        this.skillTypeIndex = new java.util.HashMap<>(16);
        this.catacombsTypeIndex = new java.util.HashMap<>(8);
        this.itemIdToTokens = new HashMap<>(items.size());
        Set<String> autocompleteTokens = new TreeSet<>();

        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = this.items.get(i);
            NeuItem neuItem = neuItems.get(stack);
            indexItem(i, stack, neuItem, autocompleteTokens);
            if (neuItem != null && neuItem.category != null) {
                categoryIndex.computeIfAbsent(neuItem.category, k -> new BitSet()).set(i);
            }
        }

        this.sortedTokens = anyTokenIndex.keySet().stream().sorted().toArray(String[]::new);

        this.allItems = new BitSet(itemCount);
        this.allItems.set(0, itemCount);

        String[] cleanTokens = autocompleteTokens.toArray(new String[0]);
        this.autocomplete = new SearchAutocomplete(cleanTokens, this.items, neuItems, this.categoryIndex);
        this.fuzzyMatcher = new FuzzyTokenMatcher(this.sortedTokens);

        this.queryCache = Collections.synchronizedMap(new LinkedHashMap<>(QUERY_CACHE_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SearchResult> eldest) {
                return size() > QUERY_CACHE_CAPACITY;
            }
        });
    }

    /**
     * Returns the ordered item list used by this index. The returned list is immutable.
     */
    public List<ItemStack> getItems() {
        return items;
    }

    public @Nullable SearchAutocomplete getAutocomplete() {
        return autocomplete;
    }

    /**
     * Executes {@code query} against the inverted index and returns a ranked {@link SearchResult}.
     * Results are cached so identical consecutive queries are O(1).
     *
     * <p>Intermediate {@link BitSet}s are borrowed from a thread-local pool to eliminate
     * per-search allocation. The returned {@link SearchResult} owns its BitSets and is safe
     * to cache or return to callers.
     */
    public SearchResult search(SearchQuery query) {
        if (query.isEmpty()) {
            return new SearchResult((BitSet) allItems.clone(), new BitSet(), new BitSet());
        }

        String cacheKey = buildCacheKey(query);
        SearchResult cached = queryCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        BitSet keywordMatches = BitSetPool.borrow(itemCount);
        BitSet statMatches = BitSetPool.borrow(itemCount);
        BitSet filterMatches = BitSetPool.borrow(itemCount);
        BitSet candidates = BitSetPool.borrow(itemCount);

        try {
            resolveKeywords(query.keywords(), keywordMatches);
            resolveStats(query.stats(), statMatches);
            resolveFilters(query.filters(), filterMatches);

            intersectInto(keywordMatches, statMatches, candidates);
            candidates.and(filterMatches);

            if (candidates.isEmpty()) {
                SearchResult empty = new SearchResult(new BitSet(), new BitSet(), new BitSet());
                queryCache.put(cacheKey, empty);
                return empty;
            }

            if (query.categoryFilter() != null) {
                BitSet catBits = categoryIndex.get(query.categoryFilter());
                if (catBits != null) {
                    candidates.and(catBits);
                }
                if (candidates.isEmpty()) {
                    SearchResult empty = new SearchResult(new BitSet(), new BitSet(), new BitSet());
                    queryCache.put(cacheKey, empty);
                    return empty;
                }
            }

            SearchResult result = rank(query, candidates);
            queryCache.put(cacheKey, result);
            return result;
        } finally {
            BitSetPool.release(keywordMatches);
            BitSetPool.release(statMatches);
            BitSetPool.release(filterMatches);
            BitSetPool.release(candidates);
        }
    }

    /**
     * Fast boolean check for inventory slot highlighting.
     *
     * <p>Stat clauses are ignored because inventory items are not mapped to overlay BitSet
     * indices. Keyword clauses are AND-ed against the item's precomputed token set.
     */
    public boolean itemMatchesInventoryQuery(String itemId, SearchQuery query) {
        Set<String> tokens = itemIdToTokens.get(itemId);
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }

        for (SearchQuery.KeywordClause kw : query.keywords()) {
            if (!tokenSetContainsPrefix(tokens, kw.token())) {
                return false;
            }
        }

        for (SearchQuery.FilterClause f : query.filters()) {
            // String-only filters can be checked against the token set because
            // the indexer adds filter values as tokens (e.g. "legendary", "sword").
            // Numeric threshold filters cannot be evaluated here — return false
            // so the caller falls back to lore scanning.
            if (f.stringValue() != null && (f.op() == null || f.op() == SearchQuery.FilterClause.Operator.EQ)) {
                if (!tokenSetContainsPrefix(tokens, f.stringValue())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean tokenSetContainsPrefix(Set<String> tokens, String prefix) {
        for (String t : tokens) {
            if (t.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private SearchResult rank(SearchQuery query, BitSet candidates) {
        BitSet first = new BitSet();
        BitSet second = new BitSet();
        BitSet third = new BitSet();

        if (!query.keywords().isEmpty()) {
            BitSet nameMatches = BitSetPool.borrow(itemCount);
            try {
                resolveKeywordsInName(query.keywords(), nameMatches);
                if (!nameMatches.isEmpty()) {
                    first.or(candidates);
                    first.and(nameMatches);
                }
            } finally {
                BitSetPool.release(nameMatches);
            }

            second.or(candidates);
            second.andNot(first);

            if (query.keywords().size() == 1 && query.stats().isEmpty()) {
                BitSet prefixMatches = BitSetPool.borrow(itemCount);
                try {
                    resolvePrefixUnion(query.keywords().getFirst().token(), prefixMatches);
                    third.or(prefixMatches);
                    third.andNot(first);
                    third.andNot(second);
                } finally {
                    BitSetPool.release(prefixMatches);
                }
            }
        } else {
            second.or(candidates);
        }

        return new SearchResult(first, second, third);
    }

    // ── Keyword resolution ─────────────────────────────────────────────────────────

    /**
     * Resolves all keyword clauses into {@code result} via AND-ing.
     * If {@code keywords} is empty, {@code result} is set to all items.
     */
    private void resolveKeywords(List<SearchQuery.KeywordClause> keywords, BitSet result) {
        if (keywords.isEmpty()) {
            result.set(0, itemCount);
            return;
        }

        boolean first = true;
        for (SearchQuery.KeywordClause clause : keywords) {
            BitSet matches = resolveKeyword(clause.token());
            if (matches == null || matches.isEmpty()) {
                result.clear();
                return;
            }
            if (first) {
                result.or(matches);
                first = false;
            } else {
                result.and(matches);
            }
        }
    }

    /**
     * Resolves all keyword clauses into {@code result} via AND-ing, using the name-only index.
     * If {@code keywords} is empty, {@code result} is cleared.
     */
    private void resolveKeywordsInName(List<SearchQuery.KeywordClause> keywords, BitSet result) {
        if (keywords.isEmpty()) {
            return;
        }

        boolean first = true;
        for (SearchQuery.KeywordClause clause : keywords) {
            BitSet matches = resolveKeywordInName(clause.token());
            if (matches == null || matches.isEmpty()) {
                result.clear();
                return;
            }
            if (first) {
                result.or(matches);
                first = false;
            } else {
                result.and(matches);
            }
        }
    }

    /**
     * Looks up a keyword in the any-token index. If an exact token exists, its items are
     * unioned with prefix-expanded matches so that typing {@code "en"} still finds {@code "end"},
     * {@code "ender"}, etc. even when {@code "en"} itself is also a token.
     */
    private BitSet resolveKeyword(String token) {
        BitSet exact = anyTokenIndex.get(token);

        BitSet temp = BitSetPool.borrow(itemCount);
        resolvePrefixUnion(token, temp);

        if (exact != null) {
            temp.or(exact);
        }

        if (!temp.isEmpty()) {
            BitSet copy = (BitSet) temp.clone();
            BitSetPool.release(temp);
            return copy;
        }
        BitSetPool.release(temp);

        // Fuzzy fallback for typo tolerance
        if (fuzzyMatcher != null) {
            BitSet fuzzy = fuzzyMatcher.fuzzyMatch(token, anyTokenIndex::get, itemCount);
            if (!fuzzy.isEmpty()) {
                return fuzzy;
            }
        }

        // Singular fallback: "pets" -> "pet", "swords" -> "sword"
        String singular = toSingular(token);
        if (singular != null) {
            BitSet singularExact = anyTokenIndex.get(singular);
            BitSet singularTemp = BitSetPool.borrow(itemCount);
            try {
                resolvePrefixUnion(singular, singularTemp);
                if (singularExact != null) {
                    singularTemp.or(singularExact);
                }
                if (!singularTemp.isEmpty()) {
                    return (BitSet) singularTemp.clone();
                }
            } finally {
                BitSetPool.release(singularTemp);
            }
        }

        return new BitSet();
    }

    /**
     * Looks up a keyword in the name-only index, unioning exact and prefix matches.
     */
    private BitSet resolveKeywordInName(String token) {
        BitSet exact = nameTokenIndex.get(token);

        BitSet temp = BitSetPool.borrow(itemCount);
        resolvePrefixUnionInName(token, temp);

        if (exact != null) {
            temp.or(exact);
        }

        if (!temp.isEmpty()) {
            BitSet copy = (BitSet) temp.clone();
            BitSetPool.release(temp);
            return copy;
        }
        BitSetPool.release(temp);

        // Singular fallback for name-only matches too
        String singular = toSingular(token);
        if (singular != null) {
            BitSet singularExact = nameTokenIndex.get(singular);
            BitSet singularTemp = BitSetPool.borrow(itemCount);
            try {
                resolvePrefixUnionInName(singular, singularTemp);
                if (singularExact != null) {
                    singularTemp.or(singularExact);
                }
                if (!singularTemp.isEmpty()) {
                    return (BitSet) singularTemp.clone();
                }
            } finally {
                BitSetPool.release(singularTemp);
            }
        }

        return new BitSet();
    }

    /** Unions all any-token BitSets whose token starts with {@code prefix} into {@code result}. */
    private void resolvePrefixUnion(String prefix, BitSet result) {
        resolvePrefixUnion(prefix, anyTokenIndex, result);
    }

    /** Unions all name-token BitSets whose token starts with {@code prefix} into {@code result}. */
    private void resolvePrefixUnionInName(String prefix, BitSet result) {
        resolvePrefixUnion(prefix, nameTokenIndex, result);
    }

    private void resolvePrefixUnion(String prefix, Map<String, BitSet> index, BitSet result) {
        if (prefix.isEmpty()) {
            return;
        }

        int idx = Arrays.binarySearch(sortedTokens, prefix);
        if (idx < 0) {
            idx = -idx - 1;
        }

        // Short prefixes match many tokens — cap them to keep latency low.
        // 2+ char prefixes are specific enough that we can expand without limit.
        int maxExpansion = switch (prefix.length()) {
            case 1 -> 512;
            case 2 -> 2048;
            default -> Integer.MAX_VALUE;
        };

        int count = 0;
        for (int i = idx; i < sortedTokens.length && sortedTokens[i].startsWith(prefix); i++) {
            BitSet bs = index.get(sortedTokens[i]);
            if (bs != null) {
                result.or(bs);
            }
            if (++count >= maxExpansion) {
                break;
            }
        }
    }

    // ── Stat resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves all stat clauses into {@code result} via AND-ing.
     * If {@code stats} is empty, {@code result} is set to all items.
     */
    private void resolveStats(List<SearchQuery.StatClause> stats, BitSet result) {
        if (stats.isEmpty()) {
            result.set(0, itemCount);
            return;
        }

        boolean first = true;
        for (SearchQuery.StatClause clause : stats) {
            BitSet matches = resolveStat(clause);
            if (matches.isEmpty()) {
                result.clear();
                return;
            }
            if (first) {
                result.or(matches);
                first = false;
            } else {
                result.and(matches);
            }
        }
    }

    private BitSet resolveStat(SearchQuery.StatClause clause) {
        TreeMap<Integer, BitSet> valueMap = statIndex.get(clause.statName());
        if (valueMap == null || valueMap.isEmpty()) {
            return new BitSet();
        }

        BitSet result = new BitSet();
        switch (clause.op()) {
            case GT -> unionRange(result, valueMap.tailMap(clause.value(), false));
            case LT -> unionRange(result, valueMap.headMap(clause.value(), false));
            case GTE -> unionRange(result, valueMap.tailMap(clause.value(), true));
            case LTE -> unionRange(result, valueMap.headMap(clause.value(), true));
            case EQ -> {
                BitSet bs = valueMap.get(clause.value());
                if (bs != null) result.or(bs);
            }
        }
        return result;
    }

    private static void unionRange(BitSet target, java.util.NavigableMap<Integer, BitSet> range) {
        for (BitSet bs : range.values()) {
            target.or(bs);
        }
    }

    // ── Filter resolution ────────────────────────────────────────────────────────

    /**
     * Resolves all filter clauses into {@code result} via AND-ing.
     * If {@code filters} is empty, {@code result} is set to all items.
     */
    private void resolveFilters(List<SearchQuery.FilterClause> filters, BitSet result) {
        if (filters.isEmpty()) {
            result.set(0, itemCount);
            return;
        }

        boolean first = true;
        for (SearchQuery.FilterClause clause : filters) {
            BitSet matches = resolveFilter(clause);
            if (matches == null || matches.isEmpty()) {
                result.clear();
                return;
            }
            if (first) {
                result.or(matches);
                first = false;
            } else {
                result.and(matches);
            }
        }
    }

    private BitSet resolveFilter(SearchQuery.FilterClause clause) {
        return switch (clause.key()) {
            case "rarity" -> resolveRarityFilter(clause);
            case "type" -> resolveTypeFilter(clause);
            case "slayer" -> resolveSlayerFilter(clause);
            case "skill" -> resolveSkillFilter(clause);
            case "catacombs" -> resolveCatacombsFilter(clause);
            case "soulbound", "dungeon", "rift" -> resolveBooleanFlagFilter(clause);
            default -> new BitSet();
        };
    }

    private BitSet resolveRarityFilter(SearchQuery.FilterClause clause) {
        String value = clause.stringValue();
        if (value == null || value.isEmpty()) {
            return new BitSet();
        }
        BitSet exact = rarityIndex.get(value);
        return exact != null ? (BitSet) exact.clone() : new BitSet();
    }

    private BitSet resolveTypeFilter(SearchQuery.FilterClause clause) {
        String value = clause.stringValue();
        if (value == null || value.isEmpty()) {
            return new BitSet();
        }
        BitSet result = new BitSet();
        // Exact match first
        BitSet exact = loreTypeIndex.get(value);
        if (exact != null) result.or(exact);
        // Prefix match for subtypes: "sword" matches "dungeon_sword"
        for (Map.Entry<String, BitSet> e : loreTypeIndex.entrySet()) {
            if (e.getKey().startsWith(value) || e.getKey().contains("_" + value)) {
                result.or(e.getValue());
            }
        }
        return result;
    }

    private BitSet resolveSlayerFilter(SearchQuery.FilterClause clause) {
        String type = clause.stringValue();
        SearchQuery.FilterClause.Operator op = clause.op();

        if (type != null && !type.isEmpty()) {
            if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
                // slayer:zombie — use dedicated slayer-type index for precision
                BitSet exact = slayerTypeIndex.get(type);
                return exact != null ? (BitSet) exact.clone() : new BitSet();
            }
            // slayer:zombie>3 — type + level threshold
            TreeMap<Integer, BitSet> levelMap = slayerLevelIndex.get(type);
            if (levelMap == null || levelMap.isEmpty()) {
                return new BitSet();
            }
            return resolveThreshold(levelMap, op, clause.intValue());
        }

        // slayer>3 — any slayer type with level threshold
        if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
            // slayer with no type or level — return all slayer items
            BitSet result = new BitSet();
            for (BitSet bs : slayerTypeIndex.values()) {
                result.or(bs);
            }
            return result;
        }
        BitSet result = new BitSet();
        for (TreeMap<Integer, BitSet> levelMap : slayerLevelIndex.values()) {
            BitSet partial = resolveThreshold(levelMap, op, clause.intValue());
            result.or(partial);
        }
        return result;
    }

    private BitSet resolveSkillFilter(SearchQuery.FilterClause clause) {
        String skill = clause.stringValue();
        SearchQuery.FilterClause.Operator op = clause.op();

        if (skill != null && !skill.isEmpty()) {
            if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
                // skill:combat — use dedicated skill-type index for precision
                BitSet exact = skillTypeIndex.get(skill);
                return exact != null ? (BitSet) exact.clone() : new BitSet();
            }
            // skill:combat>20 — skill + level threshold
            TreeMap<Integer, BitSet> levelMap = skillLevelIndex.get(skill);
            if (levelMap == null || levelMap.isEmpty()) {
                return new BitSet();
            }
            return resolveThreshold(levelMap, op, clause.intValue());
        }

        // skill>20 — any skill with level threshold
        if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
            BitSet result = new BitSet();
            for (BitSet bs : skillTypeIndex.values()) {
                result.or(bs);
            }
            return result;
        }
        BitSet result = new BitSet();
        for (TreeMap<Integer, BitSet> levelMap : skillLevelIndex.values()) {
            BitSet partial = resolveThreshold(levelMap, op, clause.intValue());
            result.or(partial);
        }
        return result;
    }

    private BitSet resolveCatacombsFilter(SearchQuery.FilterClause clause) {
        SearchQuery.FilterClause.Operator op = clause.op();
        if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
            // catacombs — return all catacombs-gated items
            BitSet result = new BitSet();
            for (BitSet bs : catacombsTypeIndex.values()) {
                result.or(bs);
            }
            return result;
        }
        BitSet result = new BitSet();
        for (TreeMap<Integer, BitSet> levelMap : catacombsLevelIndex.values()) {
            BitSet partial = resolveThreshold(levelMap, op, clause.intValue());
            result.or(partial);
        }
        return result;
    }

    private BitSet resolveBooleanFlagFilter(SearchQuery.FilterClause clause) {
        BitSet exact = booleanFlagIndex.get(clause.key());
        return exact != null ? (BitSet) exact.clone() : new BitSet();
    }

    private static BitSet resolveThreshold(TreeMap<Integer, BitSet> valueMap,
                                           SearchQuery.FilterClause.Operator op, int value) {
        BitSet result = new BitSet();
        if (op == null) {
            op = SearchQuery.FilterClause.Operator.EQ;
        }
        switch (op) {
            case GT -> unionRange(result, valueMap.tailMap(value, false));
            case LT -> unionRange(result, valueMap.headMap(value, false));
            case GTE -> unionRange(result, valueMap.tailMap(value, true));
            case LTE -> unionRange(result, valueMap.headMap(value, true));
            case EQ -> {
                BitSet bs = valueMap.get(value);
                if (bs != null) result.or(bs);
            }
        }
        return result;
    }

    // ── Index building ─────────────────────────────────────────────────────────────

    private void indexItem(int itemIndex, ItemStack stack, @Nullable NeuItem neuItem,
                           Set<String> autocompleteTokens) {
        String itemId = neuItem != null ? neuItem.internalName : null;
        Set<String> itemTokens = itemId != null ? new HashSet<>(32) : null;

        String displayName = stack.getHoverName().getString();
        tokenize(displayName, (token, idx) -> {
            addDisplayNameToken(token, idx, autocompleteTokens);
            if (itemTokens != null) itemTokens.add(token);
        }, itemIndex);

        if (neuItem != null) {
            indexNeuItem(itemIndex, neuItem, autocompleteTokens, itemTokens);
        }

        if (itemId != null && !itemTokens.isEmpty()) {
            itemIdToTokens.merge(itemId, itemTokens, (existing, incoming) -> {
                existing.addAll(incoming);
                return existing;
            });
        }
    }

    private void indexNeuItem(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                              @Nullable Set<String> itemTokens) {
        if (neuItem.internalName != null) {
            String lower = neuItem.internalName.toLowerCase(java.util.Locale.ROOT);
            addNameToken(lower, itemIndex);
            if (itemTokens != null) {
                itemTokens.add(lower);
                for (String part : lower.split("_")) {
                    if (part.length() > 1) {
                        itemTokens.add(part);
                    }
                }
            }
        }

        if (neuItem.lore != null) {
            for (String line : neuItem.lore) {
                tokenize(line, (token, idx) -> {
                    addAnyToken(token, idx);
                    if (itemTokens != null) itemTokens.add(token);
                }, itemIndex);
            }
        }

        if (neuItem.category != null) {
            String categoryName = neuItem.category.name().toLowerCase(java.util.Locale.ROOT);
            addAnyToken(categoryName, itemIndex);
            addAutocompleteToken(categoryName, autocompleteTokens);
            if (itemTokens != null) itemTokens.add(categoryName);
        }

        indexPetType(itemIndex, neuItem, autocompleteTokens, itemTokens);
        indexReforge(itemIndex, neuItem, autocompleteTokens, itemTokens);
        indexStats(itemIndex, neuItem, autocompleteTokens, itemTokens);
        indexSlayer(itemIndex, neuItem, autocompleteTokens, itemTokens);
        indexRarity(itemIndex, neuItem, autocompleteTokens, itemTokens);
        indexLoreType(itemIndex, neuItem, autocompleteTokens, itemTokens);
        indexSkillRequirements(itemIndex, neuItem, itemTokens);
        indexCatacombsRequirements(itemIndex, neuItem, itemTokens);
        indexBooleanFlags(itemIndex, neuItem, itemTokens);

        if (neuItem.crafttext != null && !neuItem.crafttext.isEmpty()) {
            tokenize(neuItem.crafttext, (token, idx) -> {
                addAnyToken(token, idx);
                if (itemTokens != null) itemTokens.add(token);
            }, itemIndex);
        }
    }

    private void indexPetType(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                              @Nullable Set<String> itemTokens) {
        if (neuItem.category != SkyblockItemCategory.PET || neuItem.internalName == null) {
            return;
        }
        String petId = extractPetId(neuItem.internalName);
        String skill = NeuConstantsRegistry.getPetType(petId);
        if (skill != null) {
            String token = skill.toLowerCase(java.util.Locale.ROOT);
            addAnyToken(token, itemIndex);
            addAutocompleteToken(token, autocompleteTokens);
            if (itemTokens != null) itemTokens.add(token);
        }
    }

    private void indexReforge(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                              @Nullable Set<String> itemTokens) {
        if (neuItem.internalName == null) {
            return;
        }
        ReforgeStoneData stone = NeuConstantsRegistry.getAllReforgeStones().get(neuItem.internalName);
        if (stone == null || stone.reforgeName() == null) {
            return;
        }
        tokenize(stone.reforgeName(), (token, idx) -> {
            addAnyToken(token, idx);
            addAutocompleteToken(token, autocompleteTokens);
            if (itemTokens != null) itemTokens.add(token);
        }, itemIndex);
    }

    private void indexStats(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                            @Nullable Set<String> itemTokens) {
        if (neuItem.internalName == null) {
            return;
        }

        Map<String, Integer> baseStats = HypixelItemsRegistry.getBaseStats(neuItem.internalName);
        if (baseStats != null) {
            for (Map.Entry<String, Integer> entry : baseStats.entrySet()) {
                String statName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                indexStatName(statName, itemIndex, autocompleteTokens, itemTokens);
                addStatValue(statName, entry.getValue(), itemIndex);
            }
        }

        Map<String, int[]> tieredStats = HypixelItemsRegistry.getTieredStats(neuItem.internalName);
        if (tieredStats != null) {
            for (Map.Entry<String, int[]> entry : tieredStats.entrySet()) {
                String statName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                indexStatName(statName, itemIndex, autocompleteTokens, itemTokens);
                for (int value : entry.getValue()) {
                    addStatValue(statName, value, itemIndex);
                }
            }
        }
    }

    private void indexStatName(String statName, int itemIndex, Set<String> autocompleteTokens,
                               @Nullable Set<String> itemTokens) {
        addAnyToken(statName, itemIndex);
        addAutocompleteToken(statName, autocompleteTokens);
        if (itemTokens != null) itemTokens.add(statName);
        for (String part : statName.split("_")) {
            if (part.length() > 1) {
                addAnyToken(part, itemIndex);
                addAutocompleteToken(part, autocompleteTokens);
                if (itemTokens != null) itemTokens.add(part);
            }
        }
    }

    private void addStatValue(String statName, int value, int itemIndex) {
        TreeMap<Integer, BitSet> valueMap = statIndex.computeIfAbsent(statName, k -> new TreeMap<>());
        valueMap.computeIfAbsent(value, k -> new BitSet()).set(itemIndex);
    }

    private void indexSlayer(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                             @Nullable Set<String> itemTokens) {
        if (neuItem.slayerReq == null || neuItem.slayerReq.isEmpty()) {
            return;
        }
        String type = extractSlayerTypeStatic(neuItem.slayerReq);
        int level = extractSlayerLevelStatic(neuItem.slayerReq);
        if (type != null) {
            addAnyToken(type, itemIndex);
            addAutocompleteToken(type, autocompleteTokens);
            if (itemTokens != null) itemTokens.add(type);

            // Composite tokens for precise slayer level search
            String composite = type + level;
            addAnyToken(composite, itemIndex);
            if (itemTokens != null) itemTokens.add(composite);

            String compositeUnderscore = type + "_" + level;
            addAnyToken(compositeUnderscore, itemIndex);
            if (itemTokens != null) itemTokens.add(compositeUnderscore);

            String fullComposite = "slayer_" + type + "_" + level;
            addAnyToken(fullComposite, itemIndex);
            if (itemTokens != null) itemTokens.add(fullComposite);

            // Dedicated slayer-type index for precise filter matching
            addToken(slayerTypeIndex, type, itemIndex);

            // Add generic "slayer" token so keyword queries like "slayer zombie" work
            addAnyToken("slayer", itemIndex);
            if (itemTokens != null) itemTokens.add("slayer");

            // Index level in dedicated TreeMap for threshold queries
            if (level > 0) {
                TreeMap<Integer, BitSet> levelMap = slayerLevelIndex.computeIfAbsent(type, k -> new TreeMap<>());
                levelMap.computeIfAbsent(level, k -> new BitSet()).set(itemIndex);
            }
        }
    }

    private void indexRarity(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                             @Nullable Set<String> itemTokens) {
        if (neuItem.rarity == null) {
            return;
        }
        String token = neuItem.rarity.name().toLowerCase(java.util.Locale.ROOT);
        addAnyToken(token, itemIndex);
        addAutocompleteToken(token, autocompleteTokens);
        if (itemTokens != null) itemTokens.add(token);
        addToken(rarityIndex, token, itemIndex);
    }

    private void indexLoreType(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens,
                               @Nullable Set<String> itemTokens) {
        if (neuItem.loreType == null || neuItem.loreType.isEmpty()) {
            return;
        }
        String token = neuItem.loreType.toLowerCase(java.util.Locale.ROOT);
        addAnyToken(token, itemIndex);
        addAutocompleteToken(token, autocompleteTokens);
        if (itemTokens != null) itemTokens.add(token);
        addToken(loreTypeIndex, token, itemIndex);

        // Also index without "dungeon_" prefix for broader matching
        if (token.startsWith("dungeon_")) {
            String sub = token.substring(8);
            addAnyToken(sub, itemIndex);
            if (itemTokens != null) itemTokens.add(sub);
        }
    }

    private void indexSkillRequirements(int itemIndex, NeuItem neuItem, @Nullable Set<String> itemTokens) {
        if (neuItem.lore == null || neuItem.lore.isEmpty()) {
            return;
        }
        for (String line : neuItem.lore) {
            String clean = StringUtil.stripColorCodes(line).toLowerCase(java.util.Locale.ROOT);
            // Pattern: "Requires <Name> Skill <N>." or "Requires <Name> Skill <N>"
            int skillIdx = clean.indexOf(" skill ");
            if (skillIdx < 0 || !clean.contains("requires")) {
                continue;
            }
            // Skip catacombs — handled separately
            if (clean.contains("catacombs")) {
                continue;
            }

            String afterSkill = clean.substring(skillIdx + 7).trim();
            // Remove trailing punctuation
            int end = 0;
            for (int i = 0; i < afterSkill.length(); i++) {
                char c = afterSkill.charAt(i);
                if (c >= '0' && c <= '9') {
                    end = i + 1;
                }
            }
            if (end == 0) {
                continue;
            }

            int level;
            try {
                level = Integer.parseInt(afterSkill.substring(0, end));
            } catch (NumberFormatException e) {
                continue;
            }

            String beforeSkill = clean.substring(0, skillIdx).trim();
            // Extract last word before "skill" as the skill name
            int lastSpace = beforeSkill.lastIndexOf(' ');
            String skillName = lastSpace >= 0 ? beforeSkill.substring(lastSpace + 1) : beforeSkill;
            skillName = normalizeFilterTokenStatic(skillName);
            if (skillName.isEmpty()) {
                continue;
            }

            String composite = skillName + level;
            addAnyToken(composite, itemIndex);
            if (itemTokens != null) itemTokens.add(composite);

            String fullComposite = "skill_" + skillName + "_" + level;
            addAnyToken(fullComposite, itemIndex);
            if (itemTokens != null) itemTokens.add(fullComposite);

            // Dedicated skill-type index for precise filter matching
            addToken(skillTypeIndex, skillName, itemIndex);
            addAnyToken("skill", itemIndex);
            if (itemTokens != null) itemTokens.add("skill");

            TreeMap<Integer, BitSet> levelMap = skillLevelIndex.computeIfAbsent(skillName, k -> new TreeMap<>());
            levelMap.computeIfAbsent(level, k -> new BitSet()).set(itemIndex);
        }
    }

    private void indexCatacombsRequirements(int itemIndex, NeuItem neuItem, @Nullable Set<String> itemTokens) {
        if (neuItem.lore == null || neuItem.lore.isEmpty()) {
            return;
        }
        for (String line : neuItem.lore) {
            String clean = StringUtil.stripColorCodes(line).toLowerCase(java.util.Locale.ROOT);
            if (!clean.contains("catacombs")) {
                continue;
            }

            // "Requires Catacombs Skill <N>."
            int skillIdx = clean.indexOf(" skill ");
            if (skillIdx >= 0 && clean.contains("requires")) {
                String afterSkill = clean.substring(skillIdx + 7).trim();
                int end = 0;
                for (int i = 0; i < afterSkill.length(); i++) {
                    char c = afterSkill.charAt(i);
                    if (c >= '0' && c <= '9') {
                        end = i + 1;
                    }
                }
                if (end > 0) {
                    try {
                        int level = Integer.parseInt(afterSkill.substring(0, end));
                        String composite = "catacombs" + level;
                        addAnyToken(composite, itemIndex);
                        if (itemTokens != null) itemTokens.add(composite);

                        TreeMap<Integer, BitSet> levelMap = catacombsLevelIndex.computeIfAbsent("catacombs", k -> new TreeMap<>());
                        levelMap.computeIfAbsent(level, k -> new BitSet()).set(itemIndex);

                        addToken(catacombsTypeIndex, "catacombs", itemIndex);
                        addAnyToken("catacombs", itemIndex);
                        if (itemTokens != null) itemTokens.add("catacombs");
                    } catch (NumberFormatException ignored) {}
                }
                continue;
            }

            // "Requires The Catacombs Floor <Roman> Completion." or "Master Mode The Catacombs Floor <Roman>"
            int floorIdx = clean.indexOf("floor ");
            if (floorIdx >= 0) {
                String afterFloor = clean.substring(floorIdx + 6).trim();
                // Remove trailing words like "completion"
                int space = afterFloor.indexOf(' ');
                if (space > 0) {
                    afterFloor = afterFloor.substring(0, space);
                }
                afterFloor = afterFloor.replace(".", "").trim();
                int level = romanToIntStatic(afterFloor);
                if (level > 0) {
                    String composite = "floor" + level;
                    addAnyToken(composite, itemIndex);
                    if (itemTokens != null) itemTokens.add(composite);

                    boolean isMaster = clean.contains("master mode");
                    String key = isMaster ? "master_catacombs" : "catacombs";
                    TreeMap<Integer, BitSet> levelMap = catacombsLevelIndex.computeIfAbsent(key, k -> new TreeMap<>());
                    levelMap.computeIfAbsent(level, k -> new BitSet()).set(itemIndex);

                    addToken(catacombsTypeIndex, key, itemIndex);
                    addAnyToken("catacombs", itemIndex);
                    if (itemTokens != null) itemTokens.add("catacombs");
                }
            }
        }
    }

    private void indexBooleanFlags(int itemIndex, NeuItem neuItem, @Nullable Set<String> itemTokens) {
        if (neuItem.lore == null || neuItem.lore.isEmpty()) {
            return;
        }
        for (String line : neuItem.lore) {
            String clean = StringUtil.stripColorCodes(line).toLowerCase(java.util.Locale.ROOT);
            if (clean.contains("soulbound")) {
                addAnyToken("soulbound", itemIndex);
                if (itemTokens != null) itemTokens.add("soulbound");
                addToken(booleanFlagIndex, "soulbound", itemIndex);
            }
            if (clean.contains("rift-transferable")) {
                addAnyToken("rift_transferable", itemIndex);
                if (itemTokens != null) itemTokens.add("rift_transferable");
                addToken(booleanFlagIndex, "rift", itemIndex);
            }
        }
        if (neuItem.loreType != null && neuItem.loreType.startsWith("DUNGEON ")) {
            addAnyToken("dungeon", itemIndex);
            if (itemTokens != null) itemTokens.add("dungeon");
            addToken(booleanFlagIndex, "dungeon", itemIndex);
        }
    }

    // ── Token helpers ──────────────────────────────────────────────────────────────

    private void addDisplayNameToken(String token, int itemIndex, Set<String> autocompleteTokens) {
        addNameToken(token, itemIndex);
        addAutocompleteToken(token, autocompleteTokens);
    }

    private void addNameToken(String token, int itemIndex) {
        if (token.length() <= 1) {
            return;
        }
        addToken(nameTokenIndex, token, itemIndex);
        addToken(anyTokenIndex, token, itemIndex);
    }

    private void addAnyToken(String token, int itemIndex) {
        if (token.length() <= 1) {
            return;
        }
        addToken(anyTokenIndex, token, itemIndex);
    }

    private static void addAutocompleteToken(String token, Set<String> autocompleteTokens) {
        if (token.length() <= 1 || isPurelyNumeric(token)) {
            return;
        }
        autocompleteTokens.add(token);
    }

    private static boolean isPurelyNumeric(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private void addToken(Map<String, BitSet> index, String token, int itemIndex) {
        BitSet bs = index.get(token);
        if (bs == null) {
            bs = new BitSet(itemCount);
            index.put(token, bs);
        }
        bs.set(itemIndex);
    }

    /** Strips Minecraft color codes and splits into alphanumeric tokens. */
    private static void tokenize(String raw, TokenConsumer consumer, int itemIndex) {
        if (raw == null) {
            return;
        }

        String clean = StringUtil.stripColorCodes(raw).toLowerCase(java.util.Locale.ROOT);
        int len = clean.length();
        int start = -1;

        for (int i = 0; i < len; i++) {
            char c = clean.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) {
                    start = i;
                }
            } else {
                if (start >= 0) {
                    if (i - start > 1) {
                        consumer.accept(clean.substring(start, i), itemIndex);
                    }
                    start = -1;
                }
            }
        }
        if (start >= 0 && len - start > 1) {
            consumer.accept(clean.substring(start, len), itemIndex);
        }
    }

    private static @NonNull String extractPetId(String internalName) {
        int semi = internalName.indexOf(';');
        return semi >= 0 ? internalName.substring(0, semi) : internalName;
    }

    @Nullable
    static String extractSlayerTypeStatic(String slayerReq) {
        int underscore = slayerReq.indexOf('_');
        return (underscore > 0)
                ? slayerReq.substring(0, underscore).toLowerCase(java.util.Locale.ROOT)
                : null;
    }

    static int extractSlayerLevelStatic(String slayerReq) {
        int underscore = slayerReq.lastIndexOf('_');
        if (underscore < 0 || underscore + 1 >= slayerReq.length()) {
            return 0;
        }
        try {
            return Integer.parseInt(slayerReq.substring(underscore + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String normalizeFilterTokenStatic(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Returns the singular form of {@code token} if it appears to be a simple plural,
     * or {@code null} if no singular form should be tried.
     *
     * <p>Rules:
     * <ul>
     *   <li>Skip words ending in "ss" (glass, boss) to avoid false positives.</li>
     *   <li>Strip trailing "es" when the base is >= 3 chars (bosses -> boss).</li>
     *   <li>Strip trailing "s" when the base is >= 3 chars (pets -> pet).</li>
     * </ul>
     */
    @Nullable
    static String toSingular(String token) {
        if (token == null || token.length() < 4) {
            return null;
        }
        // Skip words ending in "ss" (glass, pass, boss)
        if (token.endsWith("ss")) {
            return null;
        }
        if (token.endsWith("es")) {
            String base = token.substring(0, token.length() - 2);
            return base.length() >= 3 ? base : null;
        }
        if (token.endsWith("s")) {
            String base = token.substring(0, token.length() - 1);
            return base.length() >= 3 ? base : null;
        }
        return null;
    }

    static int romanToIntStatic(String roman) {
        if (roman == null || roman.isEmpty()) {
            return 0;
        }
        String lower = roman.toLowerCase(java.util.Locale.ROOT);
        int total = 0;
        int prev = 0;
        for (int i = lower.length() - 1; i >= 0; i--) {
            int value = switch (lower.charAt(i)) {
                case 'i' -> 1;
                case 'v' -> 5;
                case 'x' -> 10;
                case 'l' -> 50;
                case 'c' -> 100;
                case 'd' -> 500;
                case 'm' -> 1000;
                default -> 0;
            };
            if (value == 0) {
                return 0; // Invalid character
            }
            if (value < prev) {
                total -= value;
            } else {
                total += value;
                prev = value;
            }
        }
        return total;
    }

    // ── BitSet helpers ─────────────────────────────────────────────────────────────

    /** ANDs {@code a} and {@code b} into {@code result} in-place. Neither operand is modified. */
    private static void intersectInto(BitSet a, BitSet b, BitSet result) {
        result.or(a);
        result.and(b);
    }

    // ── Cache key ──────────────────────────────────────────────────────────────────

    private static String buildCacheKey(SearchQuery query) {
        StringBuilder sb = new StringBuilder();
        for (SearchQuery.KeywordClause k : query.keywords()) {
            sb.append('K').append(k.token());
        }
        for (SearchQuery.StatClause s : query.stats()) {
            sb.append('S').append(s.statName()).append(s.op()).append(s.value());
        }
        for (SearchQuery.FilterClause f : query.filters()) {
            sb.append('F').append(f.key()).append(f.op()).append(f.stringValue()).append(f.intValue());
        }
        if (query.categoryFilter() != null) {
            sb.append('C').append(query.categoryFilter().name());
        }
        return sb.toString();
    }

    @FunctionalInterface
    private interface TokenConsumer {
        void accept(String token, int itemIndex);
    }
}
