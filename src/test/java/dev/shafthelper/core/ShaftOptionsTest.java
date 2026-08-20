package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ShaftOptionsTest {

    @Test
    void emptyInputLeavesEveryDefault() {
        ShaftOptions options = ShaftOptions.parse("");
        assertNull(options.miningSpeed);
        assertNull(options.help);
        assertEquals(0, options.fortune);
        assertEquals(Cold.DEFAULT_EFFICIENCY, options.efficiency);
        assertEquals("Jasper", options.benchmark);
        assertEquals(Prices.Mode.SELL_OFFER, options.priceMode);
        assertEquals(Prices.Data.AVERAGE, options.priceData);
        assertEquals(Prices.Basis.FLAWLESS, options.priceBasis);
    }

    @Test
    void parsesTheFullDiscordStyleOptionString() {
        ShaftOptions options = ShaftOptions.parse(
            "mining_speed:10356 mining_fortune:2624 pristine:15.15 type:amber lapis:3 cold_res:123.5"
            + " efficiency:90 prices:insta_sell price_data:live price_basis:listed benchmark:ruby");

        assertEquals(10356, options.miningSpeed);
        assertEquals(2624, options.fortune);
        assertEquals(15.15, options.pristine);
        assertEquals("Amber", options.type);
        assertEquals(3, options.lapis);
        assertEquals(123.5, options.coldRes);
        assertEquals(90, options.efficiency);
        assertEquals(Prices.Mode.INSTA_SELL, options.priceMode);
        assertEquals(Prices.Data.LIVE, options.priceData);
        assertEquals(Prices.Basis.LISTED, options.priceBasis);
        assertEquals("Ruby", options.benchmark);
        assertEquals(java.util.Set.of("mining_speed", "mining_fortune", "pristine", "type",
            "lapis", "cold_res", "efficiency", "prices", "price_data", "price_basis", "benchmark"),
            options.given);
    }

    @Test
    void rejectsMalformedTokensUnknownKeysAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> ShaftOptions.parse("1500"));
        assertThrows(IllegalArgumentException.class, () -> ShaftOptions.parse("nonsense:1"));
        assertThrows(IllegalArgumentException.class, () -> ShaftOptions.parse("mining_speed:0"));
        assertThrows(IllegalArgumentException.class, () -> ShaftOptions.parse("lapis:5"));
        assertThrows(IllegalArgumentException.class, () -> ShaftOptions.parse("type:Diamond"));
        assertThrows(IllegalArgumentException.class, () -> ShaftOptions.parse("prices:maybe"));
    }

    @Test
    void speedAndFortuneAliasesWork() {
        ShaftOptions options = ShaftOptions.parse("speed:1500 fortune:400");
        assertEquals(1500, options.miningSpeed);
        assertEquals(400, options.fortune);
        assertEquals(java.util.Set.of("mining_speed", "mining_fortune"), options.given);
    }
}
