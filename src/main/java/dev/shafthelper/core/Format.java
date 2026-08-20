package dev.shafthelper.core;

import dev.shafthelper.core.Pristine.CorpseRow;
import dev.shafthelper.core.Pristine.Status;
import java.util.Locale;

public final class Format {

    /** Compact coin/number formatting: 1234567 -> "1.23M". */
    public static String compact(double value) {
        if (!Double.isFinite(value)) return "-";
        double abs = Math.abs(value);
        if (abs >= 1e9) return String.format(Locale.ROOT, "%.2fB", value / 1e9);
        if (abs >= 1e6) return String.format(Locale.ROOT, "%.2fM", value / 1e6);
        if (abs >= 1e3) return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        return String.format(Locale.ROOT, abs >= 10 ? "%.0f" : "%.1f", value);
    }

    /** Exact bonus Pristine the shaft needs to out-earn the benchmark. */
    public static String formatBonusPristine(CorpseRow row) {
        return switch (row.status()) {
            case REFERENCE -> "benchmark";
            case AHEAD -> "none";
            case IMPOSSIBLE -> "never";
            default -> String.format(Locale.ROOT, "%.2f", row.bonus());
        };
    }

    /** Whole lapis corpses that covers the bonus, since each corpse is +1 Pristine. */
    public static String formatCorpses(CorpseRow row, int maxCorpses) {
        return switch (row.status()) {
            case REFERENCE -> "-";
            case AHEAD -> "0 \u2714";
            case IMPOSSIBLE -> "skip";
            case OUT_OF_REACH -> row.corpses() + " (>" + maxCorpses + ")";
            case REACHABLE -> row.corpses() + " \u2714";
        };
    }

    public static boolean countsTowardVerdict(CorpseRow row) {
        return row.status() != Status.REFERENCE
            && row.status() != Status.OUT_OF_REACH
            && row.status() != Status.IMPOSSIBLE;
    }

    private Format() {}
}
