package dev.shafthelper.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HotmParser {
    private static final Pattern LEVEL = Pattern.compile("(?i)\\blevel\\s+(\\d+)\\b");

    public record Perks(Integer professionalLevel, boolean eagerAdventurer, boolean mineshaftMayhem,
                        boolean steadyHand, boolean ragsToRiches) {}

    public static Integer professionalLevel(List<String> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) return null;
        boolean professional = false;
        for (String raw : tooltipLines) {
            String line = raw == null ? "" : raw.replaceAll("\\u00a7.", "").trim();
            if (line.toLowerCase(Locale.ROOT).contains("professional")) {
                professional = true;
                continue;
            }
            if (!professional) continue;
            Matcher level = LEVEL.matcher(line);
            if (level.find()) return Integer.parseInt(level.group(1));
        }
        return null;
    }

    public static Perks perks(List<List<String>> tooltips) {
        Integer professionalLevel = null;
        boolean eagerAdventurer = false;
        boolean mineshaftMayhem = false;
        boolean steadyHand = false;
        boolean ragsToRiches = false;

        if (tooltips != null) {
            for (List<String> tooltip : tooltips) {
                Integer level = professionalLevel(tooltip);
                if (level != null) professionalLevel = level;
                String text = String.join(" ", tooltip == null ? List.of() : tooltip)
                    .replaceAll("\\u00a7.", "")
                    .toLowerCase(Locale.ROOT);
                eagerAdventurer |= text.contains("eager adventurer");
                mineshaftMayhem |= text.contains("mineshaft mayhem");
                steadyHand |= text.contains("steady hand");
                ragsToRiches |= text.contains("rags to riches");
            }
        }

        return new Perks(professionalLevel, eagerAdventurer, mineshaftMayhem, steadyHand, ragsToRiches);
    }

    private HotmParser() {}
}