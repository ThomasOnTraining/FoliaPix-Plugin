package com.foliapix.http;

import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;

public class HttpClientUtil {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static CompletableFuture<String> postAsync(String url, String json, String apiKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .header("X-API-KEY", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.failedFuture(e);
        }
    }
}
