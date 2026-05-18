package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a single best autocomplete completion for the RRV search bar.
 *
 * <p>Uses four dictionaries in priority order:
 * <ol>
 *   <li><b>Full display names</b> — what users actually search for (e.g. "aspect of the end")</li>
 *   <li><b>Community acronyms</b> — "aote" → "Aspect of the End", "hpb" → "Hot Potato Book"</li>
 *   <li><b>Display name words</b> — individual words extracted from display names
 *       (e.g. typing "drag" → "dragons")</li>
 *   <li><b>Quality tokens</b> — categories, stats, reforge names, pet types, slayer types</li>
 * </ol>
 *
 * <p>Ranked by item rarity (plus recipe presence and name-length tiebreakers) so higher-tier,
 * more relevant items outrank alphabetically earlier noise. Respects the active category filter.
 *
 * <p>Only activates when the last typed word has at least {@value #MIN_PREFIX_LENGTH} characters,
 * does not look like a math expression, and is not already an exact match.
 */
public final class SearchAutocomplete {

    private static final int MIN_PREFIX_LENGTH = 2;
    private static final int ACRONYM_MAX_LENGTH = 5;

    private final String[] tokens;
    private final DisplayNameEntry[] displayNames;
    private final String[] displayWords;
    /** Maps each display word to the best display name that contains it (used for category filtering). */
    private final String[] wordToOwnerName;
    private final Map<String, DisplayNameEntry> nameToEntry;
    private final Map<SkyblockItemCategory, BitSet> categoryIndex;
    private final Map<String, List<AcronymEntry>> acronymIndex;

    public SearchAutocomplete(String[] cleanTokens, List<ItemStack> items,
                              Map<ItemStack, NeuItem> neuItems,
                              Map<SkyblockItemCategory, BitSet> categoryIndex) {
        this.tokens = cleanTokens;
        this.categoryIndex = categoryIndex;
        this.displayNames = buildDisplayNameIndex(items, neuItems);
        WordIndex wordIndex = buildWordIndex(this.displayNames);
        this.displayWords = wordIndex.words;
        this.wordToOwnerName = wordIndex.owners;
        this.nameToEntry = new HashMap<>(this.displayNames.length * 2);
        for (DisplayNameEntry entry : this.displayNames) {
            this.nameToEntry.put(entry.name, entry);
        }
        this.acronymIndex = buildAcronymIndex(this.displayNames);
    }

    /**
     * Immutable suggestion returned by {@link #suggest}.
     *
     * @param text              the completion text
     * @param replaceWholeQuery if {@code true}, accepting the suggestion replaces the entire
     *                          query (used for acronym matches). If {@code false}, only the
     *                          last word is replaced.
     */
    public record Suggestion(String text, boolean replaceWholeQuery) {
        public static Suggestion word(String text) {
            return new Suggestion(text, false);
        }

        public static Suggestion full(String text) {
            return new Suggestion(text, true);
        }
    }

    /**
     * Returns the single best autocomplete suggestion for {@code query}.
     *
     * @param query          the current search text (already lowercased)
     * @param activeCategory the selected category tab, or {@code null} for all items
     * @return best suggestion, or {@code null} if none match
     */
    @Nullable
    public Suggestion suggest(String query, @Nullable SkyblockItemCategory activeCategory) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (looksLikeMath(query)) {
            return null;
        }

        String trimmed = query.trim();
        String lastWord = extractLastWord(query);
        if (lastWord.length() < MIN_PREFIX_LENGTH) {
            return null;
        }
        if (isExactMatch(lastWord)) {
            return null;
        }

        // 1. Full query as display-name prefix (e.g. "aspect of the bo" → "bow")
        if (trimmed.length() >= MIN_PREFIX_LENGTH && !trimmed.equals(lastWord)) {
            if (!isExactDisplayNameMatch(trimmed)) {
                String fullMatch = findBestDisplayNameMatch(trimmed, activeCategory);
                if (fullMatch != null) {
                    String completionWord = extractLastWord(fullMatch);
                    // Only use the full-query match if the completed last word
                    // genuinely starts with what the user typed.
                    if (completionWord.startsWith(lastWord)) {
                        return Suggestion.word(completionWord);
                    }
                }
            }
        }

        // 2. Last word as display-name prefix
        String match = findBestDisplayNameMatch(lastWord, activeCategory);
        if (match != null) {
            return Suggestion.word(match);
        }

        // 3. Acronym match for short words (e.g. "aote" → "Aspect of the End")
        if (lastWord.length() <= ACRONYM_MAX_LENGTH) {
            Suggestion acronym = findBestAcronymMatch(lastWord, activeCategory);
            if (acronym != null) {
                return acronym;
            }
        }

        // 4. Word-level prefix match (e.g. "drag" → "dragons")
        match = findBestWordMatch(lastWord, activeCategory);
        if (match != null) {
            return Suggestion.word(match);
        }

        // 5. Quality token prefix match
        match = findFirstTokenMatch(lastWord);
        if (match != null) {
            return Suggestion.word(match);
        }

        return null;
    }

    // ── Dictionary lookup ────────────────────────────────────────────────────────────

    @Nullable
    private String findBestDisplayNameMatch(String prefix, @Nullable SkyblockItemCategory category) {
        int idx = Arrays.binarySearch(displayNames, keyEntry(prefix), Comparator.comparing(DisplayNameEntry::name));
        if (idx < 0) {
            idx = -idx - 1;
        }

        String best = null;
        int bestScore = -1;
        int bestLen = Integer.MAX_VALUE;

        for (int i = idx; i < displayNames.length && displayNames[i].name.startsWith(prefix); i++) {
            DisplayNameEntry entry = displayNames[i];
            if (category != null && !hasItemInCategory(entry.itemIndices, category)) {
                continue;
            }
            if (entry.score > bestScore || (entry.score == bestScore && entry.name.length() < bestLen)) {
                bestScore = entry.score;
                bestLen = entry.name.length();
                best = entry.name;
            }
        }
        return best;
    }

    @Nullable
    private String findBestWordMatch(String prefix, @Nullable SkyblockItemCategory category) {
        int idx = Arrays.binarySearch(displayWords, prefix);
        if (idx < 0) {
            idx = -idx - 1;
        }

        for (int i = idx; i < displayWords.length && displayWords[i].startsWith(prefix); i++) {
            if (category != null) {
                DisplayNameEntry entry = nameToEntry.get(wordToOwnerName[i]);
                if (entry == null || !hasItemInCategory(entry.itemIndices, category)) {
                    continue;
                }
            }
            return displayWords[i];
        }
        return null;
    }

    @Nullable
    private Suggestion findBestAcronymMatch(String acronym, @Nullable SkyblockItemCategory category) {
        List<AcronymEntry> entries = acronymIndex.get(acronym);
        if (entries == null) {
            return null;
        }
        for (AcronymEntry entry : entries) {
            if (category != null) {
                DisplayNameEntry displayEntry = nameToEntry.get(entry.displayName);
                if (displayEntry == null || !hasItemInCategory(displayEntry.itemIndices, category)) {
                    continue;
                }
            }
            return Suggestion.full(entry.displayName);
        }
        return null;
    }

    @Nullable
    private String findFirstTokenMatch(String prefix) {
        int idx = Arrays.binarySearch(tokens, prefix);
        if (idx < 0) {
            idx = -idx - 1;
        }

        for (int i = idx; i < tokens.length && tokens[i].startsWith(prefix); i++) {
            return tokens[i];
        }
        return null;
    }

    // ── Index builders ─────────────────────────────────────────────────────────────

    private static DisplayNameEntry[] buildDisplayNameIndex(List<ItemStack> items,
                                                            Map<ItemStack, NeuItem> neuItems) {
        Map<String, BitSet> nameToIndices = new HashMap<>(items.size());
        Map<String, Integer> nameToScore = new HashMap<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            String name = StringUtil.stripColorCodes(stack.getHoverName().getString())
                    .toLowerCase(java.util.Locale.ROOT);
            if (name.isBlank() || name.length() <= 1) {
                continue;
            }

            nameToIndices.computeIfAbsent(name, k -> new BitSet()).set(i);

            NeuItem neuItem = neuItems.get(stack);
            int score = scoreForItem(neuItem);
            nameToScore.merge(name, score, Math::max);
        }

        List<DisplayNameEntry> entries = new ArrayList<>(nameToIndices.size());
        for (Map.Entry<String, BitSet> e : nameToIndices.entrySet()) {
            String name = e.getKey();
            int score = nameToScore.getOrDefault(name, 0);
            entries.add(new DisplayNameEntry(name, score, e.getValue()));
        }

        entries.sort(Comparator.comparing(DisplayNameEntry::name));
        return entries.toArray(new DisplayNameEntry[0]);
    }

    private static WordIndex buildWordIndex(DisplayNameEntry[] displayNames) {
        Map<String, String> wordToBestName = new HashMap<>();
        Map<String, Integer> wordToBestScore = new HashMap<>();

        for (DisplayNameEntry entry : displayNames) {
            for (String word : entry.name.split("\\s+")) {
                if (word.length() < MIN_PREFIX_LENGTH) {
                    continue;
                }
                Integer existing = wordToBestScore.get(word);
                if (existing == null || entry.score > existing) {
                    wordToBestScore.put(word, entry.score);
                    wordToBestName.put(word, entry.name);
                }
            }
        }

        List<Map.Entry<String, String>> sorted = new ArrayList<>(wordToBestName.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        String[] words = new String[sorted.size()];
        String[] owners = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            words[i] = sorted.get(i).getKey();
            owners[i] = sorted.get(i).getValue();
        }

        return new WordIndex(words, owners);
    }

    private static Map<String, List<AcronymEntry>> buildAcronymIndex(DisplayNameEntry[] displayNames) {
        Map<String, List<AcronymEntry>> map = new HashMap<>();
        for (DisplayNameEntry entry : displayNames) {
            String acronym = buildAcronym(entry.name);
            if (acronym.length() < 2) {
                continue;
            }
            map.computeIfAbsent(acronym, k -> new ArrayList<>())
                    .add(new AcronymEntry(acronym, entry.name, entry.score));
        }
        for (List<AcronymEntry> list : map.values()) {
            list.sort(Comparator.comparingInt((AcronymEntry e) -> e.score).reversed()
                    .thenComparingInt(e -> e.displayName.length()));
        }
        return map;
    }

    private static String buildAcronym(String displayName) {
        StringBuilder sb = new StringBuilder();
        for (String word : displayName.split("\\s+")) {
            if (!word.isEmpty() && Character.isLetter(word.charAt(0))) {
                sb.append(Character.toLowerCase(word.charAt(0)));
            }
        }
        return sb.toString();
    }

    // ── Scoring ────────────────────────────────────────────────────────────────────

    private static int scoreForItem(@Nullable NeuItem neuItem) {
        if (neuItem == null) {
            return 0;
        }
        var rarity = neuItem.rarity != null ? neuItem.rarity : SkyblockItemCategory.extractRarity(neuItem);
        int base = rarity != null ? (rarity.ordinal() + 1) * 100 : 0;

        // Tiebreakers within same rarity band
        if (neuItem.hasCraftingRecipe() || neuItem.hasForgeRecipe()) {
            base += 20;
        }
        if (neuItem.hasNpcShopRecipes()) {
            base += 10;
        }

        // Penalise low-relevance categories in autocomplete (not search results)
        if (neuItem.category != null) {
            switch (neuItem.category) {
                case NPC -> base -= 300;
                case COSMETIC -> base -= 100;
                case MISC -> {
                    if (!neuItem.hasAnyRecipe()) {
                        base -= 50;
                    }
                }
                default -> {
                    // no penalty
                }
            }
        }
        return Math.max(base, 0);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private boolean isExactMatch(String word) {
        return isExactDisplayNameMatch(word)
                || Arrays.binarySearch(tokens, word) >= 0;
    }

    private boolean isExactDisplayNameMatch(String word) {
        return Arrays.binarySearch(displayNames, keyEntry(word), Comparator.comparing(DisplayNameEntry::name)) >= 0;
    }

    private boolean hasItemInCategory(BitSet itemIndices, SkyblockItemCategory category) {
        BitSet catItems = categoryIndex.get(category);
        return catItems != null && catItems.intersects(itemIndices);
    }

    /**
     * Extracts the last whitespace-delimited word from {@code query}.
     *
     * <p>Trailing whitespace is ignored, so {@code "hello "} returns {@code "hello"}.
     */
    public static String extractLastWord(String query) {
        int len = query.length();
        int end = len;
        while (end > 0 && Character.isWhitespace(query.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(query.charAt(start - 1))) {
            start--;
        }
        return query.substring(start, end);
    }

    private static boolean looksLikeMath(String query) {
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '+' || c == '*' || c == '/' || c == '^' || c == '%' || c == '=') {
                return true;
            }
            if (c == '-' && i > 0 && Character.isDigit(query.charAt(i - 1))) {
                return true;
            }
            if (c == 'x' && i > 0 && Character.isDigit(query.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    private static DisplayNameEntry keyEntry(String name) {
        return new DisplayNameEntry(name, 0, new BitSet());
    }

    // ── Records ────────────────────────────────────────────────────────────────────

    private record DisplayNameEntry(String name, int score, BitSet itemIndices) {}

    private record WordIndex(String[] words, String[] owners) {}

    private record AcronymEntry(String acronym, String displayName, int score) {}
}
