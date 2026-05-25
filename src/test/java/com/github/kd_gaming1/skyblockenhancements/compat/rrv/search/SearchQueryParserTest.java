package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SearchQueryParser}.
 * Covers keyword splitting, stat threshold parsing, and the new filter prefix syntax.
 */
class SearchQueryParserTest {

    // -- Keywords --------------------------------------------------------------

    @Test
    void emptyQueryReturnsEmpty() {
        SearchQuery q = SearchQueryParser.parse("");
        assertTrue(q.isEmpty());
        assertTrue(q.keywords().isEmpty());
        assertTrue(q.stats().isEmpty());
        assertTrue(q.filters().isEmpty());
    }

    @Test
    void blankQueryReturnsEmpty() {
        SearchQuery q = SearchQueryParser.parse("   ");
        assertTrue(q.isEmpty());
    }

    @Test
    void simpleKeywordsAreSplit() {
        SearchQuery q = SearchQueryParser.parse("farming pets");
        assertEquals(2, q.keywords().size());
        assertEquals("farming", q.keywords().get(0).token());
        assertEquals("pets", q.keywords().get(1).token());
    }

    @Test
    void nonAlphanumericSplitsIntoKeywords() {
        SearchQuery q = SearchQueryParser.parse("hello-world");
        assertEquals(2, q.keywords().size());
        assertEquals("hello", q.keywords().get(0).token());
        assertEquals("world", q.keywords().get(1).token());
    }

    // -- Stats -----------------------------------------------------------------

    @Test
    void statGreaterThanParsed() {
        SearchQuery q = SearchQueryParser.parse("mining_speed>50");
        assertTrue(q.keywords().isEmpty());
        assertEquals(1, q.stats().size());
        assertEquals("mining_speed", q.stats().get(0).statName());
        assertEquals(SearchQuery.StatClause.Operator.GT, q.stats().get(0).op());
        assertEquals(50, q.stats().get(0).value());
    }

    @Test
    void statLessThanOrEqualParsed() {
        SearchQuery q = SearchQueryParser.parse("health<=100");
        assertEquals(1, q.stats().size());
        assertEquals(SearchQuery.StatClause.Operator.LTE, q.stats().get(0).op());
        assertEquals(100, q.stats().get(0).value());
    }

    @Test
    void mixedKeywordsAndStats() {
        SearchQuery q = SearchQueryParser.parse("mining_speed>50 fleet");
        assertEquals(1, q.stats().size());
        assertEquals(1, q.keywords().size());
        assertEquals("fleet", q.keywords().get(0).token());
    }

    // -- Filters: rarity -------------------------------------------------------

    @Test
    void rarityFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("rarity:legendary");
        assertEquals(1, q.filters().size());
        SearchQuery.FilterClause f = q.filters().get(0);
        assertEquals("rarity", f.key());
        assertEquals("legendary", f.stringValue());
        assertEquals(SearchQuery.FilterClause.Operator.EQ, f.op());
    }

    @Test
    void rarityAliasFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("r:mythic");
        assertEquals(1, q.filters().size());
        assertEquals("rarity", q.filters().get(0).key());
        assertEquals("mythic", q.filters().get(0).stringValue());
    }

    // -- Filters: type ---------------------------------------------------------

    @Test
    void typeFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("type:sword");
        assertEquals(1, q.filters().size());
        assertEquals("type", q.filters().get(0).key());
        assertEquals("sword", q.filters().get(0).stringValue());
    }

    // -- Filters: slayer -------------------------------------------------------

    @Test
    void slayerTypeFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("slayer:zombie");
        assertEquals(1, q.filters().size());
        assertEquals("slayer", q.filters().get(0).key());
        assertEquals("zombie", q.filters().get(0).stringValue());
        assertEquals(0, q.filters().get(0).intValue());
    }

    @Test
    void slayerTypeAndLevelFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("slayer:zombie>3");
        assertEquals(1, q.filters().size());
        SearchQuery.FilterClause f = q.filters().get(0);
        assertEquals("slayer", f.key());
        assertEquals("zombie", f.stringValue());
        assertEquals(SearchQuery.FilterClause.Operator.GT, f.op());
        assertEquals(3, f.intValue());
    }

    @Test
    void slayerLevelOnlyFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("slayer>5");
        assertEquals(1, q.filters().size());
        SearchQuery.FilterClause f = q.filters().get(0);
        assertEquals("slayer", f.key());
        assertNull(f.stringValue());
        assertEquals(SearchQuery.FilterClause.Operator.GT, f.op());
        assertEquals(5, f.intValue());
    }

    // -- Filters: skill --------------------------------------------------------

    @Test
    void skillFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("skill:combat>20");
        assertEquals(1, q.filters().size());
        SearchQuery.FilterClause f = q.filters().get(0);
        assertEquals("skill", f.key());
        assertEquals("combat", f.stringValue());
        assertEquals(SearchQuery.FilterClause.Operator.GT, f.op());
        assertEquals(20, f.intValue());
    }

    // -- Filters: catacombs ----------------------------------------------------

    @Test
    void catacombsFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("catacombs>10");
        assertEquals(1, q.filters().size());
        SearchQuery.FilterClause f = q.filters().get(0);
        assertEquals("catacombs", f.key());
        assertNull(f.stringValue());
        assertEquals(SearchQuery.FilterClause.Operator.GT, f.op());
        assertEquals(10, f.intValue());
    }

    @Test
    void catacombsAliasFilterParsed() {
        SearchQuery q = SearchQueryParser.parse("cata>=15");
        assertEquals(1, q.filters().size());
        assertEquals("catacombs", q.filters().get(0).key());
        assertEquals(SearchQuery.FilterClause.Operator.GTE, q.filters().get(0).op());
        assertEquals(15, q.filters().get(0).intValue());
    }

    // -- Filters: boolean flags ------------------------------------------------

    @Test
    void soulboundFlagParsed() {
        SearchQuery q = SearchQueryParser.parse("soulbound");
        assertEquals(1, q.filters().size());
        assertEquals("soulbound", q.filters().get(0).key());
        assertNull(q.filters().get(0).stringValue());
        assertEquals(SearchQuery.FilterClause.Operator.EQ, q.filters().get(0).op());
    }

    @Test
    void dungeonFlagParsed() {
        SearchQuery q = SearchQueryParser.parse("dungeon");
        assertEquals(1, q.filters().size());
        assertEquals("dungeon", q.filters().get(0).key());
    }

    // -- Mixed queries ---------------------------------------------------------

    @Test
    void mixedKeywordsStatsAndFilters() {
        SearchQuery q = SearchQueryParser.parse("fleet mining_speed>50 rarity:legendary");
        assertEquals(1, q.keywords().size());
        assertEquals(1, q.stats().size());
        assertEquals(1, q.filters().size());
        assertEquals("fleet", q.keywords().get(0).token());
        assertEquals("mining_speed", q.stats().get(0).statName());
        assertEquals("rarity", q.filters().get(0).key());
    }

    @Test
    void unknownTokenNotParsedAsFilter() {
        SearchQuery q = SearchQueryParser.parse("foobar:baz");
        // "foobar" is not a known filter key, so it becomes a keyword
        assertTrue(q.filters().isEmpty());
        assertFalse(q.keywords().isEmpty());
    }

    @Test
    void statWithDotNotParsedAsFilter() {
        SearchQuery q = SearchQueryParser.parse("attack_speed>50");
        assertTrue(q.filters().isEmpty());
        assertEquals(1, q.stats().size());
        assertEquals("attack_speed", q.stats().get(0).statName());
    }

    // -- Stat aliases ----------------------------------------------------------

    @Test
    void miningSpeedAliasParsed() {
        SearchQuery q = SearchQueryParser.parse("ms>50");
        assertEquals(1, q.stats().size());
        assertEquals("mining_speed", q.stats().get(0).statName());
        assertEquals(SearchQuery.StatClause.Operator.GT, q.stats().get(0).op());
        assertEquals(50, q.stats().get(0).value());
    }

    @Test
    void criticalChanceAliasParsed() {
        SearchQuery q = SearchQueryParser.parse("cc>=10");
        assertEquals(1, q.stats().size());
        assertEquals("critical_chance", q.stats().get(0).statName());
    }

    @Test
    void walkSpeedAliasParsed() {
        SearchQuery q = SearchQueryParser.parse("speed=50");
        assertEquals(1, q.stats().size());
        assertEquals("walk_speed", q.stats().get(0).statName());
    }

    @Test
    void unknownStatNamePreserved() {
        SearchQuery q = SearchQueryParser.parse("foobar>10");
        assertEquals(1, q.stats().size());
        assertEquals("foobar", q.stats().get(0).statName());
    }
}
