package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
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
 * <p>Uses three dictionaries in priority order:
 * <ol>
 *   <li><b>Full display names</b> — what users actually search for (e.g. "aspect of the end")</li>
 *   <li><b>Display name words</b> — individual words extracted from display names, mapped back
 *       to their full display name (e.g. typing "drag" → "aspect of the dragons")</li>
 *   <li><b>Quality tokens</b> — categories, stats, reforge names, pet types, slayer types</li>
 * </ol>
 *
 * <p>Ranked by item rarity so higher-tier items outrank alphabetically earlier commons.
 * Respects the active category filter when one is selected.
 *
 * <p>Only activates when the last typed word has at least {@value #MIN_PREFIX_LENGTH} characters,
 * does not look like a math expression, and is not already an exact match.
 */
public final class SearchAutocomplete {

    private static final int MIN_PREFIX_LENGTH = 2;

    private final String[] tokens;
    private final DisplayNameEntry[] displayNames;
    private final String[] displayWords;
    private final String[] wordToDisplayName;
    private final Map<String, DisplayNameEntry> nameToEntry;
    private final Map<SkyblockItemCategory, BitSet> categoryIndex;

    public SearchAutocomplete(String[] cleanTokens, List<ItemStack> items,
                              Map<ItemStack, NeuItem> neuItems,
                              Map<SkyblockItemCategory, BitSet> categoryIndex) {
        this.tokens = cleanTokens;
        this.categoryIndex = categoryIndex;
        this.displayNames = buildDisplayNameIndex(items, neuItems);
        WordIndex wordIndex = buildWordIndex(this.displayNames);
        this.displayWords = wordIndex.words;
        this.wordToDisplayName = wordIndex.completions;
        this.nameToEntry = new HashMap<>(this.displayNames.length * 2);
        for (DisplayNameEntry entry : this.displayNames) {
            this.nameToEntry.put(entry.name, entry);
        }
    }

    /**
     * Returns the single best autocomplete completion for {@code query}.
     *
     * <p>The returned string is the full completion of the last word in the query.
     * For example, if the user typed {@code "aspect of the bo"}, this returns
     * {@code "bow"} (the matching word), not just the suffix {@code "w"}.
     *
     * @param query          the current search text (already lowercased)
     * @param activeCategory the selected category tab, or {@code null} for all items
     * @return full completion string, or {@code null} if none match
     */
    @Nullable
    public String suggest(String query, @Nullable SkyblockItemCategory activeCategory) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (looksLikeMath(query)) {
            return null;
        }

        String lastWord = extractLastWord(query);
        if (lastWord.length() < MIN_PREFIX_LENGTH) {
            return null;
        }
        if (isExactMatch(lastWord)) {
            return null;
        }

        String match = findBestDisplayNameMatch(lastWord, activeCategory);
        if (match != null) {
            return match;
        }

        match = findBestWordMatch(lastWord, activeCategory);
        if (match != null) {
            return match;
        }

        return findFirstTokenMatch(lastWord);
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

        for (int i = idx; i < displayNames.length && displayNames[i].name.startsWith(prefix); i++) {
            DisplayNameEntry entry = displayNames[i];
            if (category != null && !hasItemInCategory(entry.itemIndices, category)) {
                continue;
            }
            if (entry.score > bestScore) {
                bestScore = entry.score;
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
            String displayName = wordToDisplayName[i];
            if (category != null) {
                DisplayNameEntry entry = nameToEntry.get(displayName);
                if (entry == null || !hasItemInCategory(entry.itemIndices, category)) {
                    continue;
                }
            }
            return displayName;
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
            String name = stripColorCodes(stack.getHoverName().getString())
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
        String[] completions = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            words[i] = sorted.get(i).getKey();
            completions[i] = sorted.get(i).getValue();
        }

        return new WordIndex(words, completions);
    }

    // ── Scoring ────────────────────────────────────────────────────────────────────

    private static int scoreForItem(@Nullable NeuItem neuItem) {
        if (neuItem == null) {
            return 0;
        }
        var rarity = SkyblockItemCategory.extractRarity(neuItem);
        if (rarity == null) {
            return 0;
        }
        return (rarity.ordinal() + 1) * 10;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private boolean isExactMatch(String word) {
        return Arrays.binarySearch(displayNames, keyEntry(word), Comparator.comparing(DisplayNameEntry::name)) >= 0
                || Arrays.binarySearch(tokens, word) >= 0
                || Arrays.binarySearch(displayWords, word) >= 0;
    }

    private boolean hasItemInCategory(BitSet itemIndices, SkyblockItemCategory category) {
        BitSet catItems = categoryIndex.get(category);
        if (catItems == null) {
            return false;
        }
        for (int i = itemIndices.nextSetBit(0); i >= 0; i = itemIndices.nextSetBit(i + 1)) {
            if (catItems.get(i)) {
                return true;
            }
        }
        return false;
    }

    private static String extractLastWord(String query) {
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

    private static DisplayNameEntry keyEntry(String name) {
        return new DisplayNameEntry(name, 0, new BitSet());
    }

    // ── Records ────────────────────────────────────────────────────────────────────

    private record DisplayNameEntry(String name, int score, BitSet itemIndices) {}

    private record WordIndex(String[] words, String[] completions) {}
}
