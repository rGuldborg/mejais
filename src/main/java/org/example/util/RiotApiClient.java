package org.example.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Wraps HttpClient with Riot headers, throttling, and simple retry logic.
 */
public class RiotApiClient {
    private final HttpClient httpClient;
    private final String apiKey;
    private final RiotRateLimiter rateLimiter;

    public RiotApiClient(String apiKey, RiotRateLimiter rateLimiter) {
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String get(String url) throws IOException, InterruptedException {
        return get(URI.create(url));
    }

    public String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("X-Riot-Token", apiKey)
                .GET()
                .build();
        return execute(request);
    }

    private String execute(HttpRequest request) throws IOException, InterruptedException {
        int attempts = 0;
        while (true) {
            attempts++;
            if (rateLimiter != null) {
                rateLimiter.acquire();
            }
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            if (status == 429) {
                long retryMillis = parseRetryAfterMillis(response);
                Thread.sleep(Math.max(retryMillis, 1000L));
                continue;
            }
            if (status >= 500 && attempts < 3) {
                Thread.sleep(500L * attempts);
                continue;
            }
            throw new IOException("Riot API " + request.uri() + " returned " + status + " body=" + truncate(response.body(), 400));
        }
    }

    private long parseRetryAfterMillis(HttpResponse<String> response) {
        Optional<String> retry = response.headers().firstValue("Retry-After");
        if (retry.isEmpty()) {
            return 0L;
        }
        try {
            double seconds = Double.parseDouble(retry.get().trim());
            return (long) Math.ceil(seconds * 1000);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String truncate(String body, int max) {
        if (body == null) return "null";
        return body.length() > max ? body.substring(0, max) + "..." : body;
    }
}
