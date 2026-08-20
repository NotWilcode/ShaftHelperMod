package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ColdTest {

    @Test
    void coldResistanceSlowsTheClock() {
        assertEquals(5, Cold.secondsPerCold(0));
        assertEquals(10, Cold.secondsPerCold(100));
        assertEquals(7.5, Cold.secondsPerCold(50));
    }

    @Test
    void aShaftEndsAt100ColdSoMaxColdResistanceBuysAbout20Minutes() {
        assertEquals(500, Cold.shaftSeconds(0));
        assertEquals(1_192.5, Cold.shaftSeconds(Cold.MAX_COLD_RESISTANCE));
        assertTrue(Math.abs(Cold.shaftSeconds(Cold.MAX_COLD_RESISTANCE) / 60 - 19.9) < 0.1);
    }

    @Test
    void shaftProfitIsTheHourlyRateOverTheTimeColdAllowsMinusDowntime() {
        Cold.ShaftRun run = Cold.shaftProfit(3_600_000, 100, 100);
        assertEquals(1_000.0 / 60, run.minutes());
        assertEquals(1_000_000, run.coins());

        Cold.ShaftRun lazier = Cold.shaftProfit(3_600_000, 100, 80);
        assertEquals(800_000, lazier.coins(), 1e-6);
    }

    @Test
    void negativeColdResistanceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cold.secondsPerCold(-1));
    }
}
