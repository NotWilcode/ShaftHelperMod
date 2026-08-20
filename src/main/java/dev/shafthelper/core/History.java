package dev.shafthelper.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Average Bazaar prices over the last few days from Coflnet's history API, which smooths out the
 * short price spikes that manipulation creates. Shaped like {@link Bazaar} so both sources drop
 * into the same code path.
 */
public final class History {

    private static final String HISTORY_URL = "https://sky.coflnet.com/api/bazaar";
    private static final long CACHE_TTL_MS = 30 * 60_000;
    // The history API rate limits bursts, so requests go out a few at a time and back off on 429.
    private static final int CONCURRENCY = 2;
    private static final int RETRIES = 4;
    private static final long RETRY_DELAY_MS = 1_000;

    public static final int AVERAGE_DAYS = 3;

    public record Averages(JsonObject products, long lastUpdated, long fetchedAt, int days) {}

    private static Averages cache;

    private static String iso(long ms) {
        return Instant.ofEpochMilli(ms).toString().substring(0, 19);
    }

    /** Mean of a history series; the buckets Coflnet returns are evenly spaced. */
    static Double meanOf(JsonArray points, String key) {
        double sum = 0;
        int count = 0;
        for (JsonElement point : points) {
            if (!point.isJsonObject()) continue;
            JsonElement value = point.getAsJsonObject().get(key);
            if (value == null || value.isJsonNull()) continue;
            double number = value.getAsDouble();
            if (!Double.isFinite(number)) continue;
            sum += number;
            count += 1;
        }
        return count == 0 ? null : sum / count;
    }

    static JsonObject fetchProductHistory(String productId, long from, long to,
                                          HttpFetcher fetcher, long retryDelayMs) throws Exception {
        String url = HISTORY_URL + "/" + productId + "/history?start=" + iso(from) + "&end=" + iso(to);

        for (int attempt = 0; attempt < RETRIES; attempt += 1) {
            HttpFetcher.Response response = fetcher.fetch(url);
            if (response.status() == 429) {
                Thread.sleep(retryDelayMs * (1L << attempt));
                continue;
            }
            if (!response.ok()) {
                throw new IllegalStateException("History request failed for " + productId + ": " + response.status());
            }

            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) return null;
            JsonArray points = parsed.getAsJsonArray();
            // Coflnet calls the sell-offer side "buy" and the instant-sell side "sell", like Hypixel does.
            JsonObject quickStatus = new JsonObject();
            Double buy = meanOf(points, "buy");
            Double sell = meanOf(points, "sell");
            if (buy != null) quickStatus.addProperty("buyPrice", buy);
            if (sell != null) quickStatus.addProperty("sellPrice", sell);
            return quickStatus;
        }

        throw new IllegalStateException("History request rate limited for " + productId);
    }

    /** Runs the lookups a few at a time so the history API is not hit with 36 requests at once. */
    public static synchronized Averages fetchAverages(List<String> productIds, HttpFetcher fetcher,
                                                      long now, long retryDelayMs) throws Exception {
        if (cache != null && cache.days() == AVERAGE_DAYS && now - cache.fetchedAt() < CACHE_TTL_MS) {
            return cache;
        }

        long from = now - AVERAGE_DAYS * 24L * 60 * 60_000;
        Map<String, JsonObject> results = new HashMap<>();
        List<Thread> workers = new ArrayList<>();
        List<String> queue = new ArrayList<>(productIds);

        for (int i = 0; i < Math.min(CONCURRENCY, productIds.size()); i += 1) {
            Thread worker = Thread.ofVirtual().start(() -> {
                while (true) {
                    String productId;
                    synchronized (queue) {
                        if (queue.isEmpty()) return;
                        productId = queue.removeFirst();
                    }
                    try {
                        JsonObject quickStatus = fetchProductHistory(productId, from, now, fetcher, retryDelayMs);
                        if (quickStatus != null) {
                            synchronized (results) {
                                results.put(productId, quickStatus);
                            }
                        }
                    } catch (Exception error) {
                        System.err.println("History lookup failed for " + productId + ": " + error);
                    }
                }
            });
            workers.add(worker);
        }
        for (Thread worker : workers) worker.join();

        if (results.isEmpty()) throw new IllegalStateException("No price history available");

        JsonObject products = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : results.entrySet()) {
            JsonObject product = new JsonObject();
            product.add("quick_status", entry.getValue());
            products.add(entry.getKey(), product);
        }

        cache = new Averages(products, now, now, AVERAGE_DAYS);
        return cache;
    }

    public static synchronized void clearCache() {
        cache = null;
    }

    private History() {}
}
