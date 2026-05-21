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

    public SkyblockSearchIndex(List<ItemStack> items, Map<ItemStack, NeuItem> neuItems) {
        this.items = List.copyOf(items);
        this.itemCount = this.items.size();

        this.nameTokenIndex = new java.util.HashMap<>(4096);
        this.anyTokenIndex = new java.util.HashMap<>(8192);
        this.statIndex = new java.util.HashMap<>(256);
        this.categoryIndex = new EnumMap<>(SkyblockItemCategory.class);
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
        BitSet candidates = BitSetPool.borrow(itemCount);

        try {
            resolveKeywords(query.keywords(), keywordMatches);
            resolveStats(query.stats(), statMatches);
            intersectInto(keywordMatches, statMatches, candidates);

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
            BitSetPool.release(candidates);
        }
    }

    // ── Ranking ────────────────────────────────────────────────────────────────────

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

    // ── Index building ─────────────────────────────────────────────────────────────

    private void indexItem(int itemIndex, ItemStack stack, @Nullable NeuItem neuItem,
                           Set<String> autocompleteTokens) {
        String displayName = stack.getHoverName().getString();
        tokenize(displayName, (token, idx) -> addDisplayNameToken(token, idx, autocompleteTokens), itemIndex);

        if (neuItem != null) {
            indexNeuItem(itemIndex, neuItem, autocompleteTokens);
        }
    }

    private void indexNeuItem(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens) {
        if (neuItem.internalName != null) {
            String lower = neuItem.internalName.toLowerCase(java.util.Locale.ROOT);
            addNameToken(lower, itemIndex);
            for (String part : lower.split("_")) {
                if (part.length() > 1) {
                    addNameToken(part, itemIndex);
                }
            }
        }

        if (neuItem.lore != null) {
            for (String line : neuItem.lore) {
                tokenize(line, this::addAnyToken, itemIndex);
            }
        }

        if (neuItem.category != null) {
            String categoryName = neuItem.category.name().toLowerCase(java.util.Locale.ROOT);
            addAnyToken(categoryName, itemIndex);
            addAutocompleteToken(categoryName, autocompleteTokens);
        }

        indexPetType(itemIndex, neuItem, autocompleteTokens);
        indexReforge(itemIndex, neuItem, autocompleteTokens);
        indexStats(itemIndex, neuItem, autocompleteTokens);
        indexSlayer(itemIndex, neuItem, autocompleteTokens);

        if (neuItem.crafttext != null && !neuItem.crafttext.isEmpty()) {
            tokenize(neuItem.crafttext, this::addAnyToken, itemIndex);
        }
    }

    private void indexPetType(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens) {
        if (neuItem.category != SkyblockItemCategory.PET || neuItem.internalName == null) {
            return;
        }
        String petId = extractPetId(neuItem.internalName);
        if (petId == null) {
            return;
        }
        String skill = NeuConstantsRegistry.getPetType(petId);
        if (skill != null) {
            String token = skill.toLowerCase(java.util.Locale.ROOT);
            addAnyToken(token, itemIndex);
            addAutocompleteToken(token, autocompleteTokens);
        }
    }

    private void indexReforge(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens) {
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
        }, itemIndex);
    }

    private void indexStats(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens) {
        if (neuItem.internalName == null) {
            return;
        }

        Map<String, Integer> baseStats = HypixelItemsRegistry.getBaseStats(neuItem.internalName);
        if (baseStats != null) {
            for (Map.Entry<String, Integer> entry : baseStats.entrySet()) {
                String statName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                indexStatName(statName, itemIndex, autocompleteTokens);
                addStatValue(statName, entry.getValue(), itemIndex);
            }
        }

        Map<String, int[]> tieredStats = HypixelItemsRegistry.getTieredStats(neuItem.internalName);
        if (tieredStats != null) {
            for (Map.Entry<String, int[]> entry : tieredStats.entrySet()) {
                String statName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                indexStatName(statName, itemIndex, autocompleteTokens);
                for (int value : entry.getValue()) {
                    addStatValue(statName, value, itemIndex);
                }
            }
        }
    }

    private void indexStatName(String statName, int itemIndex, Set<String> autocompleteTokens) {
        addAnyToken(statName, itemIndex);
        addAutocompleteToken(statName, autocompleteTokens);
        for (String part : statName.split("_")) {
            if (part.length() > 1) {
                addAnyToken(part, itemIndex);
                addAutocompleteToken(part, autocompleteTokens);
            }
        }
    }

    private void addStatValue(String statName, int value, int itemIndex) {
        TreeMap<Integer, BitSet> valueMap = statIndex.computeIfAbsent(statName, k -> new TreeMap<>());
        valueMap.computeIfAbsent(value, k -> new BitSet()).set(itemIndex);
    }

    private void indexSlayer(int itemIndex, NeuItem neuItem, Set<String> autocompleteTokens) {
        if (neuItem.slayerReq == null || neuItem.slayerReq.isEmpty()) {
            return;
        }
        String type = extractSlayerType(neuItem.slayerReq);
        if (type != null) {
            addAnyToken(type, itemIndex);
            addAutocompleteToken(type, autocompleteTokens);
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
    private static String extractSlayerType(String slayerReq) {
        int underscore = slayerReq.indexOf('_');
        return (underscore > 0)
                ? slayerReq.substring(0, underscore).toLowerCase(java.util.Locale.ROOT)
                : null;
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
