package dev.shafthelper.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiningFiesta {
    private static final Pattern DURATION = Pattern.compile(
        "(?i)(?:(\\d+)\\s*d)?\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?"
    );
    private static final Pattern MAYOR_COLE = Pattern.compile("(?i)\\bmayor\\s*:?\\s*cole\\b");
    private static final Pattern SKYBLOCK_DATE = Pattern.compile(
        "(?i)\\b(\\d+)(?:st|nd|rd|th)?\\s+(Early Spring|Spring|Late Spring|Early Summer|Summer|Late Summer|Early Autumn|Autumn|Late Autumn|Early Winter|Winter|Late Winter)\\b"
    );
    private static final List<String> COLE_SEASONS = List.of(
        "early summer", "late summer", "autumn", "early winter"
    );

    public record State(boolean active, boolean coleMayor, String label, long remainingMs) {
        public static State empty() {
            return new State(false, false, "", 0L);
        }

        public boolean hasEvent() {
            return !label.isEmpty();
        }
    }

    public static State parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) return State.empty();

        boolean coleMayor = false;
        boolean activeSection = false;
        String label = "";
        long remainingMs = 0L;
        boolean active = false;
        int currentDay = -1;
        int currentSeason = -1;

        for (String raw : lines) {
            String line = stripFormatting(raw).trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (MAYOR_COLE.matcher(line).find()) coleMayor = true;
            Matcher date = SKYBLOCK_DATE.matcher(line);
            if (date.find()) {
                currentDay = Integer.parseInt(date.group(1));
                currentSeason = seasonIndex(date.group(2));
            }
            if (lower.contains("active events")) {
                activeSection = true;
                continue;
            }
            if (lower.contains("next event") || lower.contains("upcoming events")) {
                activeSection = false;
                continue;
            }
            if (!lower.contains("mining fiesta")) continue;

            active = active || activeSection;
            if (label.isEmpty()) label = line.substring(0, findDurationStart(line)).trim();
            long parsed = parseDuration(line.substring(findDurationStart(line)));
            if (parsed > 0L) remainingMs = parsed;
        }

        if (coleMayor && label.isEmpty() && currentDay > 0 && currentSeason >= 0) {
            int targetSeason = nextColeSeason(currentSeason, currentDay);
            int days = daysUntil(currentSeason, currentDay, targetSeason);
            label = "Mining Fiesta";
            remainingMs = days * 20L * 60L * 1000L;
        }

        return new State(active, coleMayor, label, remainingMs);
    }

    private static int nextColeSeason(int currentSeason, int currentDay) {
        for (String season : COLE_SEASONS) {
            int target = seasonIndex(season);
            if (target > currentSeason || (target == currentSeason && currentDay < 1)) return target;
        }
        return seasonIndex(COLE_SEASONS.getFirst());
    }

    private static int daysUntil(int currentSeason, int currentDay, int targetSeason) {
        int seasonDistance = (targetSeason - currentSeason + 12) % 12;
        if (seasonDistance == 0) seasonDistance = 12;
        return (31 - currentDay) + (seasonDistance - 1) * 31;
    }

    private static int seasonIndex(String season) {
        return List.of(
            "early spring", "spring", "late spring", "early summer", "summer", "late summer",
            "early autumn", "autumn", "late autumn", "early winter", "winter", "late winter"
        ).indexOf(season.toLowerCase(Locale.ROOT));
    }

    private static int findDurationStart(String line) {
        Matcher matcher = Pattern.compile("(?i)\\d+\\s*[dhms]").matcher(line);
        return matcher.find() ? matcher.start() : line.length();
    }

    static long parseDuration(String text) {
        Matcher matcher = DURATION.matcher(text.trim());
        if (!matcher.matches()) return 0L;
        long days = value(matcher.group(1));
        long hours = value(matcher.group(2));
        long minutes = value(matcher.group(3));
        long seconds = value(matcher.group(4));
        return (((days * 24L + hours) * 60L + minutes) * 60L + seconds) * 1000L;
    }

    private static long value(String value) {
        return value == null || value.isEmpty() ? 0L : Long.parseLong(value);
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("\\u00a7.", "");
    }

    private MiningFiesta() {}
}