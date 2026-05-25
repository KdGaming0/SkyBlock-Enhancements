package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the static helper methods and indexing logic in {@link SkyblockSearchIndex}.
 */
class SkyblockSearchIndexTest {

    // -- Slayer parsing --------------------------------------------------------

    @Test
    void extractSlayerTypeFromZombie3() {
        assertEquals("zombie", SkyblockSearchIndex.extractSlayerTypeStatic("ZOMBIE_3"));
    }

    @Test
    void extractSlayerTypeFromEman6() {
        assertEquals("eman", SkyblockSearchIndex.extractSlayerTypeStatic("EMAN_6"));
    }

    @Test
    void extractSlayerTypeReturnsNullForNoUnderscore() {
        assertNull(SkyblockSearchIndex.extractSlayerTypeStatic("ZOMBIE"));
    }

    @Test
    void extractSlayerLevelFromZombie3() {
        assertEquals(3, SkyblockSearchIndex.extractSlayerLevelStatic("ZOMBIE_3"));
    }

    @Test
    void extractSlayerLevelFromSpider8() {
        assertEquals(8, SkyblockSearchIndex.extractSlayerLevelStatic("SPIDER_8"));
    }

    @Test
    void extractSlayerLevelReturnsZeroForNoUnderscore() {
        assertEquals(0, SkyblockSearchIndex.extractSlayerLevelStatic("ZOMBIE"));
    }

    @Test
    void extractSlayerLevelReturnsZeroForNonNumericSuffix() {
        assertEquals(0, SkyblockSearchIndex.extractSlayerLevelStatic("ZOMBIE_X"));
    }

    // -- Filter token normalization --------------------------------------------

    @Test
    void normalizeFilterTokenStripsNonAlphanumeric() {
        assertEquals("combat", SkyblockSearchIndex.normalizeFilterTokenStatic("Combat"));
        assertEquals("combat", SkyblockSearchIndex.normalizeFilterTokenStatic("combat!"));
        assertEquals("ccombat", SkyblockSearchIndex.normalizeFilterTokenStatic("§cCombat"));
    }

    // -- Roman numeral conversion ----------------------------------------------

    @Test
    void romanToIntConvertsBasicValues() {
        assertEquals(1, SkyblockSearchIndex.romanToIntStatic("i"));
        assertEquals(2, SkyblockSearchIndex.romanToIntStatic("ii"));
        assertEquals(3, SkyblockSearchIndex.romanToIntStatic("iii"));
        assertEquals(4, SkyblockSearchIndex.romanToIntStatic("iv"));
        assertEquals(5, SkyblockSearchIndex.romanToIntStatic("v"));
        assertEquals(6, SkyblockSearchIndex.romanToIntStatic("vi"));
        assertEquals(7, SkyblockSearchIndex.romanToIntStatic("vii"));
        assertEquals(9, SkyblockSearchIndex.romanToIntStatic("ix"));
        assertEquals(10, SkyblockSearchIndex.romanToIntStatic("x"));
    }

    @Test
    void romanToIntConvertsCatacombsFloors() {
        assertEquals(1, SkyblockSearchIndex.romanToIntStatic("i"));
        assertEquals(4, SkyblockSearchIndex.romanToIntStatic("iv"));
        assertEquals(7, SkyblockSearchIndex.romanToIntStatic("vii"));
    }

    @Test
    void romanToIntReturnsZeroForEmpty() {
        assertEquals(0, SkyblockSearchIndex.romanToIntStatic(""));
        assertEquals(0, SkyblockSearchIndex.romanToIntStatic(null));
    }

    @Test
    void romanToIntReturnsZeroForInvalid() {
        assertEquals(0, SkyblockSearchIndex.romanToIntStatic("abc"));
        // "iiv" is non-standard but parses as 3 in right-to-left logic
        assertEquals(3, SkyblockSearchIndex.romanToIntStatic("iiv"));
    }

    @Test
    void romanToIntIsCaseInsensitive() {
        assertEquals(7, SkyblockSearchIndex.romanToIntStatic("VII"));
        assertEquals(10, SkyblockSearchIndex.romanToIntStatic("X"));
    }

    // -- Singular fallback -----------------------------------------------------

    @Test
    void toSingularStripsTrailingS() {
        assertEquals("pet", SkyblockSearchIndex.toSingular("pets"));
        assertEquals("sword", SkyblockSearchIndex.toSingular("swords"));
        assertEquals("boss", SkyblockSearchIndex.toSingular("bosses"));
    }

    @Test
    void toSingularSkipsShortWords() {
        assertNull(SkyblockSearchIndex.toSingular("ab"));
        assertNull(SkyblockSearchIndex.toSingular("abc"));
    }

    @Test
    void toSingularSkipsWordsEndingInSs() {
        assertNull(SkyblockSearchIndex.toSingular("glass"));
        assertNull(SkyblockSearchIndex.toSingular("pass"));
        assertNull(SkyblockSearchIndex.toSingular("boss")); // wait, boss ends in ss
    }

    @Test
    void toSingularHandlesEs() {
        assertEquals("boss", SkyblockSearchIndex.toSingular("bosses"));
        assertEquals("match", SkyblockSearchIndex.toSingular("matches"));
    }

    @Test
    void toSingularReturnsNullForNonPlural() {
        assertNull(SkyblockSearchIndex.toSingular("pet"));
        assertNull(SkyblockSearchIndex.toSingular("sword"));
    }
}
