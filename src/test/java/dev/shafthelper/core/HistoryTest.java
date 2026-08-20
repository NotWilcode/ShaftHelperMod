package dev.shafthelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryTest {

    private static HttpFetcher stub(Map<String, String> series, List<String> urls) {
        return (url) -> {
            synchronized (urls) {
                urls.add(url);
            }
            String id = url.split("/api/bazaar/")[1].split("/")[0];
            String body = series.get(id);
            if ("rate-limited".equals(body)) return new HttpFetcher.Response(429, "[]");
            return new HttpFetcher.Response(200, body == null ? "[]" : body);
        };
    }

    @BeforeEach
    void clearCache() {
        History.clearCache();
    }

    @Test
    void fetchAveragesMeansEachHistorySeriesOverTheRequestedWindow() throws Exception {
        List<String> urls = new ArrayList<>();
        HttpFetcher fetcher = stub(Map.of(
            "ROUGH_RUBY_GEM", "[{\"buy\": 4, \"sell\": 1}, {\"buy\": 6, \"sell\": 2}, {\"buy\": 8, \"sell\": 3}]"
        ), urls);

        long now = 1_767_484_800_000L; // 2026-01-04T00:00:00Z
        History.Averages averages = History.fetchAverages(List.of("ROUGH_RUBY_GEM"), fetcher, now, 0);

        var quickStatus = averages.products().getAsJsonObject("ROUGH_RUBY_GEM").getAsJsonObject("quick_status");
        assertEquals(6, quickStatus.get("buyPrice").getAsDouble());
        assertEquals(2, quickStatus.get("sellPrice").getAsDouble());
        assertTrue(urls.getFirst().contains("start=2026-01-01T00:00:00&end=2026-01-04T00:00:00"));
    }

    @Test
    void fetchAveragesDropsProductsTheHistoryApiCannotServeAndCachesTheRest() throws Exception {
        List<String> urls = new ArrayList<>();
        HttpFetcher fetcher = stub(Map.of(
            "ROUGH_RUBY_GEM", "[{\"buy\": 4, \"sell\": 1}]",
            "ROUGH_JADE_GEM", "rate-limited"
        ), urls);

        List<String> ids = List.of("ROUGH_RUBY_GEM", "ROUGH_JADE_GEM");
        History.Averages first = History.fetchAverages(ids, fetcher, 0, 0);

        assertEquals(List.of("ROUGH_RUBY_GEM"), first.products().keySet().stream().toList());

        int callsAfterFirst = urls.size();
        History.fetchAverages(ids, fetcher, 60_000, 0);
        assertEquals(callsAfterFirst, urls.size());
    }
}
