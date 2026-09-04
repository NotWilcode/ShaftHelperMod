package dev.shafthelper.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Prices {

    /** Which side of the Flawless order book to read. */
    public enum Mode { SELL_OFFER, INSTA_SELL }

    /** Averaged prices are the default because a day of manipulation barely moves them. */
    public enum Data { AVERAGE, LIVE }

    /** Flawless is where the demand is, so it is the steadiest price to value a drop from. */
    public enum Basis { FLAWLESS, LISTED }

    public static final Mode DEFAULT_MODE = Mode.SELL_OFFER;
    public static final Data DEFAULT_DATA = Data.AVERAGE;
    public static final Basis DEFAULT_BASIS = Basis.FLAWLESS;

    /** A drop listed a little above its Flawless value is normal; double it is worth pointing out. */
    private static final double INFLATED_LISTING = 2;

    public static final List<String> GEM_PRODUCT_IDS = Gemstones.ALL.stream()
        .flatMap(gem -> List.of(gem.roughId(), gem.flawedId(), gem.flawlessId()).stream())
        .toList();
    public static final List<String> ALL_PRODUCT_IDS =  
        java.util.stream.Stream.concat(  
            GEM_PRODUCT_IDS.stream(),  
            DropTracker.PRODUCT_IDS.stream()  
        ).distinct().toList();

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public record Adjustment(Gemstone gem, String id, double live, double used) {}

    public record Valued(Map<String, Double> prices, List<Adjustment> adjustments) {}

    /** Prices is null when no price source is reachable. */
    public record Result(Map<String, Double> prices, List<Adjustment> adjustments, Long lastUpdated) {}

    /**
     * Values every drop through the flawless tier it crafts into: 80 rough make a flawed, 80 flawed
     * a fine and 80 fine a flawless, so a rough is flawless / 80^3 and a flawed is flawless / 80^2.
     * Flawless is what players actually buy, which keeps it far harder to move than the rough listing.
     */
    public static Valued valueFromFlawless(Map<String, Double> prices) {
        Map<String, Double> valued = new HashMap<>(prices);
        List<Adjustment> adjustments = new ArrayList<>();

        for (Gemstone gem : Gemstones.ALL) {
            Double flawless = prices.get(gem.flawlessId());
            if (flawless == null || !Double.isFinite(flawless) || flawless <= 0) continue;

            Map<String, Integer> ratios = Map.of(
                gem.roughId(), Gemstones.ROUGH_PER_FLAWLESS,
                gem.flawedId(), Gemstones.FLAWED_PER_FLAWLESS
            );
            for (String id : List.of(gem.roughId(), gem.flawedId())) {
                Double listed = prices.get(id);
                double value = flawless / ratios.get(id);
                valued.put(id, value);
                if (listed != null && Double.isFinite(listed) && listed >= value * INFLATED_LISTING) {
                    adjustments.add(new Adjustment(gem, id, listed, value));
                }
            }
        }

        return new Valued(valued, adjustments);
    }

    /**
     * Live rough + flawed gemstone unit prices, or a null price map when no price source is
     * reachable. AVERAGE uses the last few days of history instead of the current snapshot.
     */
    public static CompletableFuture<Result> load(Mode mode, Data data, Basis basis, HttpFetcher fetcher) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                Map<String, Double> listed;
                Long lastUpdated;
                if (data == Data.AVERAGE) {
                    Map<String, Double> live;
                    try {
                        live = livePrices(mode, fetcher, now).prices();
                    } catch (Exception error) {
                        live = new HashMap<>();
                    }
                    History.Averages averages = History.fetchAverages(ALL_PRODUCT_IDS, fetcher, now, 1_000);
                    // Averages win; the live snapshot fills in any product the history API could not serve.
                    Map<String, Double> merged = new HashMap<>(live);
                    Bazaar.extractPrices(averages.products(), ALL_PRODUCT_IDS, mode).forEach((id, price) -> {
                        if (Double.isFinite(price) && price > 0) merged.put(id, price);
                    });
                    listed = merged;
                    lastUpdated = averages.lastUpdated();
                } else {
                    LivePrices live = livePrices(mode, fetcher, now);
                    listed = live.prices();
                    lastUpdated = live.lastUpdated();
                }

                if (basis == Basis.FLAWLESS) {
                    Valued valued = valueFromFlawless(listed);
                    return new Result(valued.prices(), valued.adjustments(), lastUpdated);
                }
                return new Result(listed, List.of(), lastUpdated);
            } catch (Exception error) {
                System.err.println("Price lookup failed: " + error);
                return new Result(null, List.of(), null);
            }
        }, EXECUTOR);
    }

    private record LivePrices(Map<String, Double> prices, long lastUpdated) {}

    private static LivePrices livePrices(Mode mode, HttpFetcher fetcher, long now) throws Exception {
        Bazaar.Snapshot snapshot = Bazaar.fetch(fetcher, now);
        return new LivePrices(Bazaar.extractPrices(snapshot.products(), ALL_PRODUCT_IDS, mode),
            snapshot.lastUpdated());
    }

    public static String modeLabel(Mode mode) {
        return mode == Mode.INSTA_SELL ? "instant sell" : "sell offer";
    }

    public static String dataLabel(Data data) {
        return data == Data.LIVE ? "live" : History.AVERAGE_DAYS + "-day average";
    }

    public static String basisLabel(Basis basis) {
        return basis == Basis.LISTED ? "rough/flawed listings" : "Flawless / " + Gemstones.TIER_RATIO + "^3";
    }

    private Prices() {}
}
