package org.example.collector;

import com.merakianalytics.orianna.types.common.Platform;
import com.merakianalytics.orianna.types.common.Queue;
import org.example.model.StatsSnapshot;
import org.example.util.RiotApiClient;
import org.example.util.RiotRateLimiter;

import java.io.File;
import java.time.Duration;
import java.util.List;

/**
 * CLI entry to collect matches, aggregate stats, and write a snapshot JSON.
 * Usage (example):
 *   RIOT_API_KEY=... RIOT_PLATFORM=EUROPE_WEST mvn exec:java -Dexec.mainClass=org.example.collector.CollectorRunner
 */
public class CollectorRunner {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("RIOT_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[CollectorRunner] Missing RIOT_API_KEY");
            return;
        }
        String platformTag = System.getenv().getOrDefault("RIOT_PLATFORM", "EUROPE_WEST");
        int limit = parseIntEnv("MATCH_LIMIT", 300);
        int seeds = parseIntEnv("SEED_COUNT", 25);
        int perSecond = parseIntEnv("RIOT_RATE_PER_SECOND", 20);
        int perTwoMinutes = parseIntEnv("RIOT_RATE_PER_TWO_MINUTES", 100);
        File snapshotFile = new File("data/snapshot.json");

        Platform platform = parsePlatform(platformTag);
        RiotRateLimiter rateLimiter = new RiotRateLimiter(perSecond, Duration.ofSeconds(1), perTwoMinutes, Duration.ofMinutes(2));
        RiotApiClient apiClient = new RiotApiClient(apiKey, rateLimiter);

        System.out.println("[CollectorRunner] Fetching up to " + limit + " matches from queue 420 on " + platform + " with " + seeds + " seeds.");
        MatchFetcher fetcher = new MatchFetcher(platform, apiClient);
        List<String> matchIds = fetcher.fetchRecentMatchIds(Queue.RANKED_SOLO, limit, seeds);

        MatchAggregator aggregator = new MatchAggregator(platform, apiClient);
        StatsSnapshot snapshot = aggregator.aggregate(matchIds);

        SnapshotStore store = new SnapshotStore(snapshotFile);
        if (!snapshotFile.getParentFile().exists()) {
            snapshotFile.getParentFile().mkdirs();
        }
        store.save(snapshot);
        System.out.println("[CollectorRunner] Snapshot saved to " + snapshotFile.getAbsolutePath());
    }

    private static int parseIntEnv(String key, int fallback) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Platform parsePlatform(String tag) {
        if (tag == null || tag.isBlank()) return Platform.EUROPE_WEST;
        String normalized = tag.trim().toUpperCase().replace("-", "_");
        try {
            return Platform.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            System.err.println("[CollectorRunner] Unknown platform '" + tag + "', falling back to EUW.");
            return Platform.EUROPE_WEST;
        }
    }
}
