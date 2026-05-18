package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Provides fuzzy token matching for typo-tolerant search.
 *
 * <p>When an exact keyword or prefix match returns empty, the search index falls back
 * to this matcher, which finds tokens within Damerau-Levenshtein distance 1.
 *
 * <p>To keep search latency low:
 * <ul>
 *   <li>Only the first unmatched keyword triggers fuzzy matching</li>
 *   <li>At most 8 candidate tokens are evaluated</li>
 *   <li>Only substitutions, insertions, deletions, and adjacent transpositions are considered</li>
 * </ul>
 */
public final class FuzzyTokenMatcher {

    /** Maximum fuzzy candidates to evaluate per failed keyword. */
    private static final int MAX_CANDIDATES = 8;

    private final String[] sortedTokens;

    public FuzzyTokenMatcher(String[] sortedTokens) {
        this.sortedTokens = sortedTokens;
    }

    /**
     * Attempts to find tokens that are within 1 edit distance of {@code token}.
     *
     * @return a BitSet union of all fuzzy-matched items, or an empty BitSet if no fuzzy matches
     */
    public BitSet fuzzyMatch(String token, java.util.function.Function<String, BitSet> indexLookup) {
        if (token.length() < 2) {
            return new BitSet();
        }

        BitSet result = new BitSet();
        int count = 0;

        for (String candidate : sortedTokens) {
            if (candidate.length() < 2) continue;
            if (Math.abs(candidate.length() - token.length()) > 1) continue;

            if (editDistanceOne(token, candidate)) {
                BitSet bits = indexLookup.apply(candidate);
                if (bits != null) {
                    result.or(bits);
                }
                if (++count >= MAX_CANDIDATES) break;
            }
        }

        return result;
    }

    /**
     * Returns {@code true} if the Damerau-Levenshtein distance between {@code a} and {@code b}
     * is exactly 1 (substitution, insertion, deletion, or adjacent transposition).
     */
    static boolean editDistanceOne(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();

        if (lenA == lenB) {
            // Substitution or transposition
            int diff = 0;
            int diffPos = -1;
            for (int i = 0; i < lenA; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    diff++;
                    if (diffPos < 0) diffPos = i;
                    if (diff > 2) return false;
                }
            }
            if (diff == 1) return true; // Substitution
            if (diff == 2) {
                // Check for adjacent transposition
                return diffPos + 1 < lenA
                        && a.charAt(diffPos) == b.charAt(diffPos + 1)
                        && a.charAt(diffPos + 1) == b.charAt(diffPos);
            }
            return false;
        }

        if (lenA + 1 == lenB) {
            return isOneInsertion(a, b);
        }
        if (lenB + 1 == lenA) {
            return isOneInsertion(b, a);
        }

        return false;
    }

    private static boolean isOneInsertion(String shorter, String longer) {
        int i = 0;
        int j = 0;
        boolean foundDiff = false;
        while (i < shorter.length() && j < longer.length()) {
            if (shorter.charAt(i) != longer.charAt(j)) {
                if (foundDiff) return false;
                foundDiff = true;
                j++; // skip the extra char in longer
            } else {
                i++;
                j++;
            }
        }
        return true;
    }
}
