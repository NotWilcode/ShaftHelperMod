package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcTrackerTest {

    private static final long HOUR = 3600_000;

    @Test
    void recordsPristineProcMessages() {
        ProcTracker tracker = new ProcTracker();
        assertTrue(tracker.record("PRISTINE! You found \u2727 Flawed Ruby Gemstone x28!", 0));
        assertTrue(tracker.record("\u00a7d\u00a7lPRISTINE! \u00a7rYou found \u00a7aFlawed Jasper Gemstone \u00a7rx14!", 0));
        assertEquals(2, tracker.procs());
    }

    @Test
    void ignoresOtherChat() {
        ProcTracker tracker = new ProcTracker();
        assertFalse(tracker.record("You found a Flawless Ruby Gemstone!", 0));
        assertFalse(tracker.record("PRISTINE! You found something odd", 0));
        assertFalse(tracker.record(null, 0));
        assertEquals(0, tracker.procs());
    }

    @Test
    void estimatesCoinsPerHourFromProcsAndRuntime() {
        ProcTracker tracker = new ProcTracker();
        tracker.record("PRISTINE! You found Flawed Ruby Gemstone x80!", 0);
        tracker.record("PRISTINE! You found Flawed Ruby Gemstone x80!", HOUR / 2);

        // 160 flawed at 100 coins each; at 100 Pristine no rough is extrapolated.
        Map<String, Double> prices = Map.of("FLAWED_RUBY_GEM", 100.0, "ROUGH_RUBY_GEM", 2.0);
        ProcTracker.Estimate estimate = tracker.estimate(prices, 100, HOUR).orElseThrow();
        assertEquals(2, estimate.procs());
        assertEquals(16_000, estimate.flawedValue());
        assertEquals(16_000, estimate.coinsPerHour(), 0.001);
    }

    @Test
    void extrapolatesRoughDropsFromPristineChance() {
        ProcTracker tracker = new ProcTracker();
        tracker.record("PRISTINE! You found Flawed Ruby Gemstone x100!", 0);

        // At 50 Pristine, each flawed gem implies one rough gem mined alongside it.
        Map<String, Double> prices = Map.of("FLAWED_RUBY_GEM", 10.0, "ROUGH_RUBY_GEM", 1.0);
        ProcTracker.Estimate estimate = tracker.estimate(prices, 50, HOUR).orElseThrow();
        assertEquals(1_000, estimate.flawedValue());
        assertEquals(1_100, estimate.coinsPerHour(), 0.001);
    }

    @Test
    void emptyUntilFirstProcAndAfterReset() {
        ProcTracker tracker = new ProcTracker();
        assertEquals(Optional.empty(), tracker.estimate(Map.of(), 50, HOUR));
        tracker.record("PRISTINE! You found Flawed Topaz Gemstone x2!", 0);
        tracker.reset();
        assertEquals(0, tracker.procs());
        assertEquals(Optional.empty(), tracker.estimate(Map.of(), 50, HOUR));
    }
}
