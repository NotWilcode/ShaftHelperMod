package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class HotmParserTest {
    @Test
    void readsProfessionalLevelFromItsTooltip() {
        assertEquals(54, HotmParser.professionalLevel(List.of(
            "Professional",
            "Level 54",
            "Grants +5 Mining Speed"
        )));
    }

    @Test
    void doesNotReadMiningSpeedLevelAsProfessionalLevel() {
        assertNull(HotmParser.professionalLevel(List.of(
            "Mining Speed",
            "Level 54",
            "Grants +1,000 Mining Speed"
        )));
    }

    @Test
    void findsMineshaftOnlyPerksAcrossHotmTooltips() {
        HotmParser.Perks perks = HotmParser.perks(List.of(
            List.of("Professional", "Level 100"),
            List.of("Eager Adventurer", "Level 100", "Grants +4000 Mining Speed"),
            List.of("Mineshaft Mayhem", "Level 100"),
            List.of("Steady Hand", "Level 100", "Grants +10 Gemstone Spread"),
            List.of("Rags to Riches", "Level 50", "Grants +2000 Mining Fortune")
        ));

        assertEquals(100, perks.professionalLevel());
        assertEquals(true, perks.eagerAdventurer());
        assertEquals(true, perks.mineshaftMayhem());
        assertEquals(true, perks.steadyHand());
        assertEquals(true, perks.ragsToRiches());
    }
}