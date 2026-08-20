package dev.shafthelper.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live Hypixel Bazaar snapshot, cached for a minute. The v2 bazaar endpoint needs no API key. */
public final class Bazaar {

    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long CACHE_TTL_MS = 60_000;

    public record Snapshot(JsonObject products, long lastUpdated, long fetchedAt) {}

    private static Snapshot cache;

    public static synchronized Snapshot fetch(HttpFetcher fetcher, long now) throws Exception {
        if (cache != null && now - cache.fetchedAt() < CACHE_TTL_MS) return cache;

        HttpFetcher.Response response = fetcher.fetch(BAZAAR_URL);
        if (!response.ok()) {
            throw new IllegalStateException("Bazaar request failed: " + response.status());
        }
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!body.has("success") || !body.get("success").getAsBoolean()) {
            String cause = body.has("cause") ? body.get("cause").getAsString() : "unknown cause";
            throw new IllegalStateException("Bazaar request unsuccessful: " + cause);
        }

        cache = new Snapshot(body.getAsJsonObject("products"),
            body.has("lastUpdated") ? body.get("lastUpdated").getAsLong() : now, now);
        return cache;
    }

    public static synchronized void clearCache() {
        cache = null;
    }

    /**
     * Unit prices for the requested product ids.
     * INSTA_SELL is what a sell-to-Bazaar click pays; SELL_OFFER is what a filled sell offer pays.
     */
    public static Map<String, Double> extractPrices(JsonObject products, List<String> productIds, Prices.Mode mode) {
        Map<String, Double> prices = new HashMap<>();
        for (String id : productIds) {
            JsonElement product = products.get(id);
            if (product == null || !product.isJsonObject()) continue;
            JsonElement status = product.getAsJsonObject().get("quick_status");
            if (status == null || !status.isJsonObject()) continue;
            JsonObject quickStatus = status.getAsJsonObject();
            String key = mode == Prices.Mode.SELL_OFFER ? "buyPrice" : "sellPrice";
            JsonElement price = quickStatus.get(key);
            if (price == null || price.isJsonNull()) continue;
            prices.put(id, price.getAsDouble());
        }
        return prices;
    }

    private Bazaar() {}
}
