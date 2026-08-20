package dev.shafthelper.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tab-completion for the /shaft options string. Given the text typed so far, suggests option
 * keys (typing "mi" offers mining_speed: and mining_fortune:) and, after a colon, the values
 * an option accepts (type:Am offers type's Amber and Amethyst).
 */
public final class ShaftSuggestions {

    /** Option keys in the order they are suggested. Aliases (speed, fortune) are accepted by the parser but not suggested. */
    public static final List<String> KEYS = List.of(
        "mining_speed", "mining_fortune", "pristine", "type", "lapis", "efficiency",
        "cold_res", "prices", "price_data", "price_basis", "benchmark", "help");

    private static final List<String> GEMSTONE_NAMES =
        Gemstones.ALL.stream().map(Gemstone::name).collect(Collectors.toUnmodifiableList());

    /**
     * Completions for the token being typed, replacing the text from {@link #tokenStart} onward.
     *
     * @param input everything typed after "/shaft " so far
     */
    public static Result suggest(String input) {
        String text = input == null ? "" : input;
        int tokenStart = text.lastIndexOf(' ') + 1;
        String token = text.substring(tokenStart);
        Set<String> used = usedKeys(text.substring(0, tokenStart));

        int colon = token.indexOf(':');
        if (colon < 0) {
            String partial = token.toLowerCase(Locale.ROOT);
            List<String> keys = new ArrayList<>();
            for (String key : KEYS) {
                if (key.startsWith(partial) && !used.contains(key)) {
                    keys.add(key + ":");
                }
            }
            return new Result(tokenStart, keys);
        }

        String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
        String partial = token.substring(colon + 1).toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        for (String value : valuesFor(key)) {
            if (value.toLowerCase(Locale.ROOT).startsWith(partial)) {
                values.add(value);
            }
        }
        return new Result(tokenStart + colon + 1, values);
    }

    private static List<String> valuesFor(String key) {
        return switch (key) {
            case "type", "benchmark" -> GEMSTONE_NAMES;
            case "prices" -> List.of("sell_offer", "insta_sell");
            case "price_data" -> List.of("average", "live");
            case "price_basis" -> List.of("flawless", "listed");
            case "help" -> List.of("1", "2", "3", "4");
            case "lapis" -> List.of("0", "1", "2", "3", "4");
            default -> List.of();
        };
    }

    private static Set<String> usedKeys(String previousTokens) {
        return Arrays.stream(previousTokens.trim().split("\\s+"))
            .map(token -> {
                int colon = token.indexOf(':');
                return (colon > 0 ? token.substring(0, colon) : token).toLowerCase(Locale.ROOT);
            })
            .map(ShaftSuggestions::canonical)
            .collect(Collectors.toSet());
    }

    private static String canonical(String key) {
        return switch (key) {
            case "speed" -> "mining_speed";
            case "fortune" -> "mining_fortune";
            default -> key;
        };
    }

    /**
     * @param offset index into the input where the suggestions start replacing text
     * @param suggestions the completion strings
     */
    public record Result(int offset, List<String> suggestions) {}

    private ShaftSuggestions() {}
}
