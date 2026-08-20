package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.shafthelper.core.Mining.Breakdown;
import dev.shafthelper.core.Mining.ProfitLine;
import dev.shafthelper.core.Pristine.Comparison;
import dev.shafthelper.core.Pristine.CorpseRow;
import dev.shafthelper.core.Pristine.Status;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PristineTest {

    private static final List<Breakdown> BREAKDOWN = Mining.calculateBreakdown(1000, 0, 0, 0);

    // Jasper wins on rough price; Sapphire is faster to break and worth much more once drops come out flawed.
    private static final Map<String, Double> PRICES = Map.of(
        "ROUGH_JASPER_GEM", 8.0,
        "FLAWED_JASPER_GEM", 8.0 * 80,
        "ROUGH_SAPPHIRE_GEM", 1.0,
        "FLAWED_SAPPHIRE_GEM", 500.0,
        "ROUGH_RUBY_GEM", 0.5,
        "FLAWED_RUBY_GEM", 0.5 * 80,
        "ROUGH_JADE_GEM", 2.0,
        "FLAWED_JADE_GEM", 2.0
    );

    private static Breakdown gem(String name) {
        return BREAKDOWN.stream().filter(entry -> entry.gem().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void bonusClosesExactlyTheGapToTheBenchmarkAtZeroPristine() {
        double bonus = Pristine.bonusPristineToBeat(gem("Sapphire"), gem("Jasper"), PRICES, 0);
        assertTrue(bonus > 0);
        assertEquals(Mining.coinsPerHour(gem("Jasper"), PRICES, 0),
            Mining.coinsPerHour(gem("Sapphire"), PRICES, bonus), 1e-6);
    }

    @Test
    void bonusIsZeroWhenTheGemstoneAlreadyEarnsMore() {
        assertEquals(0, Pristine.bonusPristineToBeat(gem("Jasper"), gem("Ruby"), PRICES, 0));
    }

    @Test
    void bonusIsInfiniteWhenFlawedDropsAreWorthNoMoreThanRough() {
        assertEquals(Double.POSITIVE_INFINITY,
            Pristine.bonusPristineToBeat(gem("Jade"), gem("Jasper"), PRICES, 0));
    }

    @Test
    void basePristineAppliesToBothShaftsSoItShiftsTheBonusNeeded() {
        double atZero = Pristine.bonusPristineToBeat(gem("Sapphire"), gem("Jasper"), PRICES, 0);
        double atOne = Pristine.bonusPristineToBeat(gem("Sapphire"), gem("Jasper"), PRICES, 1);

        // Sapphire gains more per Pristine than Jasper, so shared base Pristine closes part of the gap.
        assertTrue(atOne > 0 && atOne < atZero);
        ProfitLine jasperLine = Mining.profitLine(gem("Jasper"), PRICES);
        ProfitLine sapphireLine = Mining.profitLine(gem("Sapphire"), PRICES);
        double sapphireAt = sapphireLine.base() + sapphireLine.slope() * (1 + atOne);
        assertEquals(jasperLine.base() + jasperLine.slope() * 1, sapphireAt, 1e-6);
    }

    @Test
    void compareToReferenceRoundsTheBonusUpToWholeLapisCorpses() {
        Comparison sapphire = Pristine.compareToReference(gem("Sapphire"), gem("Jasper"), PRICES,
            0, Pristine.MAX_LAPIS_CORPSES);

        assertEquals(Status.REACHABLE, sapphire.status());
        assertEquals(Math.ceil(sapphire.bonus()), sapphire.corpses());
        assertTrue(sapphire.corpses() <= Pristine.MAX_LAPIS_CORPSES);
    }

    @Test
    void compareToReferenceLabelsTheBenchmarkFreeWinsAndDeadEnds() {
        assertEquals(Status.REFERENCE, Pristine.compareToReference(gem("Jasper"), gem("Jasper"),
            PRICES, 0, Pristine.MAX_LAPIS_CORPSES).status());

        Comparison free = Pristine.compareToReference(gem("Jasper"), gem("Ruby"), PRICES,
            0, Pristine.MAX_LAPIS_CORPSES);
        assertEquals(Status.AHEAD, free.status());
        assertEquals(0, free.corpses());

        Comparison dead = Pristine.compareToReference(gem("Jade"), gem("Jasper"), PRICES,
            0, Pristine.MAX_LAPIS_CORPSES);
        assertEquals(Status.IMPOSSIBLE, dead.status());
    }

    @Test
    void compareToReferenceFlagsGemstonesThatNeedMoreCorpsesThanAShaftCanHold() {
        Map<String, Double> expensive = new HashMap<>(PRICES);
        expensive.put("ROUGH_ONYX_GEM", 0.1);
        expensive.put("FLAWED_ONYX_GEM", 30.0);
        Comparison onyx = Pristine.compareToReference(gem("Onyx"), gem("Jasper"), expensive,
            0, Pristine.MAX_LAPIS_CORPSES);

        assertEquals(Status.OUT_OF_REACH, onyx.status());
        assertTrue(onyx.corpses() > Pristine.MAX_LAPIS_CORPSES);
    }

    @Test
    void aLowerMaxCorpsesMakesAReachableGemstoneOutOfReach() {
        Comparison sapphire = Pristine.compareToReference(gem("Sapphire"), gem("Jasper"), PRICES, 0, 0);
        assertEquals(Status.OUT_OF_REACH, sapphire.status());
    }

    @Test
    void corpseTableKeepsOneRowPerGemstoneWithASingleBenchmark() {
        var ranked = Mining.rankByProfit(BREAKDOWN, PRICES, 0);
        var jasper = ranked.stream().filter(gem -> gem.name().equals("Jasper")).findFirst().orElseThrow();
        List<CorpseRow> rows = Pristine.corpseTable(ranked, PRICES, jasper, 0, Pristine.MAX_LAPIS_CORPSES);
        assertEquals(BREAKDOWN.size(), rows.size());
        assertEquals(1, rows.stream().filter(row -> row.status() == Status.REFERENCE).count());
    }
}
