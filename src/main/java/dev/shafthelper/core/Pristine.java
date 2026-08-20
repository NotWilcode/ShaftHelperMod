package dev.shafthelper.core;

import dev.shafthelper.core.Mining.Breakdown;
import dev.shafthelper.core.Mining.ProfitLine;
import dev.shafthelper.core.Mining.Ranked;
import java.util.List;
import java.util.Map;

public final class Pristine {

    /** A shaft spawns 2-4 corpses; each lapis corpse is worth +1 Pristine inside that shaft. */
    public static final int MAX_LAPIS_CORPSES = 4;
    private static final double EPSILON = 1e-9;

    private static double valueAt(ProfitLine line, double pristine) {
        return line.base() + line.slope() * pristine;
    }

    /**
     * Extra Pristine a gemstone shaft needs to out-earn the benchmark shaft.
     * The benchmark never gets the bonus (a Jasper shaft is mined as-is), while the challenger
     * runs at {@code basePristine + bonus}, so:
     *
     *   base_gem + slope_gem x (basePristine + bonus) = base_ref + slope_ref x basePristine
     */
    public static double bonusPristineToBeat(Breakdown gem, Breakdown reference,
                                             Map<String, Double> prices, double basePristine) {
        ProfitLine line = Mining.profitLine(gem, prices);
        ProfitLine referenceLine = Mining.profitLine(reference, prices);
        double shortfall = valueAt(referenceLine, basePristine) - valueAt(line, basePristine);

        if (shortfall <= EPSILON) return 0;
        if (line.slope() <= EPSILON) return Double.POSITIVE_INFINITY;
        return shortfall / line.slope();
    }

    public enum Status { REFERENCE, AHEAD, REACHABLE, OUT_OF_REACH, IMPOSSIBLE }

    public record Comparison(Status status, double bonus, int corpses) {}

    /**
     * How a gemstone shaft compares to the benchmark shaft.
     * REFERENCE    - the benchmark itself
     * AHEAD        - already out-earns it with no lapis corpses
     * REACHABLE    - {@code corpses} lapis corpses (&lt;= max) make it better
     * OUT_OF_REACH - needs more Pristine than the corpses a shaft can hold can give
     * IMPOSSIBLE   - flawed drops are worth no more than rough, so Pristine cannot help
     */
    public static Comparison compareToReference(Breakdown gem, Breakdown reference,
                                                Map<String, Double> prices,
                                                double basePristine, int maxCorpses) {
        if (gem.gem().name().equals(reference.gem().name())) {
            return new Comparison(Status.REFERENCE, 0, 0);
        }

        double bonus = bonusPristineToBeat(gem, reference, prices, basePristine);
        if (bonus == 0) return new Comparison(Status.AHEAD, 0, 0);
        if (!Double.isFinite(bonus)) return new Comparison(Status.IMPOSSIBLE, bonus, Integer.MAX_VALUE);

        int corpses = (int) Math.ceil(bonus - EPSILON);
        return new Comparison(corpses <= maxCorpses ? Status.REACHABLE : Status.OUT_OF_REACH, bonus, corpses);
    }

    public record CorpseRow(Ranked ranked, Comparison comparison,
                            double pristineWithCorpses, double coinsWithCorpses) {
        public Status status() { return comparison.status(); }
        public double bonus() { return comparison.bonus(); }
        public int corpses() { return comparison.corpses(); }
        public String name() { return ranked.name(); }
    }

    /**
     * Bonus Pristine needed by every gemstone to beat the benchmark shaft, plus the coins/hr each
     * shaft makes once the corpses it needs are in it (capped at what a shaft can actually hold).
     */
    public static List<CorpseRow> corpseTable(List<Ranked> ranked, Map<String, Double> prices,
                                              Ranked reference, double basePristine, int maxCorpses) {
        return ranked.stream().map(gem -> {
            Comparison comparison = compareToReference(gem.breakdown(), reference.breakdown(),
                prices, basePristine, maxCorpses);
            int corpses = Math.min(comparison.corpses(), maxCorpses);
            double pristineWithCorpses = basePristine + corpses;
            return new CorpseRow(gem, comparison, pristineWithCorpses,
                Mining.coinsPerHour(gem.breakdown(), prices, pristineWithCorpses));
        }).toList();
    }

    private Pristine() {}
}
