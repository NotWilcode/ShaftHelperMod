package dev.shafthelper.core;

/**
 * A Hypixel Skyblock gemstone: its block strength, the Bazaar product ids of the rough and flawed
 * drops, the flawless tier the drops are valued from, and a chat color.
 */
public record Gemstone(String name, int strength, int color, String roughId, String flawedId, String flawlessId, boolean isGemstone) {

    public static Gemstone of(String name, int strength, String prefix, int color) {
        return new Gemstone(
            name,
            strength,
            color,
            "ROUGH_" + prefix + "_GEM",
            "FLAWED_" + prefix + "_GEM",
            "FLAWLESS_" + prefix + "_GEM",
            true
        );
    }

    public static Gemstone ofShaftType(String name, int strength, String prefix, int color) {
        return new Gemstone(
            name,
            strength,
            color,
            "ROUGH_" + prefix + "_GEM",
            "FLAWED_" + prefix + "_GEM",
            "FLAWLESS_" + prefix + "_GEM",
            false
        );
    }
}
