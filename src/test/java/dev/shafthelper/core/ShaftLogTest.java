package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.shafthelper.core.Pristine.Status;
import dev.shafthelper.core.ShaftDetector.Shaft;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShaftLogTest {

    private static final Map<String, Double> PRICES = Map.of(
        "ROUGH_JASPER_GEM", 8.0,
        "FLAWED_JASPER_GEM", 8.0 * 80,
        "ROUGH_SAPPHIRE_GEM", 1.0,
        "FLAWED_SAPPHIRE_GEM", 500.0
    );

    private static Shaft shaft(String code) {
        return ShaftDetector.detect(List.of(code)).orElseThrow();
    }

    @Test
    void numbersShaftsInOrder() {
        ShaftLog log = new ShaftLog();
        assertTrue(log.enter(shaft("JASP_C"), 0, 0, 0, 1, 0.0));
        log.leave(100.0);
        assertTrue(log.enter(shaft("PERI_1"), 1, 0, 0, 2, 100.0));
        assertEquals(1, log.entries().get(0).number());
        assertEquals(2, log.entries().get(1).number());
        assertEquals("Jasper", log.entries().get(0).gem().name());
        assertEquals(0, log.entries().get(0).lapisCorpses());
        assertEquals(0, log.entries().get(0).umberCorpses());
        assertEquals(0, log.entries().get(0).tungstenCorpses());
        assertEquals(1, log.entries().get(1).lapisCorpses());
        assertEquals(0, log.entries().get(1).umberCorpses());
        assertEquals(0, log.entries().get(1).tungstenCorpses());
        assertEquals(100.0, log.entries().get(0).finalProfit());
        assertEquals(100.0, log.entries().get(1).finalProfit());
    }

    @Test
    void stayingInTheSameShaftLogsOnlyOnce() {
        ShaftLog log = new ShaftLog();
        assertTrue(log.enter(shaft("JASP_C"), 0, 0, 0, 1, 0.0));
        assertFalse(log.enter(shaft("JASP_C"), 0, 0, 0, 2, 0.0));
        assertEquals(1, log.entries().size());
        log.leave(50.0);
        assertEquals(50.0, log.entries().get(0).finalProfit());
    }

    @Test
    void reenteringAfterLeavingLogsAgain() {
        ShaftLog log = new ShaftLog();
        log.enter(shaft("JASP_C"), 0, 0, 0, 1, 0.0);
        log.leave(100.0);
        assertTrue(log.enter(shaft("JASP_C"), 0, 0, 0, 2, 100.0));
        assertEquals(2, log.entries().size());
        assertEquals(100.0, log.entries().get(0).finalProfit());
        assertEquals(100.0, log.entries().get(1).initialProfit());
    }

    @Test
    void clearEmptiesTheLog() {
        ShaftLog log = new ShaftLog();
        log.enter(shaft("JASP_C"), 0, 0, 0, 1, 0.0);
        log.leave(50.0);
        log.clear();
        assertTrue(log.isEmpty());
    }

    @Test
    void leavingShaftRecordsFinalProfit() {
        ShaftLog log = new ShaftLog();
        log.enter(shaft("JASP_C"), 0, 0, 0, 1, 50.0);
        log.leave(150.0);
        assertEquals(50.0, log.entries().get(0).initialProfit());
        assertEquals(150.0, log.entries().get(0).finalProfit());
    }

    @Test
    void shaftSwitchingRecordsFinalProfit() {
        ShaftLog log = new ShaftLog();
        log.enter(shaft("JASP_C"), 0, 0, 0, 1, 50.0);
        log.leave(150.0);
        log.enter(shaft("PERI_1"), 1, 0, 0, 2, 150.0);
        assertEquals(50.0, log.entries().get(0).initialProfit());
        assertEquals(150.0, log.entries().get(0).finalProfit());
        assertEquals(150.0, log.entries().get(1).initialProfit());
    }

    @Test
    void compareMatchesPristineAgainstTheBenchmark() {
        Pristine.Comparison sapphire = ShaftLog.compare(
            ShaftDetector.CODES.get("SAPP"), "Jasper", 1000, 0, 0, 0, 0, PRICES);
        assertEquals(Status.REACHABLE, sapphire.status());
        assertTrue(sapphire.corpses() >= 1);

        Pristine.Comparison jasper = ShaftLog.compare(
            ShaftDetector.CODES.get("JASP"), "Jasper", 1000, 0, 0, 0, 0, PRICES);
        assertEquals(Status.REFERENCE, jasper.status());
    }
}
