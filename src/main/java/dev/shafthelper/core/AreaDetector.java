package dev.shafthelper.core;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects which area the player is in from the tab list/scoreboard.
 * Matches "Area: Dwarven Mines", "Area: Glacite Mineshafts", etc.
 */
public final class AreaDetector {

    public enum Area {
        DWARVEN_MINES,
        MINESHAFTS,
        UNKNOWN
    }

    // Matches "Area: Dwarven Mines", "Area: Glacite Mineshafts", etc. Case-insensitive.
    private static final Pattern AREA_PATTERN = Pattern.compile("(?i)Area:\\s*(.+)$");

    /**
     * The first area found in the given tab list / scoreboard lines.
     */
    public static Optional<Area> detect(Iterable<String> lines) {
        for (String raw : lines) {
            String line = StatsParser.stripFormatting(raw).trim();
            Matcher matcher = AREA_PATTERN.matcher(line);
            if (matcher.find()) {
                return Optional.of(fromName(matcher.group(1).trim()));
            }
        }
        return Optional.empty();
    }

    private static Area fromName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("dwarven")) return Area.DWARVEN_MINES;
        if (n.contains("mineshaft")) return Area.MINESHAFTS;
        return Area.UNKNOWN;
    }

    /**
     * Get the display name for an area (matches waypoint island names).
     */
    public static String getDisplayName(Area area) {
        return switch (area) {
            case DWARVEN_MINES -> "Dwarven Mines";
            case MINESHAFTS -> "Mineshaft";
            case UNKNOWN -> "";
            default -> "";
        };
    }

    /**
     * Get area from display name (reverse of getDisplayName).
     */
    public static Area fromDisplayName(String name) {
        if (name == null) return Area.UNKNOWN;
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.contains("dwarven")) return Area.DWARVEN_MINES;
        if (n.contains("mineshaft")) return Area.MINESHAFTS;
        return Area.UNKNOWN;
    }

    private AreaDetector() {}
}
