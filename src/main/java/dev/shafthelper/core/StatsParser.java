package dev.shafthelper.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls mining stats out of Hypixel's tab list widgets and scoreboard. Hypixel shows lines like
 * "⸕ Mining Speed: 1,500" in the tab list Stats widget, individual "Lapis: LOOTED" or "Lapis: NOT LOOTED",
 * "Umber: LOOTED", "Tungsten: LOOTED" entries in the tab menu, and "Cold: -50" on the mineshaft scoreboard;
 * formatting codes and icons around the numbers are ignored.
 */
public final class StatsParser {

    private static final Pattern FORMATTING = Pattern.compile("\u00a7.");
    private static final Pattern MINING_SPEED = stat("Mining Speed");
    private static final Pattern MINING_FORTUNE = stat("Mining Fortune");
    private static final Pattern GEMSTONE_FORTUNE = stat("Gemstone Fortune");
    private static final Pattern GEMSTONE_SPREAD = stat("Gemstone Spread");
    private static final Pattern PRISTINE = stat("Pristine");
    private static final Pattern FROZEN_CORPSES = Pattern.compile("(?i)Frozen Corpses:");
    private static final Pattern LAPIS_ENTRY = Pattern.compile("(?i)Lapis:\\s*(NOT LOOTED|LOOTED)");
    private static final Pattern UMBER_ENTRY = Pattern.compile("(?i)Umber:\\s*(NOT LOOTED|LOOTED)");
    private static final Pattern TUNGSTEN_ENTRY = Pattern.compile("(?i)Tungsten:\\s*(NOT LOOTED|LOOTED)");
    private static final Pattern COLD = Pattern.compile("(?i)Cold:\\s*-?([\\d,]+(?:\\.\\d+)?)");

    /** Hypixel puts the stat icon between the colon and the number: "Mining Speed: ⸕3445". */
    private static Pattern stat(String name) {
        return Pattern.compile("(?i)" + name + ":?\\s*[^\\d\\n]{0,4}?([\\d,]+(?:\\.\\d+)?)");
    }

    /** Stats found in the given lines; a field is null when its line was not present. */
    public record Stats(Double miningSpeed, Double miningFortune, Double gemstoneFortune, Double gemstoneSpread, Double pristine, Double lapisCorpses, Double umberCorpses, Double tungstenCorpses, Double cold) {
        public boolean isEmpty() {
            return miningSpeed == null && miningFortune == null && gemstoneFortune == null && gemstoneSpread == null && pristine == null && lapisCorpses == null && umberCorpses == null && tungstenCorpses == null && cold == null;
        }
    }

    public static String stripFormatting(String line) {
        return FORMATTING.matcher(line).replaceAll("");
    }

    public static Stats parse(List<String> lines) {
        Double speed = null;
        Double fortune = null;
        Double gemstoneFortune = null;
        Double gemstoneSpread = null;
        Double pristine = null;
        Double lapisCorpses = null;
        Double umberCorpses = null;
        Double tungstenCorpses = null;
        Double cold = null;

        int lapisCount = 0;
        int umberCount = 0;
        int tungstenCount = 0;
        
        for (String raw : lines) {
            String line = stripFormatting(raw);
            if (speed == null) speed = match(MINING_SPEED, line);
            if (fortune == null) fortune = match(MINING_FORTUNE, line);
            if (gemstoneFortune == null) gemstoneFortune = match(GEMSTONE_FORTUNE, line);
            if (gemstoneSpread == null) gemstoneSpread = match(GEMSTONE_SPREAD, line);
            if (pristine == null) pristine = match(PRISTINE, line);
            if (cold == null) cold = match(COLD, line);
            if (LAPIS_ENTRY.matcher(line).find()) lapisCount++;
            if (UMBER_ENTRY.matcher(line).find()) umberCount++;
            if (TUNGSTEN_ENTRY.matcher(line).find()) tungstenCount++;
        }
        if (lapisCount > 0) lapisCorpses = (double) lapisCount;
        if (umberCount > 0) umberCorpses = (double) umberCount;
        if (tungstenCount > 0) tungstenCorpses = (double) tungstenCount;
        
        return new Stats(speed, fortune, gemstoneFortune, gemstoneSpread, pristine, lapisCorpses, umberCorpses, tungstenCorpses, cold);
    }

    private static Double match(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) return null;
        try {
            return Double.parseDouble(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private StatsParser() {}
}
