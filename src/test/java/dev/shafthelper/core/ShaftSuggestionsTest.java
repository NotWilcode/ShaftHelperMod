package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShaftSuggestionsTest {

    @Test
    void emptyInputSuggestsAllKeys() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("");
        assertEquals(0, result.offset());
        assertEquals(ShaftSuggestions.KEYS.stream().map(key -> key + ":").toList(), result.suggestions());
    }

    @Test
    void partialKeyFiltersSuggestions() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("mi");
        assertEquals(0, result.offset());
        assertEquals(List.of("mining_speed:", "mining_fortune:"), result.suggestions());
    }

    @Test
    void partialKeyIsCaseInsensitive() {
        assertEquals(List.of("price_data:"), ShaftSuggestions.suggest("PRICE_D").suggestions());
    }

    @Test
    void secondTokenOffsetsPastFirst() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("mining_speed:1500 pr");
        assertEquals(18, result.offset());
        assertEquals(List.of("pristine:", "prices:", "price_data:", "price_basis:"), result.suggestions());
    }

    @Test
    void usedKeysAreNotSuggestedAgain() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("mining_speed:1500 ");
        assertTrue(result.suggestions().stream().noneMatch("mining_speed:"::equals));
        assertTrue(result.suggestions().contains("mining_fortune:"));
    }

    @Test
    void aliasCountsAsUsedKey() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("speed:1500 mi");
        assertEquals(List.of("mining_fortune:"), result.suggestions());
    }

    @Test
    void gemstoneValuesSuggestedAfterColon() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("type:Am");
        assertEquals(5, result.offset());
        assertEquals(List.of("Amber", "Amethyst"), result.suggestions());
    }

    @Test
    void benchmarkSuggestsGemstones() {
        ShaftSuggestions.Result result = ShaftSuggestions.suggest("mining_speed:1500 benchmark:");
        assertEquals(28, result.offset());
        assertEquals(Gemstones.ALL.size(), result.suggestions().size());
    }

    @Test
    void enumValuesSuggested() {
        assertEquals(List.of("sell_offer", "insta_sell"), ShaftSuggestions.suggest("prices:").suggestions());
        assertEquals(List.of("average", "live"), ShaftSuggestions.suggest("price_data:").suggestions());
        assertEquals(List.of("flawless", "listed"), ShaftSuggestions.suggest("price_basis:").suggestions());
        assertEquals(List.of("1", "2", "3", "4"), ShaftSuggestions.suggest("help:").suggestions());
        assertEquals(List.of("0", "1", "2", "3", "4"), ShaftSuggestions.suggest("lapis:").suggestions());
    }

    @Test
    void numericKeysHaveNoValueSuggestions() {
        assertEquals(List.of(), ShaftSuggestions.suggest("mining_speed:").suggestions());
    }
}
