package dev.shafthelper.client;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class PlayerTimersTest {
    @Test
    void parsesActiveEffectsBlockAndRomanNumeralDurations() {
        List<String> lines = List.of(
            "Active Effects: (38)",
            "Cold Resistance: 5m 30s",
            "Filet O' Fortune III: 55m",
            "Pristine Potato: 33m"
        );

        assertEquals(330_000L, PlayerTimers.parseEffectTime(lines, "Cold Resistance"));
        assertEquals(3_300_000L, PlayerTimers.parseEffectTime(lines, "Filet O' Fortune"));
        assertEquals(1_980_000L, PlayerTimers.parseEffectTime(lines, "Pristine Potato"));
    }

    @Test
    void parsesClockAndMixedDurations() {
        assertEquals(90_000L, PlayerTimers.parseDuration("1:30"));
        assertEquals(3_600_000L, PlayerTimers.parseDuration("1h"));
        assertEquals(1_500_000L, PlayerTimers.parseDuration("25m 0s"));
        assertNull(PlayerTimers.parseDuration("N/A"));
    }
}
