package dev.shafthelper.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The shafts visited this session, in order, for the HUD: shaft 1, shaft 2: 1 lapis, ...
 * A new entry is added when the detected shaft code changes. Each entry tracks the lapis corpses
 * present in the shaft at the time of entry.
 */
public final class ShaftLog {

    public record Entry(int number, Gemstone gem, String code, int lapisCorpses, int umberCorpses, int tungstenCorpses, long enteredAt, double initialProfit, double finalProfit) {}

    private final List<Entry> entries = new ArrayList<>();
    private String currentCode;

    /** Records the shaft if it differs from the one the player was already in. */
    public synchronized boolean enter(ShaftDetector.Shaft shaft, int lapisCorpses, int umberCorpses, int tungstenCorpses, long now, double initialProfit) {
        if (shaft.code().equals(currentCode)) return false;
        currentCode = shaft.code();
        entries.add(new Entry(entries.size() + 1, shaft.gem(), shaft.code(), lapisCorpses, umberCorpses, tungstenCorpses, now, initialProfit, initialProfit));
        return true;
    }

    public synchronized void updateCurrentLapisCorpses(int lapisCorpses) {
        if (lapisCorpses < 0 || entries.isEmpty()) return;
        int last = entries.size() - 1;
        Entry e = entries.get(last);
        if (e.lapisCorpses() >= 0) return;
        entries.set(last, new Entry(
            e.number(), e.gem(), e.code(), lapisCorpses, e.umberCorpses(), e.tungstenCorpses(), e.enteredAt(), e.initialProfit(), e.finalProfit()));
    }

    public synchronized void updateCurrentCorpses(int lapisCorpses, int umberCorpses, int tungstenCorpses) {
        if (entries.isEmpty()) return;
        int last = entries.size() - 1;
        Entry e = entries.get(last);
        int newLapis = (lapisCorpses >= 0) ? lapisCorpses : e.lapisCorpses();
        int newUmber = (umberCorpses >= 0) ? umberCorpses : e.umberCorpses();
        int newTungsten = (tungstenCorpses >= 0) ? tungstenCorpses : e.tungstenCorpses();
        entries.set(last, new Entry(
            e.number(), e.gem(), e.code(), newLapis, newUmber, newTungsten, e.enteredAt(), e.initialProfit(), e.finalProfit()));
    }

    /** Call when the player is no longer in any shaft, so re-entering the same one logs again. */
    public synchronized void leave(double finalProfit) {
        if (!entries.isEmpty() && currentCode != null) {
            Entry last = entries.get(entries.size() - 1);
            if (last.code().equals(currentCode)) {
                entries.set(entries.size() - 1, new Entry(last.number(), last.gem(), last.code(), 
                    last.lapisCorpses(), last.umberCorpses(), last.tungstenCorpses(), last.enteredAt(), last.initialProfit(), finalProfit));
            }
        }
        currentCode = null;
    }

    public synchronized void clear() {
        entries.clear();
        currentCode = null;
    }

    public synchronized List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * How the given shaft's gemstone compares to the benchmark shaft: lapis corpses needed to
     * out-earn it, or AHEAD/OUT_OF_REACH/IMPOSSIBLE.
     */
    public static Pristine.Comparison compare(Gemstone gem, String benchmark, double miningSpeed, double fortune, double gemstoneFortune, double gemstoneSpread, double pristine, Map<String, Double> prices) {
        List<Mining.Breakdown> breakdown = Mining.calculateBreakdown(miningSpeed, fortune, gemstoneFortune, gemstoneSpread);
        Mining.Breakdown shaft = byName(breakdown, gem.name());
        Mining.Breakdown reference = byName(breakdown, benchmark);
        return Pristine.compareToReference(shaft, reference, prices, pristine, Pristine.MAX_LAPIS_CORPSES);
    }

    private static Mining.Breakdown byName(List<Mining.Breakdown> breakdown, String name) {
        return breakdown.stream()
            .filter(gem -> gem.gem().name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown gemstone " + name));
    }

}
