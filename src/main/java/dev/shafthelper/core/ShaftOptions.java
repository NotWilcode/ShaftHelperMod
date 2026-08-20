package dev.shafthelper.core;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The /shaft options, given in chat as space-separated key:value pairs, the same syntax the
 * Discord bot used: {@code /shaft mining_speed:1500 type:Amber lapis:3}.
 */
public final class ShaftOptions {

    public static final String DEFAULT_BENCHMARK = "Jasper";

    public Integer help;
    public Integer miningSpeed;
    public int fortune = 0;
    public int gemstoneFortune = 0;
    public int gemstoneSpread = 0;
    public double pristine = 0;
    public String type;
    public int lapis = 0;
    public double efficiency = Cold.DEFAULT_EFFICIENCY;
    public double coldRes = 0;
    public Prices.Mode priceMode = Prices.DEFAULT_MODE;
    public Prices.Data priceData = Prices.DEFAULT_DATA;
    public Prices.Basis priceBasis = Prices.DEFAULT_BASIS;
    public String benchmark = DEFAULT_BENCHMARK;

    /** Canonical keys the user typed, so saved-config defaults never override explicit options. */
    public final Set<String> given = new HashSet<>();

    public static ShaftOptions parse(String input) {
        ShaftOptions options = new ShaftOptions();
        if (input == null || input.isBlank()) return options;

        for (String token : input.trim().split("\\s+")) {
            int colon = token.indexOf(':');
            if (colon <= 0 || colon == token.length() - 1) {
                throw new IllegalArgumentException("Options look like key:value, e.g. mining_speed:1500 — got \"" + token + "\"");
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            options.set(key, value);
            options.given.add(canonical(key));
        }
        return options;
    }

    private static String canonical(String key) {
        return switch (key) {
            case "speed" -> "mining_speed";
            case "fortune" -> "mining_fortune";
            case "gemstone_fortune" -> "gemstone_fortune";
            case "gemstone_spread" -> "gemstone_spread";
            default -> key;
        };
    }

    private void set(String key, String value) {
        switch (key) {
            case "help" -> help = intIn(key, value, 1, 4);
            case "mining_speed", "speed" -> miningSpeed = intIn(key, value, 1, 100_000);
            case "mining_fortune", "fortune" -> fortune = intIn(key, value, 0, 10_000);
            case "gemstone_fortune" -> gemstoneFortune = intIn(key, value, 0, 10_000);
            case "gemstone_spread" -> gemstoneSpread = intIn(key, value, 0, 100);
            case "pristine" -> pristine = doubleIn(key, value, 0, 100);
            case "type" -> type = gemstone(key, value);
            case "lapis" -> lapis = intIn(key, value, 0, Pristine.MAX_LAPIS_CORPSES);
            case "efficiency" -> efficiency = doubleIn(key, value, 1, 100);
            case "cold_res" -> coldRes = doubleIn(key, value, 0, Cold.MAX_COLD_RESISTANCE);
            case "prices" -> priceMode = switch (value.toLowerCase(Locale.ROOT)) {
                case "sell_offer" -> Prices.Mode.SELL_OFFER;
                case "insta_sell", "instant_sell" -> Prices.Mode.INSTA_SELL;
                default -> throw new IllegalArgumentException("prices must be sell_offer or insta_sell");
            };
            case "price_data" -> priceData = switch (value.toLowerCase(Locale.ROOT)) {
                case "average" -> Prices.Data.AVERAGE;
                case "live" -> Prices.Data.LIVE;
                default -> throw new IllegalArgumentException("price_data must be average or live");
            };
            case "price_basis" -> priceBasis = switch (value.toLowerCase(Locale.ROOT)) {
                case "flawless" -> Prices.Basis.FLAWLESS;
                case "listed" -> Prices.Basis.LISTED;
                default -> throw new IllegalArgumentException("price_basis must be flawless or listed");
            };
            case "benchmark" -> benchmark = gemstone(key, value);
            default -> throw new IllegalArgumentException("Unknown option \"" + key + "\" — run /shaft for the guide");
        }
    }

    private static String gemstone(String key, String value) {
        return Gemstones.byName(value)
            .map(Gemstone::name)
            .orElseThrow(() -> new IllegalArgumentException(key + " must be a gemstone name, e.g. " + key + ":Amber"));
    }

    private static int intIn(String key, String value, int min, int max) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be a whole number");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    private static double doubleIn(String key, String value, double min, double max) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return parsed;
    }
}
