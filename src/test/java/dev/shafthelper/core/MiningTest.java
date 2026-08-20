package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.shafthelper.core.Mining.Breakdown;
import dev.shafthelper.core.Mining.ProfitLine;
import dev.shafthelper.core.Mining.Ranked;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiningTest {

    private static Breakdown gem(List<Breakdown> breakdown, String name) {
        return breakdown.stream().filter(entry -> entry.gem().name().equals(name)).findFirst().orElseThrow();
    }

    private static Ranked ranked(List<Ranked> rankedList, String name) {
        return rankedList.stream().filter(entry -> entry.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void breakingTicksFloorsTheRawFormula() {
        assertEquals(69, Mining.breakingTicks(2300, 1000));
        assertEquals(128, Mining.breakingTicks(3000, 700)); // 128.57 -> 128
        assertEquals(468, Mining.breakingTicks(5200, 333)); // 468.46 -> 468
    }

    @Test
    void breakingTicksNeverReturnsLessThanOneTick() {
        assertEquals(1, Mining.breakingTicks(2300, 1_000_000));
    }

    @Test
    void breakingTicksRejectsNonPositiveMiningSpeed() {
        assertThrows(IllegalArgumentException.class, () -> Mining.breakingTicks(2300, 0));
        assertThrows(IllegalArgumentException.class, () -> Mining.breakingTicks(2300, -5));
    }

    @Test
    void dropsPerBlockAveragesAndScalesWithFortune() {
        assertEquals(4, Gemstones.AVG_DROPS);
        assertEquals(4, Mining.dropsPerBlock(0));
        assertEquals(8, Mining.dropsPerBlock(100));
        assertEquals(14, Mining.dropsPerBlock(250));
    }

    @Test
    void pristineChanceConvertsPointsToClampedProbability() {
        assertEquals(0, Mining.pristineChance(0));
        assertEquals(0.015, Mining.pristineChance(1.5));
        assertEquals(1, Mining.pristineChance(250));
        assertEquals(0, Mining.pristineChance(-5));
    }

    @Test
    void calculateBreakdownCoversEveryGemstone() {
        List<Breakdown> breakdown = Mining.calculateBreakdown(1500, 0, 0, 0);
        // Only actual gemstones (12), not shaft types (5)
        assertEquals(12, breakdown.size());

        Breakdown ruby = gem(breakdown, "Ruby");
        assertEquals(46, ruby.ticks());
        assertEquals(2.3, ruby.seconds());
        assertEquals(1565, Math.round(ruby.blocksPerHour()));
        assertEquals(6261, Math.round(ruby.gemsPerHour()));

        assertEquals(96, gem(breakdown, "Jasper").ticks());
    }

    @Test
    void fortuneRaisesGemsPerHourWithoutChangingBreakingTime() {
        List<Breakdown> plain = Mining.calculateBreakdown(1000, 0, 0, 0);
        List<Breakdown> fortuned = Mining.calculateBreakdown(1000, 100, 0, 0);
        Breakdown plainRuby = gem(plain, "Ruby");
        Breakdown fortunedRuby = gem(fortuned, "Ruby");
        assertEquals(plainRuby.ticks(), fortunedRuby.ticks());
        assertEquals(plainRuby.gemsPerHour() * 2, fortunedRuby.gemsPerHour());
    }

    @Test
    void profitLineTurnsPristineIntoAStraightLine() {
        List<Breakdown> breakdown = Mining.calculateBreakdown(1000, 0, 0, 0);
        Breakdown ruby = gem(breakdown, "Ruby");
        Map<String, Double> prices = Map.of("ROUGH_RUBY_GEM", 1.0, "FLAWED_RUBY_GEM", 101.0);
        ProfitLine line = Mining.profitLine(ruby, prices);

        assertEquals(ruby.gemsPerHour(), line.base());
        assertEquals(ruby.gemsPerHour(), line.slope()); // (101 - 1) / 100
        assertEquals(line.base(), Mining.coinsPerHour(ruby, prices, 0));
        assertEquals(ruby.gemsPerHour() * 101, Mining.coinsPerHour(ruby, prices, 100));
        assertEquals(ruby.gemsPerHour() * 2, Mining.coinsPerHour(ruby, prices, 1));
    }

    @Test
    void rankByProfitSplitsDropsBetweenRoughAndFlawedByPristine() {
        List<Breakdown> breakdown = Mining.calculateBreakdown(1000, 0, 0, 0);
        Map<String, Double> prices = Map.of("ROUGH_ONYX_GEM", 1.0, "FLAWED_ONYX_GEM", 80.0);
        List<Ranked> rankedList = Mining.rankByProfit(breakdown, prices, 10);
        Ranked onyx = ranked(rankedList, "Onyx");

        double gemsPerHour = onyx.breakdown().gemsPerHour();
        assertEquals(gemsPerHour * 0.9, onyx.roughPerHour());
        assertEquals(gemsPerHour * 0.1, onyx.flawedPerHour(), 1e-9);
        assertEquals(gemsPerHour * (0.9 * 1 + 0.1 * 80), onyx.coinsPerHour(), 1e-6);
    }

    @Test
    void gemstoneSpreadIncreasesBlocksPerHour() {
        List<Breakdown> noSpread = Mining.calculateBreakdown(1000, 0, 0, 0);
        List<Breakdown> withSpread = Mining.calculateBreakdown(1000, 0, 0, 10);
        
        Breakdown noSpreadRuby = gem(noSpread, "Ruby");
        Breakdown withSpreadRuby = gem(withSpread, "Ruby");
        
        // 10% spread should increase blocks per hour by 10%
        assertEquals(noSpreadRuby.blocksPerHour() * 1.1, withSpreadRuby.blocksPerHour(), 1e-6);
        assertEquals(noSpreadRuby.gemsPerHour() * 1.1, withSpreadRuby.gemsPerHour(), 1e-6);
    }

    @Test
    void gemstoneFortuneAddsToMiningFortune() {
        List<Breakdown> noGemFortune = Mining.calculateBreakdown(1000, 100, 0, 0);
        List<Breakdown> withGemFortune = Mining.calculateBreakdown(1000, 100, 50, 0);
        
        Breakdown noGemRuby = gem(noGemFortune, "Ruby");
        Breakdown withGemRuby = gem(withGemFortune, "Ruby");
        
        // 50 gemstone fortune should add to 100 mining fortune = 150 total
        // Base drops: 4.0 (AVG_DROPS)
        // With 100 fortune: 4.0 * (1 + 100/100) = 8.0
        // With 150 fortune: 4.0 * (1 + 150/100) = 10.0
        // Ratio: 10.0 / 8.0 = 1.25 (25% increase)
        assertEquals(noGemRuby.dropsPerBlock() * 1.25, withGemRuby.dropsPerBlock(), 1e-6);
        assertEquals(noGemRuby.gemsPerHour() * 1.25, withGemRuby.gemsPerHour(), 1e-6);
    }

    @Test
    void rankByProfitSortsByCoinsPerHourAndPricesMissingProductsAtZero() {
        List<Breakdown> breakdown = Mining.calculateBreakdown(1000, 0, 0, 0);
        List<Ranked> rankedList = Mining.rankByProfit(breakdown,
            Map.of("ROUGH_ONYX_GEM", 100.0, "ROUGH_RUBY_GEM", 1.0), 0);

        assertEquals("Onyx", rankedList.getFirst().name());
        assertEquals(0, rankedList.getLast().coinsPerHour());
        assertEquals(0, ranked(rankedList, "Jade").roughPrice());
        
        // Verify that shaft types are not in the breakdown
        boolean hasShaftTypes = rankedList.stream().anyMatch(r -> 
            r.name().equals("Titanium") || r.name().equals("Tungsten") || 
            r.name().equals("Umber") || r.name().equals("Fairy") || r.name().equals("Little"));
        assertEquals(false, hasShaftTypes);
    }
}
