package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricesTest {

    @Test
    void dropsAreValuedFromTheFlawlessTheyCraftIntoNotTheirOwnListing() {
        Prices.Valued valued = Prices.valueFromFlawless(Map.of(
            "ROUGH_RUBY_GEM", 15.0,
            "FLAWED_RUBY_GEM", 1_695.0,
            "FLAWLESS_RUBY_GEM", 2_275_200.0
        ));

        // 80 rough = 1 flawed = ... = 512000 rough per flawless, and 6400 flawed per flawless.
        assertEquals(2_275_200.0 / 512_000, valued.prices().get("ROUGH_RUBY_GEM"));
        assertEquals(2_275_200.0 / 6_400, valued.prices().get("FLAWED_RUBY_GEM"));
        // Both listings are more than double their flawless value, so both are reported as inflated.
        assertEquals(List.of("ROUGH_RUBY_GEM", "FLAWED_RUBY_GEM"),
            valued.adjustments().stream().map(Prices.Adjustment::id).toList());
    }

    @Test
    void aListingNearItsFlawlessValueIsRepricedQuietly() {
        Prices.Valued valued = Prices.valueFromFlawless(Map.of(
            "ROUGH_JADE_GEM", 12.0,
            "FLAWED_JADE_GEM", 900.0,
            "FLAWLESS_JADE_GEM", 5_120_000.0
        ));

        assertEquals(10, valued.prices().get("ROUGH_JADE_GEM"));
        assertEquals(800, valued.prices().get("FLAWED_JADE_GEM"));
        assertTrue(valued.adjustments().isEmpty());
    }

    @Test
    void aGemstoneWithNoFlawlessPriceFallsBackToItsOwnListings() {
        Prices.Valued valued = Prices.valueFromFlawless(Map.of(
            "ROUGH_OPAL_GEM", 5.0,
            "FLAWED_OPAL_GEM", 400.0,
            "FLAWLESS_OPAL_GEM", 0.0
        ));

        assertEquals(5, valued.prices().get("ROUGH_OPAL_GEM"));
        assertEquals(400, valued.prices().get("FLAWED_OPAL_GEM"));
    }

    @Test
    void theFlawlessTierIsFetchedAlongsideTheDrops() {
        assertTrue(Prices.GEM_PRODUCT_IDS.contains("FLAWLESS_JASPER_GEM"));
        assertEquals(17 * 3, Prices.GEM_PRODUCT_IDS.size()); // 17 total (12 gemstones + 5 shaft types) all have product IDs
    }
}
