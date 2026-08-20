package dev.shafthelper.core;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class Mining {

    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_HOUR = TICKS_PER_SECOND * 3600;

    /** Ticks needed to break a block: floor(strength * 30 / miningSpeed), never below 1. */
    public static int breakingTicks(int strength, double miningSpeed) {
        if (!Double.isFinite(miningSpeed) || miningSpeed <= 0) {
            throw new IllegalArgumentException("miningSpeed must be a positive number");
        }
        return Math.max(1, (int) Math.floor(strength * 30 / miningSpeed));
    }

    /** Average gems per block: 3-5 base, multiplied by Mining Fortune + Gemstone Fortune. */
    public static double dropsPerBlock(double fortune, double gemstoneFortune) {
        double totalFortune = fortune + gemstoneFortune;
        return Gemstones.AVG_DROPS * (1 + totalFortune / 100);
    }

    /** Backward compatibility: dropsPerBlock without gemstone fortune */
    public static double dropsPerBlock(double fortune) {
        return dropsPerBlock(fortune, 0);
    }

    /** Each point of Pristine is a 1% chance for a block's drops to come out flawed instead of rough. */
    public static double pristineChance(double pristine) {
        return Math.min(Math.max(pristine, 0), 100) / 100;
    }

    /** Breaking time and theoretical throughput for one gemstone at a given mining speed. */
    public record Breakdown(Gemstone gem, int ticks, double seconds, double blocksPerHour,
                            double dropsPerBlock, double gemsPerHour) {}

    /**
     * Breaking time and theoretical throughput for every gemstone at a given mining speed.
     * Throughput assumes non-stop mining, so it is an upper bound.
     */
    public static List<Breakdown> calculateBreakdown(double miningSpeed, double fortune, double gemstoneFortune, double gemstoneSpread) {
        double drops = dropsPerBlock(fortune, gemstoneFortune);
        double spreadMultiplier = 1 + (gemstoneSpread / 100.0); // 10% spread = 1.1x blocks
        return Gemstones.ALL.stream()
            .filter(gem -> gem.isGemstone()) // Only calculate for actual gemstones
            .map(gem -> {
                int ticks = breakingTicks(gem.strength(), miningSpeed);
                double blocksPerHour = (double) TICKS_PER_HOUR / ticks * spreadMultiplier;
                return new Breakdown(gem, ticks, (double) ticks / TICKS_PER_SECOND, blocksPerHour,
                    drops, blocksPerHour * drops);
            }).toList();
    }

    /** Backward compatibility: calculateBreakdown without gemstone stats */
    public static List<Breakdown> calculateBreakdown(double miningSpeed, double fortune) {
        return calculateBreakdown(miningSpeed, fortune, 0, 0);
    }

    /**
     * Coins per hour is linear in Pristine:
     *   coins/hr(P) = gems/hr x [ (1 - P/100)*roughPrice + (P/100)*flawedPrice ]
     *               = base + slope x P
     * so gemstone comparisons reduce to intersecting straight lines.
     */
    public record ProfitLine(double rough, double flawed, double base, double slope) {}

    public static ProfitLine profitLine(Breakdown gem, Map<String, Double> prices) {
        double rough = prices.getOrDefault(gem.gem().roughId(), 0.0);
        double flawed = prices.getOrDefault(gem.gem().flawedId(), 0.0);
        return new ProfitLine(rough, flawed, gem.gemsPerHour() * rough,
            gem.gemsPerHour() * (flawed - rough) / 100);
    }

    public static double coinsPerHour(Breakdown gem, Map<String, Double> prices, double pristine) {
        ProfitLine line = profitLine(gem, prices);
        return line.base() + line.slope() * (pristineChance(pristine) * 100);
    }

    public record Ranked(Breakdown breakdown, double roughPrice, double flawedPrice,
                         double roughPerHour, double flawedPerHour, double coinsPerHour) {
        public Gemstone gem() { return breakdown.gem(); }
        public String name() { return breakdown.gem().name(); }
        public int ticks() { return breakdown.ticks(); }
    }

    /** Ranks gemstones by coins per hour at a given Pristine level. */
    public static List<Ranked> rankByProfit(List<Breakdown> breakdown, Map<String, Double> prices, double pristine) {
        double chance = pristineChance(pristine);
        return breakdown.stream()
            .map(gem -> {
                ProfitLine line = profitLine(gem, prices);
                return new Ranked(gem, line.rough(), line.flawed(),
                    gem.gemsPerHour() * (1 - chance), gem.gemsPerHour() * chance,
                    line.base() + line.slope() * chance * 100);
            })
            .sorted(Comparator.comparingDouble(Ranked::coinsPerHour).reversed()
                .thenComparingInt(Ranked::ticks))
            .toList();
    }

    private Mining() {}
}
