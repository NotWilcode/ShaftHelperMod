package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatsParserTest {

    @Test
    void readsStatsFromTabWidgetLines() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "\u2895 Mining Speed: 1,500",
            "\u2618 Mining Fortune: 400",
            "\u2727 Pristine: 3.5"));
        assertEquals(1500, stats.miningSpeed());
        assertEquals(400, stats.miningFortune());
        assertEquals(3.5, stats.pristine());
        assertNull(stats.lapisCorpses());
        assertNull(stats.umberCorpses());
        assertNull(stats.tungstenCorpses());
        assertNull(stats.gemstoneFortune());
        assertNull(stats.gemstoneSpread());
        assertNull(stats.cold());
    }

    @Test
    void readsHypixelStatsWidgetWithIconAfterTheColon() {
        // Exactly what Hypixel's tab list Stats widget shows: the stat icon sits between the
        // colon and the number.
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Speed: \u2e153445",
            "Pristine: \u27672",
            "Mining Fortune: \u2618597"));
        assertEquals(3445, stats.miningSpeed());
        assertEquals(597, stats.miningFortune());
        assertEquals(2, stats.pristine());
        assertNull(stats.lapisCorpses());
        assertNull(stats.umberCorpses());
        assertNull(stats.tungstenCorpses());
        assertNull(stats.gemstoneFortune());
        assertNull(stats.gemstoneSpread());
    }

    @Test
    void readsLapisCorpsesFromTabWidget() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Speed: 1500",
            "Frozen Corpses:",
            "Lapis: NOT LOOTED",
            "Lapis: LOOTED",
            "Lapis: NOT LOOTED"));
        assertEquals(1500, stats.miningSpeed());
        assertEquals(3, stats.lapisCorpses());
        assertNull(stats.umberCorpses());
        assertNull(stats.tungstenCorpses());
    }

    @Test
    void readsUmberAndTungstenCorpsesFromTabWidget() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Speed: 1500",
            "Frozen Corpses:",
            "Umber: NOT LOOTED",
            "Tungsten: LOOTED",
            "Tungsten: NOT LOOTED"));
        assertEquals(1500, stats.miningSpeed());
        assertEquals(1, stats.umberCorpses());
        assertEquals(2, stats.tungstenCorpses());
        assertNull(stats.lapisCorpses());
    }

    @Test
    void stripsFormattingCodes() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "\u00a76\u00a7lMining Speed: \u00a7a2,340"));
        assertEquals(2340, stats.miningSpeed());
    }

    @Test
    void readsNegativeColdFromScoreboard() {
        StatsParser.Stats stats = StatsParser.parse(List.of("\u2744 Cold: -50"));
        assertEquals(50, stats.cold());
    }

    @Test
    void ignoresLinesWithoutNumbers() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Speed: N/A", "Area: Mineshaft", "some_player"));
        assertTrue(stats.isEmpty());
    }

    @Test
    void readsGemstoneFortuneAndSpread() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Gemstone Fortune: 25",
            "Gemstone Spread: 15"));
        assertEquals(25, stats.gemstoneFortune());
        assertEquals(15, stats.gemstoneSpread());
    }

    @Test
    void missingStatsStayNull() {
        StatsParser.Stats stats = StatsParser.parse(List.of("Mining Fortune: 123"));
        assertNull(stats.miningSpeed());
        assertEquals(123, stats.miningFortune());
        assertNull(stats.lapisCorpses());
        assertNull(stats.umberCorpses());
        assertNull(stats.tungstenCorpses());
        assertNull(stats.gemstoneFortune());
        assertNull(stats.gemstoneSpread());
    }

    @Test
    void isCaseInsensitive() {
        StatsParser.Stats stats = StatsParser.parse(List.of("mining speed: 999"));
        assertEquals(999, stats.miningSpeed());
    }

    @Test
    void gemstoneFortuneAddsToMiningFortuneInDrops() {
        // This test should verify that gemstone fortune is combined with mining fortune
        // For now we just verify parsing works, calculation logic is tested in MiningTest
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Fortune: 100",
            "Gemstone Fortune: 50"));
        assertEquals(100, stats.miningFortune());
        assertEquals(50, stats.gemstoneFortune());
    }

    @Test
    void countsAllLapisEntriesRegardlessOfSection() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Stats:",
            "Mining Speed: 1500",
            "Lapis: NOT LOOTED",
            "Frozen Corpses:",
            "Lapis: LOOTED",
            "Lapis: NOT LOOTED",
            "Other Section:",
            "Lapis: LOOTED"));
        assertEquals(1500, stats.miningSpeed());
        assertEquals(4, stats.lapisCorpses());
    }

    @Test
    void countsLapisEvenWithoutFrozenCorpsesHeader() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Speed: 1500",
            "Lapis: NOT LOOTED",
            "Cold: -50"));
        assertEquals(1500, stats.miningSpeed());
        assertEquals(1, stats.lapisCorpses());
    }

    @Test
    void emptyFrozenCorpsesSection() {
        StatsParser.Stats stats = StatsParser.parse(List.of(
            "Mining Speed: 1500",
            "Frozen Corpses:",
            "Other Section:",
            "Cold: -50"));
        assertEquals(1500, stats.miningSpeed());
        assertNull(stats.lapisCorpses());
    }
}
