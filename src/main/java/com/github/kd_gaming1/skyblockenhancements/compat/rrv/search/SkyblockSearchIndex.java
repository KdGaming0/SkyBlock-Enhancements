package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.ReforgeStoneData;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
 * </ul>
 */
public final class SkyblockSearchIndex {

    /** Maximum tokens expanded for a single prefix query. Guards against {@code "a"} → everything. */
    private static final int MAX_PREFIX_EXPANSION = 64;
    /** Maximum cached query results. */
    private static final int QUERY_CACHE_CAPACITY = 64;

    private final List<ItemStack> items;
    private final Map<String, BitSet> nameTokenIndex;
    private final Map<String, BitSet> anyTokenIndex;
    private final String[] sortedTokens;
    private final Map<String, TreeMap<Integer, BitSet>> statIndex;
    private final Map<String, SearchResult> queryCache;

    public SkyblockSearchIndex(List<ItemStack> items, Map<ItemStack, NeuItem> neuItems) {
        this.items = List.copyOf(items);
        int size = this.items.size();

        this.nameTokenIndex = new java.util.HashMap<>(4096);
        this.anyTokenIndex = new java.util.HashMap<>(8192);
        this.statIndex = new java.util.HashMap<>(256);

        for (int i = 0; i < size; i++) {
            ItemStack stack = this.items.get(i);
            NeuItem neuItem = neuItems.get(stack);
            indexItem(i, stack, neuItem);
        }

        this.sortedTokens = anyTokenIndex.keySet().stream().sorted().toArray(String[]::new);

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

    /**
     * Executes {@code query} against the inverted index and returns a ranked {@link SearchResult}.
     * Results are cached so identical consecutive queries are O(1).
     */
    public SearchResult search(SearchQuery query) {
        if (query.isEmpty()) {
            BitSet all = new BitSet(items.size());
            all.set(0, items.size());
            return new SearchResult(all, new BitSet(), new BitSet());
        }

        String cacheKey = buildCacheKey(query);
        SearchResult cached = queryCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        BitSet keywordMatches = resolveKeywords(query.keywords());
        BitSet statMatches = resolveStats(query.stats());

        BitSet candidates = intersect(keywordMatches, statMatches);
        if (candidates == null || candidates.isEmpty()) {
            SearchResult empty = new SearchResult(new BitSet(), new BitSet(), new BitSet());
            queryCache.put(cacheKey, empty);
            return empty;
        }

        SearchResult result = rank(query, candidates);
        queryCache.put(cacheKey, result);
        return result;
    }

    // ── Ranking ────────────────────────────────────────────────────────────────────

    private SearchResult rank(SearchQuery query, BitSet candidates) {
        BitSet first = new BitSet();
        BitSet second = new BitSet();
        BitSet third = new BitSet();

        if (!query.keywords().isEmpty()) {
            BitSet nameMatches = resolveKeywordsInName(query.keywords());
            if (nameMatches != null) {
                first = (BitSet) candidates.clone();
                first.and(nameMatches);
            }

            candidates.andNot(first);
            second = candidates;

            if (query.keywords().size() == 1 && query.stats().isEmpty()) {
                BitSet prefixMatches = resolvePrefixUnion(query.keywords().get(0).token());
                third = (BitSet) prefixMatches.clone();
                third.andNot(first);
                third.andNot(second);
            }
        } else {
            second = candidates;
        }

        return new SearchResult(first, second, third);
    }

    // ── Keyword resolution ─────────────────────────────────────────────────────────

    @Nullable
    private BitSet resolveKeywords(List<SearchQuery.KeywordClause> keywords) {
        if (keywords.isEmpty()) {
            return null;
        }

        BitSet result = null;
        for (SearchQuery.KeywordClause clause : keywords) {
            BitSet matches = resolveKeyword(clause.token());
            if (matches == null || matches.isEmpty()) {
                return new BitSet();
            }
            result = (result == null) ? (BitSet) matches.clone() : and(result, matches);
        }
        return result;
    }

    @Nullable
    private BitSet resolveKeywordsInName(List<SearchQuery.KeywordClause> keywords) {
        if (keywords.isEmpty()) {
            return null;
        }

        BitSet result = null;
        for (SearchQuery.KeywordClause clause : keywords) {
            BitSet matches = resolveKeywordInName(clause.token());
            if (matches == null || matches.isEmpty()) {
                return new BitSet();
            }
            result = (result == null) ? (BitSet) matches.clone() : and(result, matches);
        }
        return result;
    }

    /** Looks up a keyword in the any-token index, falling back to prefix expansion. */
    private BitSet resolveKeyword(String token) {
        BitSet exact = anyTokenIndex.get(token);
        if (exact != null) {
            return exact;
        }
        return resolvePrefixUnion(token);
    }

    /** Looks up a keyword in the name-only index, falling back to prefix expansion. */
    private BitSet resolveKeywordInName(String token) {
        BitSet exact = nameTokenIndex.get(token);
        if (exact != null) {
            return exact;
        }
        return resolvePrefixUnionInName(token);
    }

    /** Unions all any-token BitSets whose token starts with {@code prefix}. */
    private BitSet resolvePrefixUnion(String prefix) {
        return resolvePrefixUnion(prefix, anyTokenIndex);
    }

    /** Unions all name-token BitSets whose token starts with {@code prefix}. */
    private BitSet resolvePrefixUnionInName(String prefix) {
        return resolvePrefixUnion(prefix, nameTokenIndex);
    }

    private BitSet resolvePrefixUnion(String prefix, Map<String, BitSet> index) {
        if (prefix.length() < 2) {
            return new BitSet();
        }

        int idx = Arrays.binarySearch(sortedTokens, prefix);
        if (idx < 0) {
            idx = -idx - 1;
        }

        BitSet result = new BitSet();
        int count = 0;
        for (int i = idx; i < sortedTokens.length && sortedTokens[i].startsWith(prefix); i++) {
            BitSet bs = index.get(sortedTokens[i]);
            if (bs != null) {
                result.or(bs);
            }
            if (++count >= MAX_PREFIX_EXPANSION) {
                break;
            }
        }
        return result;
    }

    // ── Stat resolution ────────────────────────────────────────────────────────────

    @Nullable
    private BitSet resolveStats(List<SearchQuery.StatClause> stats) {
        if (stats.isEmpty()) {
            return null;
        }

        BitSet result = null;
        for (SearchQuery.StatClause clause : stats) {
            BitSet matches = resolveStat(clause);
            if (matches == null || matches.isEmpty()) {
                return new BitSet();
            }
            result = (result == null) ? (BitSet) matches.clone() : and(result, matches);
        }
        return result;
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

    private void indexItem(int itemIndex, ItemStack stack, @Nullable NeuItem neuItem) {
        String displayName = stack.getHoverName().getString();
        tokenize(displayName, this::addNameToken, itemIndex);

        if (neuItem != null) {
            indexNeuItem(itemIndex, neuItem);
        }
    }

    private void indexNeuItem(int itemIndex, NeuItem neuItem) {
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
            addAnyToken(neuItem.category.name().toLowerCase(java.util.Locale.ROOT), itemIndex);
        }

        indexPetType(itemIndex, neuItem);
        indexReforge(itemIndex, neuItem);
        indexStats(itemIndex, neuItem);
        indexSlayer(itemIndex, neuItem);

        if (neuItem.crafttext != null && !neuItem.crafttext.isEmpty()) {
            tokenize(neuItem.crafttext, this::addAnyToken, itemIndex);
        }
    }

    private void indexPetType(int itemIndex, NeuItem neuItem) {
        if (neuItem.category != SkyblockItemCategory.PET || neuItem.internalName == null) {
            return;
        }
        String petId = extractPetId(neuItem.internalName);
        if (petId == null) {
            return;
        }
        String skill = NeuConstantsRegistry.getPetType(petId);
        if (skill != null) {
            addAnyToken(skill.toLowerCase(java.util.Locale.ROOT), itemIndex);
        }
    }

    private void indexReforge(int itemIndex, NeuItem neuItem) {
        if (neuItem.internalName == null) {
            return;
        }
        ReforgeStoneData stone = NeuConstantsRegistry.getAllReforgeStones().get(neuItem.internalName);
        if (stone == null || stone.reforgeName() == null) {
            return;
        }
        tokenize(stone.reforgeName(), this::addAnyToken, itemIndex);
    }

    private void indexStats(int itemIndex, NeuItem neuItem) {
        if (neuItem.internalName == null) {
            return;
        }

        Map<String, Integer> baseStats = HypixelItemsRegistry.getBaseStats(neuItem.internalName);
        if (baseStats != null) {
            for (Map.Entry<String, Integer> entry : baseStats.entrySet()) {
                String statName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                indexStatName(statName, itemIndex);
                addStatValue(statName, entry.getValue(), itemIndex);
            }
        }

        Map<String, int[]> tieredStats = HypixelItemsRegistry.getTieredStats(neuItem.internalName);
        if (tieredStats != null) {
            for (Map.Entry<String, int[]> entry : tieredStats.entrySet()) {
                String statName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                indexStatName(statName, itemIndex);
                for (int value : entry.getValue()) {
                    addStatValue(statName, value, itemIndex);
                }
            }
        }
    }

    private void indexStatName(String statName, int itemIndex) {
        addAnyToken(statName, itemIndex);
        for (String part : statName.split("_")) {
            if (part.length() > 1) {
                addAnyToken(part, itemIndex);
            }
        }
    }

    private void addStatValue(String statName, int value, int itemIndex) {
        TreeMap<Integer, BitSet> valueMap = statIndex.computeIfAbsent(statName, k -> new TreeMap<>());
        valueMap.computeIfAbsent(value, k -> new BitSet()).set(itemIndex);
    }

    private void indexSlayer(int itemIndex, NeuItem neuItem) {
        if (neuItem.slayerReq == null || neuItem.slayerReq.isEmpty()) {
            return;
        }
        String type = extractSlayerType(neuItem.slayerReq);
        if (type != null) {
            addAnyToken(type, itemIndex);
        }
    }

    // ── Token helpers ──────────────────────────────────────────────────────────────

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

    private static void addToken(Map<String, BitSet> index, String token, int itemIndex) {
        index.computeIfAbsent(token, k -> new BitSet()).set(itemIndex);
    }

    /** Strips Minecraft color codes and splits into alphanumeric tokens. */
    private static void tokenize(String raw, TokenConsumer consumer, int itemIndex) {
        if (raw == null) {
            return;
        }

        String clean = stripColorCodes(raw).toLowerCase(java.util.Locale.ROOT);
        int len = clean.length();
        int start = -1;

        for (int i = 0; i <= len; i++) {
            char c = i < len ? clean.charAt(i) : '\0';
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) {
                    start = i;
                }
            } else {
                if (start >= 0) {
                    String token = clean.substring(start, i);
                    if (token.length() > 1) {
                        consumer.accept(token, itemIndex);
                    }
                    start = -1;
                }
            }
        }
    }

    private static String stripColorCodes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int len = raw.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = raw.charAt(i);
            if (ch == '§' && i + 1 < len) {
                i++;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    @Nullable
    private static String extractPetId(String internalName) {
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

    private static BitSet and(BitSet a, BitSet b) {
        a.and(b);
        return a;
    }

    @Nullable
    private static BitSet intersect(@Nullable BitSet a, @Nullable BitSet b) {
        if (a == null) return (b == null) ? null : (BitSet) b.clone();
        if (b == null) return (BitSet) a.clone();
        BitSet result = (BitSet) a.clone();
        result.and(b);
        return result;
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
        return sb.toString();
    }

    @FunctionalInterface
    private interface TokenConsumer {
        void accept(String token, int itemIndex);
    }
}
