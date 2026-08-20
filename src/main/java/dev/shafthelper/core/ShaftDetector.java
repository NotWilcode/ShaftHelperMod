package dev.shafthelper.core;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spots which gemstone mineshaft the player is in from the tab list. Hypixel names shafts with
 * the first four letters of the gemstone plus a variant suffix: JASP_C (crystal), PERI_1,
 * AMBE_2, and so on — only the four-letter prefix matters here.
 */
public final class ShaftDetector {

    /** First four letters of each gemstone name, uppercased — unique across all twelve. */
    public static final Map<String, Gemstone> CODES = Gemstones.ALL.stream()
        .collect(Collectors.toUnmodifiableMap(
            gem -> gem.name().substring(0, 4).toUpperCase(Locale.ROOT),
            gem -> gem));

    private static final Pattern SHAFT_CODE = Pattern.compile(
        "\\b(" + String.join("|", CODES.keySet()) + ")_([A-Z0-9]{1,3})\\b");

    /** A detected shaft, e.g. code "JASP_C" for a crystal Jasper shaft. */
    public record Shaft(Gemstone gem, String code) {}

    /** The first shaft code found in the given tab list / scoreboard lines. */
    public static Optional<Shaft> detect(Iterable<String> lines) {
        for (String raw : lines) {
            String line = StatsParser.stripFormatting(raw);
            Matcher matcher = SHAFT_CODE.matcher(line);
            if (matcher.find()) {
                return Optional.of(new Shaft(CODES.get(matcher.group(1)), matcher.group()));
            }
        }
        return Optional.empty();
    }

    private ShaftDetector() {}
}
