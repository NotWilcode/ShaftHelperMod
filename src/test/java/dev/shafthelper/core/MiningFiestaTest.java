package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MiningFiestaTest {
    @Test
    void findsActiveMiningFiestaFromEventsTab() {
        MiningFiesta.State state = MiningFiesta.parse(List.of(
            "Mayor: Cole",
            "Active Events:",
            "Bonus Mining Fiesta 1h 2m 34s",
            "Next Event: Season of Jerry"
        ));

        assertTrue(state.active());
        assertTrue(state.coleMayor());
        assertEquals("Bonus Mining Fiesta", state.label());
        assertEquals(3_754_000L, state.remainingMs());
    }

    @Test
    void findsUpcomingFiestaAndDoesNotMarkItActive() {
        MiningFiesta.State state = MiningFiesta.parse(List.of(
            "Next Event:",
            "Mining Fiesta 1d 3h 20m 35s"
        ));

        assertTrue(!state.active());
        assertEquals(98_435_000L, state.remainingMs());
    }

    @Test
    void tracksNextColeFiestaFromTheCalendarDate() {
        MiningFiesta.State state = MiningFiesta.parse(List.of(
            "Mayor: Cole",
            "Date: 3rd Early Winter 512"
        ));

        assertEquals("Mining Fiesta", state.label());
        assertEquals(183L * 20L * 60L * 1000L, state.remainingMs());
    }
}