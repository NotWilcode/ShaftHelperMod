package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShaftDetectorTest {

    @Test
    void everyGemstoneHasAUniqueFourLetterCode() {
        assertEquals(Gemstones.ALL.size(), ShaftDetector.CODES.size());
        assertEquals("Jasper", ShaftDetector.CODES.get("JASP").name());
        assertEquals("Peridot", ShaftDetector.CODES.get("PERI").name());
        assertEquals("Amber", ShaftDetector.CODES.get("AMBE").name());
        assertEquals("Amethyst", ShaftDetector.CODES.get("AMET").name());
        assertEquals("Sapphire", ShaftDetector.CODES.get("SAPP").name());
    }

    @Test
    void detectsCrystalShaft() {
        Optional<ShaftDetector.Shaft> shaft = ShaftDetector.detect(List.of(
            "some_player", "Area: Mineshaft", "JASP_C"));
        assertEquals("Jasper", shaft.orElseThrow().gem().name());
        assertEquals("JASP_C", shaft.orElseThrow().code());
    }

    @Test
    void detectsNumberedVariants() {
        assertEquals("Peridot",
            ShaftDetector.detect(List.of("PERI_1")).orElseThrow().gem().name());
        assertEquals("Amber",
            ShaftDetector.detect(List.of("AMBE_2")).orElseThrow().gem().name());
    }

    @Test
    void detectsCodeEmbeddedInALine() {
        Optional<ShaftDetector.Shaft> shaft = ShaftDetector.detect(List.of(
            "\u00a7b\u00a7lArea: \u00a7rSAPP_1 Mineshaft"));
        assertEquals("Sapphire", shaft.orElseThrow().gem().name());
    }

    @Test
    void ignoresPrefixWithoutVariantSuffix() {
        assertTrue(ShaftDetector.detect(List.of("JASP", "RUBY mining guide")).isEmpty());
    }

    @Test
    void ignoresUnknownCodes() {
        assertTrue(ShaftDetector.detect(List.of("XXXX_C", "MITH_1")).isEmpty());
    }

    @Test
    void emptyLinesGiveNoShaft() {
        assertTrue(ShaftDetector.detect(List.of()).isEmpty());
    }
}
