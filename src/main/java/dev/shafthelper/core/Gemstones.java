package dev.shafthelper.core;

import java.util.List;
import java.util.Optional;

/** Gemstone block strengths as used by Hypixel Skyblock's gemstone blocks. */
public final class Gemstones {

    public static final List<Gemstone> ALL = List.of(
        Gemstone.of("Ruby", 2300, "RUBY", 0xE91E63),
        Gemstone.of("Jade", 3000, "JADE", 0x4ADE80),
        Gemstone.of("Amber", 3000, "AMBER", 0xF97316),
        Gemstone.of("Amethyst", 3000, "AMETHYST", 0xA855F7),
        Gemstone.of("Sapphire", 3000, "SAPPHIRE", 0x3B82F6),
        Gemstone.of("Opal", 3000, "OPAL", 0xF5F5F4),
        Gemstone.of("Topaz", 3800, "TOPAZ", 0xFACC15),
        Gemstone.of("Jasper", 4800, "JASPER", 0xF472B6),
        Gemstone.of("Onyx", 5200, "ONYX", 0x57534E),
        Gemstone.of("Aquamarine", 5200, "AQUAMARINE", 0x67E8F9),
        Gemstone.of("Citrine", 5200, "CITRINE", 0xD97706),
        Gemstone.of("Peridot", 5200, "PERIDOT", 0x84CC16),
        Gemstone.ofShaftType("Titanium", 3000, "TITANIUM", 0xA0A0A0),
        Gemstone.ofShaftType("Tungsten", 3000, "TUNGSTEN", 0x808080),
        Gemstone.ofShaftType("Umber", 3000, "UMBER", 0x8B4513),
        Gemstone.ofShaftType("Fairy", 3000, "FAIRY", 0xFFB6C1),
        Gemstone.ofShaftType("Little", 3000, "LITTLE", 0x87CEEB)
    );

    /** A block drops 3-5 gems before Mining Fortune. */
    public static final int MIN_DROPS = 3;
    public static final int MAX_DROPS = 5;
    public static final double AVG_DROPS = (MIN_DROPS + MAX_DROPS) / 2.0;

    /** Crafting ratio: 80 of a tier make one of the next (rough -> flawed -> fine -> flawless). */
    public static final int TIER_RATIO = 80;
    public static final int ROUGH_PER_FLAWED = TIER_RATIO;
    public static final int FLAWED_PER_FLAWLESS = TIER_RATIO * TIER_RATIO;
    public static final int ROUGH_PER_FLAWLESS = TIER_RATIO * TIER_RATIO * TIER_RATIO;

    public static Optional<Gemstone> byName(String name) {
        return ALL.stream().filter(gem -> gem.name().equalsIgnoreCase(name)).findFirst();
    }

    private Gemstones() {}
}
