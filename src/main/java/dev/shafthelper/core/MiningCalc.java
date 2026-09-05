package dev.shafthelper.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mineshaft profit / acceptance calculator.
 *
 * Unlike Mining.rankByProfit(), this does NOT simply rank shafts by
 * their theoretical in-shaft coins/hour.
 *
 * It calculates the optimal long-run strategy:
 *
 *   - Enter a shaft
 *   - Decide whether to mine it or leave it
 *   - Leaving costs the shaft spawn time
 *   - Mining gives coins for the amount of time Cold allows
 *
 * The optimal strategy is therefore an acceptance threshold:
 *
 *     MINE if shaftRate >= optimalThreshold
 *     SKIP otherwise
 *
 * This assumes all accepted shafts have the same mining duration,
 * which is currently true because Cold duration depends only on
 * Cold Resistance.
 */
public final class MiningCalc {

    /* =========================
       Mineshaft mechanics
       ========================= */

    /** Average time spent generating/entering another shaft. */
    public static final double SHAFT_SPAWN_MINUTES = 1.5;

    /** Chance for the normal third corpse. */
    public static final double THIRD_CORPSE_CHANCE = 0.50;

    /**
     * Chance for the additional corpse.
     *
     * This is deliberately configurable here rather than hidden in
     * the formula because the exact current in-game behavior has
     * changed over time.
     */
    public static final double EXTRA_CORPSE_CHANCE = 0.45;

    /** Chance that an individual corpse is Lapis. */
    public static final double LAPIS_CORPSE_CHANCE = 0.50;

    /** Number of possible Lapis corpses. */
    public static final int MAX_LAPIS = 4;

    /** Number of shaft types currently represented by Gemstones.ALL. */
    public static final int SHAFT_TYPES = 17;

    private static final double EPSILON = 1e-9;

    /* =========================
       Lapis probability
       ========================= */

    /**
     * Probability of having exactly N Lapis corpses.
     *
     * Shaft corpse count:
     *
     *   2 corpses guaranteed
     *   + third corpse with 50%
     *   + fourth corpse with 45%
     *
     * Each corpse independently has a 50% chance of being Lapis.
     */
    public static double lapisProbability(int lapis) {
        if (lapis < 0 || lapis > MAX_LAPIS) {
            return 0;
        }

        double probability = 0;

        // 2 corpses
        probability +=
            (1 - THIRD_CORPSE_CHANCE) *
            (1 - EXTRA_CORPSE_CHANCE) *
            binomialProbability(2, lapis);

        // 3 corpses
        probability +=
            (
                THIRD_CORPSE_CHANCE * (1 - EXTRA_CORPSE_CHANCE)
                +
                (1 - THIRD_CORPSE_CHANCE) * EXTRA_CORPSE_CHANCE
            ) *
            binomialProbability(3, lapis);

        // 4 corpses
        probability +=
            THIRD_CORPSE_CHANCE *
            EXTRA_CORPSE_CHANCE *
            binomialProbability(4, lapis);

        return probability;
    }

    private static double binomialProbability(int n, int successes) {
        if (successes < 0 || successes > n) {
            return 0;
        }

        return binomialCoefficient(n, successes)
            * Math.pow(LAPIS_CORPSE_CHANCE, successes)
            * Math.pow(1 - LAPIS_CORPSE_CHANCE, n - successes);
    }

    private static int binomialCoefficient(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }

        k = Math.min(k, n - k);

        int result = 1;

        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }

        return result;
    }

    /**
     * Returns the complete Lapis distribution.
     */
    public static Map<Integer, Double> lapisDistribution() {
        Map<Integer, Double> result = new LinkedHashMap<>();

        for (int lapis = 0; lapis <= MAX_LAPIS; lapis++) {
            result.put(lapis, lapisProbability(lapis));
        }

        return result;
    }

    /* =========================
       Shaft state
       ========================= */

    /**
     * One possible shaft state.
     *
     * Example:
     *
     *   Jasper + 3 Lapis
     *
     * is a completely separate state from:
     *
     *   Jasper + 4 Lapis
     */
    public record ShaftState(
        Gemstone gem,
        int lapis,
        double probability,
        double coinsPerHour,
        double miningMinutes
    ) {
        public double effectiveCoinsPerHour(double efficiency) {
            return coinsPerHour * efficiency / 100.0;
        }

        public String label() {
            return gem.name() + " " + lapis + "L";
        }
    }

    /**
     * Result of the optimal stopping calculation.
     */
    public record Strategy(
        double coinsPerHour,
        double threshold,
        double expectedCoinsPerCycle,
        double expectedMinutesPerCycle,
        double acceptedProbability,
        List<ShaftState> acceptedStates,
        List<ShaftState> allStates
    ) {
        public boolean shouldMine(
            Gemstone gem,
            int lapis
        ) {
            return acceptedStates.stream()
                .anyMatch(state ->
                    state.gem().name().equals(gem.name())
                    && state.lapis() == lapis
                );
        }

        public boolean shouldMine(
            Gemstone gem,
            int lapis,
            double rate
        ) {
            return rate + EPSILON >= threshold;
        }
    }

    /**
     * Builds every possible gemstone shaft state.
     *
     * Shaft type probability is currently assumed to be uniform:
     *
     *     1 / 17
     *
     * This is intentionally isolated here so it can later be replaced
     * with the actual shaft-spawn weighting when needed.
     */
    public static List<ShaftState> buildStates(
        Map<String, Double> prices,
        int miningSpeed,
        int miningFortune,
        int gemstoneFortune,
        int gemstoneSpread,
        double pristine,
        double coldResistance
    ) {
        List<Mining.Breakdown> breakdowns =
            Mining.calculateBreakdown(
                miningSpeed,
                miningFortune,
                gemstoneFortune,
                gemstoneSpread
            );

        Map<String, Mining.Breakdown> breakdownMap = new LinkedHashMap<>();

        for (Mining.Breakdown breakdown : breakdowns) {
            breakdownMap.put(
                breakdown.gem().name(),
                breakdown
            );
        }

        double typeProbability =
            1.0 / Gemstones.ALL.size();

        double miningMinutes =
            Cold.shaftSeconds(coldResistance) / 60.0;

        List<ShaftState> states = new ArrayList<>();

        for (Gemstone gem : Gemstones.ALL) {

            /*
             * Non-gemstone shaft types are currently treated as
             * zero profit because Mining.java only models gemstone
             * mining.
             *
             * They remain in the probability space, however,
             * which is important: rejecting them still consumes
             * the shaft spawn roll.
             */
            Mining.Breakdown breakdown =
                breakdownMap.get(gem.name());

            for (int lapis = 0; lapis <= MAX_LAPIS; lapis++) {

                double probability =
                    typeProbability *
                    lapisProbability(lapis);

                double coinsPerHour = 0;

                if (breakdown != null) {
                    coinsPerHour =
                        Mining.coinsPerHour(
                            breakdown,
                            prices,
                            pristine + lapis
                        );
                }

                states.add(
                    new ShaftState(
                        gem,
                        lapis,
                        probability,
                        coinsPerHour,
                        miningMinutes
                    )
                );
            }
        }

        return states;
    }

    /* =========================
       Optimal strategy
       ========================= */

    /**
     * Calculates the maximum long-run coins/hour strategy.
     *
     * For an accepted set A:
     *
     *       Σ[pᵢ × rateᵢ × timeᵢ]
     * R = ------------------------------
     *       spawn + Σ[pᵢ × timeᵢ]
     *
     * Rates are converted to coins/minute internally.
     *
     * We sort all states by rate and test every possible cutoff.
     * Since the shaft duration is identical for all states, the
     * optimal strategy is guaranteed to be a threshold strategy.
     */
    public static Strategy calculate(
        Map<String, Double> prices,
        int miningSpeed,
        int miningFortune,
        int gemstoneFortune,
        int gemstoneSpread,
        double pristine,
        double coldResistance,
        double efficiency
    ) {
        List<ShaftState> states =
            buildStates(
                prices,
                miningSpeed,
                miningFortune,
                gemstoneFortune,
                gemstoneSpread,
                pristine,
                coldResistance
            );

        /*
         * Sort from best to worst.
         *
         * Efficiency is identical for every shaft, so sorting by
         * raw coins/hour gives the exact same ordering.
         */
        List<ShaftState> sorted =
            states.stream()
                .sorted(
                    Comparator
                        .comparingDouble(
                            (ShaftState state) ->
                                state.effectiveCoinsPerHour(efficiency)
                        )
                        .reversed()
                )
                .toList();

        double bestCoinsPerHour = 0;
        double bestCoinsPerCycle = 0;
        double bestMinutesPerCycle = SHAFT_SPAWN_MINUTES;
        double bestAcceptedProbability = 0;
        int bestCount = 0;

        double acceptedProbability = 0;
        double expectedCoinsPerCycle = 0;
        double expectedMiningMinutes = 0;

        /*
         * Test every possible threshold.
         *
         * Prefix 1:
         *   accept only the single best state.
         *
         * Prefix 2:
         *   accept the two best states.
         *
         * ...
         *
         * The best prefix is the optimal strategy.
         */
        for (int i = 0; i < sorted.size(); i++) {

            ShaftState state = sorted.get(i);

            /*
             * States with zero probability don't matter.
             */
            if (state.probability() <= 0) {
                continue;
            }

            double effectiveRate =
                state.effectiveCoinsPerHour(efficiency);

            /*
             * M per minute while actually mining.
             */
            double coinsPerMinute =
                effectiveRate / 60.0;

            acceptedProbability += state.probability();

            expectedCoinsPerCycle +=
                state.probability()
                * coinsPerMinute
                * state.miningMinutes();

            expectedMiningMinutes +=
                state.probability()
                * state.miningMinutes();

            double totalMinutes =
                SHAFT_SPAWN_MINUTES
                + expectedMiningMinutes;

            double totalCoinsPerHour =
                totalMinutes <= 0
                    ? 0
                    : expectedCoinsPerCycle
                        / totalMinutes
                        * 60.0;

            if (totalCoinsPerHour > bestCoinsPerHour + EPSILON) {

                bestCoinsPerHour =
                    totalCoinsPerHour;

                bestCoinsPerCycle =
                    expectedCoinsPerCycle;

                bestMinutesPerCycle =
                    totalMinutes;

                bestAcceptedProbability =
                    acceptedProbability;

                bestCount = i + 1;
            }
        }

        List<ShaftState> accepted =
            sorted.subList(
                0,
                Math.min(bestCount, sorted.size())
            );

        double threshold =
            accepted.isEmpty()
                ? Double.POSITIVE_INFINITY
                : accepted.get(accepted.size() - 1)
                    .effectiveCoinsPerHour(efficiency);

        return new Strategy(
            bestCoinsPerHour,
            threshold,
            bestCoinsPerCycle,
            bestMinutesPerCycle,
            bestAcceptedProbability,
            List.copyOf(accepted),
            List.copyOf(states)
        );
    }

    /* =========================
       Convenience helpers
       ========================= */

    /**
     * Returns the theoretical raw coins/hour for one exact shaft.
     */
    public static double rateFor(
        Gemstone gem,
        int lapis,
        Map<String, Double> prices,
        int miningSpeed,
        int miningFortune,
        int gemstoneFortune,
        int gemstoneSpread,
        double pristine
    ) {
        List<Mining.Breakdown> breakdown =
            Mining.calculateBreakdown(
                miningSpeed,
                miningFortune,
                gemstoneFortune,
                gemstoneSpread
            );

        Mining.Breakdown target =
            breakdown.stream()
                .filter(b ->
                    b.gem().name().equals(gem.name()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return 0;
        }

        return Mining.coinsPerHour(
            target,
            prices,
            pristine + lapis
        );
    }

    /**
     * Groups accepted states by gemstone.
     *
     * This makes the HUD much easier to read:
     *
     *   Jasper 0-4L
     *   Opal 3-4L
     *   Amethyst 4L
     */
    public record AcceptanceGroup(
        Gemstone gem,
        int minimumLapis,
        int maximumLapis
    ) {}

    public static List<AcceptanceGroup> acceptanceGroups(
        Strategy strategy
    ) {
        Map<Gemstone, List<Integer>> grouped =
            new LinkedHashMap<>();

        for (ShaftState state : strategy.acceptedStates()) {
            grouped
                .computeIfAbsent(
                    state.gem(),
                    ignored -> new ArrayList<>()
                )
                .add(state.lapis());
        }

        List<AcceptanceGroup> result =
            new ArrayList<>();

        for (Map.Entry<Gemstone, List<Integer>> entry
                : grouped.entrySet()) {

            List<Integer> lapis =
                entry.getValue();

            int min =
                lapis.stream()
                    .min(Integer::compareTo)
                    .orElse(0);

            int max =
                lapis.stream()
                    .max(Integer::compareTo)
                    .orElse(0);

            result.add(
                new AcceptanceGroup(
                    entry.getKey(),
                    min,
                    max
                )
            );
        }

        return result;
    }

    private MiningCalc() {}
}