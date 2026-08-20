package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.shafthelper.core.Pristine.Comparison;
import dev.shafthelper.core.Pristine.CorpseRow;
import dev.shafthelper.core.Pristine.Status;
import org.junit.jupiter.api.Test;

class FormatTest {

    private static CorpseRow row(Status status, double bonus, int corpses) {
        return new CorpseRow(null, new Comparison(status, bonus, corpses), 0, 0);
    }

    @Test
    void compactShortensLargeNumbers() {
        assertEquals("1.50B", Format.compact(1_500_000_000));
        assertEquals("2.34M", Format.compact(2_340_000));
        assertEquals("15.4k", Format.compact(15_400));
        assertEquals("42", Format.compact(42));
        assertEquals("6.4", Format.compact(6.4));
    }

    @Test
    void formatBonusPristinePrintsTheExactBonusOrWhyThereIsNone() {
        assertEquals("1.53", Format.formatBonusPristine(row(Status.REACHABLE, 1.534, 2)));
        assertEquals("12.50", Format.formatBonusPristine(row(Status.OUT_OF_REACH, 12.5, 13)));
        assertEquals("none", Format.formatBonusPristine(row(Status.AHEAD, 0, 0)));
        assertEquals("never", Format.formatBonusPristine(row(Status.IMPOSSIBLE, Double.POSITIVE_INFINITY, Integer.MAX_VALUE)));
        assertEquals("benchmark", Format.formatBonusPristine(row(Status.REFERENCE, 0, 0)));
    }

    @Test
    void formatCorpsesRoundsToWholeCorpsesAndMarksUnreachableShafts() {
        assertEquals("2 \u2714", Format.formatCorpses(row(Status.REACHABLE, 1.5, 2), 4));
        assertEquals("0 \u2714", Format.formatCorpses(row(Status.AHEAD, 0, 0), 4));
        assertEquals("13 (>4)", Format.formatCorpses(row(Status.OUT_OF_REACH, 12.5, 13), 4));
        assertEquals("skip", Format.formatCorpses(row(Status.IMPOSSIBLE, Double.POSITIVE_INFINITY, Integer.MAX_VALUE), 4));
        assertEquals("-", Format.formatCorpses(row(Status.REFERENCE, 0, 0), 4));
    }
}
