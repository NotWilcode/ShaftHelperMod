package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BazaarTest {

    private static final String SNAPSHOT = """
        {
          "success": true,
          "lastUpdated": 1700000000000,
          "products": {
            "ROUGH_RUBY_GEM": { "quick_status": { "sellPrice": 0.8, "buyPrice": 6.4 } },
            "ROUGH_ONYX_GEM": { "quick_status": { "sellPrice": 12, "buyPrice": 20 } }
          }
        }
        """;

    private record CountingFetcher(AtomicInteger calls, int status, String body) implements HttpFetcher {
        @Override
        public Response fetch(String url) {
            calls.incrementAndGet();
            return new Response(status, body);
        }
    }

    @BeforeEach
    void clearCache() {
        Bazaar.clearCache();
    }

    @Test
    void extractPricesPicksTheRequestedBazaarSide() {
        JsonObject products = JsonParser.parseString(SNAPSHOT).getAsJsonObject().getAsJsonObject("products");
        List<String> ids = List.of("ROUGH_RUBY_GEM", "ROUGH_ONYX_GEM", "ROUGH_JADE_GEM");

        Map<String, Double> instaSell = Bazaar.extractPrices(products, ids, Prices.Mode.INSTA_SELL);
        assertEquals(Map.of("ROUGH_RUBY_GEM", 0.8, "ROUGH_ONYX_GEM", 12.0), instaSell);

        Map<String, Double> sellOffer = Bazaar.extractPrices(products, ids, Prices.Mode.SELL_OFFER);
        assertEquals(Map.of("ROUGH_RUBY_GEM", 6.4, "ROUGH_ONYX_GEM", 20.0), sellOffer);
        assertNull(sellOffer.get("ROUGH_JADE_GEM"));
    }

    @Test
    void fetchCachesForAMinuteAndRefetchesAfterwards() throws Exception {
        CountingFetcher fetcher = new CountingFetcher(new AtomicInteger(), 200, SNAPSHOT);
        Bazaar.fetch(fetcher, 0);
        Bazaar.fetch(fetcher, 30_000);
        assertEquals(1, fetcher.calls().get());

        Bazaar.fetch(fetcher, 61_000);
        assertEquals(2, fetcher.calls().get());
    }

    @Test
    void fetchThrowsOnHttpAndApiFailures() {
        CountingFetcher httpError = new CountingFetcher(new AtomicInteger(), 502, "{}");
        Exception thrown = assertThrows(IllegalStateException.class, () -> Bazaar.fetch(httpError, 0));
        assertTrue(thrown.getMessage().contains("502"));

        CountingFetcher apiError = new CountingFetcher(new AtomicInteger(), 200,
            "{\"success\": false, \"cause\": \"nope\"}");
        thrown = assertThrows(IllegalStateException.class, () -> Bazaar.fetch(apiError, 0));
        assertTrue(thrown.getMessage().contains("nope"));
    }
}
