package dev.shafthelper.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Tiny fetch abstraction so the price sources can be exercised in tests without real HTTP. */
@FunctionalInterface
public interface HttpFetcher {

    record Response(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    Response fetch(String url) throws IOException, InterruptedException;

    static HttpFetcher real() {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        return (url) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        };
    }
}
