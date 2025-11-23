package org.example.collector;

import com.merakianalytics.orianna.types.common.Platform;
import com.merakianalytics.orianna.types.common.Queue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.RiotApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches recent matches for a set of seed summoners in a given queue, deduplicated by match id.
 */
public class MatchFetcher {
    private final Platform platform;
    private final RiotApiClient apiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MatchFetcher(Platform platform, RiotApiClient apiClient) {
        this.platform = platform;
        this.apiClient = apiClient;
    }

    public List<String> fetchRecentMatchIds(Queue queue, int limit, int seeds) throws InterruptedException {
        Set<String> matchIds = new LinkedHashSet<>();
        List<String> puuids = fetchTierPuuids(queue, seeds);
        if (puuids.isEmpty()) {
            System.err.println("[MatchFetcher] No puuids found for queue " + queue);
            return new ArrayList<>();
        }

        for (String puuid : puuids) {
            if (matchIds.size() >= limit) break;
            try {
                List<String> ids = fetchMatchIdsForPuuid(puuid, queue, limit - matchIds.size());
                matchIds.addAll(ids);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (Exception e) {
                System.err.println("[MatchFetcher] Failed to fetch matches for puuid: " + e.getMessage());
            }
        }
        System.out.println("[MatchFetcher] Collected " + matchIds.size() + " match ids (unique) from " + puuids.size() + " seeds");
        return new ArrayList<>(matchIds);
    }

    private List<String> fetchTierPuuids(Queue queue, int seeds) throws InterruptedException {
        List<String> puuids = new ArrayList<>();
        try {
            List<String> tiers = List.of("EMERALD", "PLATINUM");
            String host = hostForPlatform(platform); // e.g., euw1.api.riotgames.com

            for (String tier : tiers) {
                for (int page = 1; page <= 5 && puuids.size() < seeds; page++) {
                    String url = "https://" + host + "/lol/league-exp/v4/entries/RANKED_SOLO_5x5/" + tier + "/I?page=" + page;
                    String body = apiClient.get(url);
                    JsonNode root = mapper.readTree(body);
                    if (root == null || !root.isArray()) {
                        System.err.println("[MatchFetcher] Unexpected response for " + tier + " page " + page + ": " + truncate(body, 200));
                        break;
                    }
                    for (JsonNode entry : root) {
                        if (puuids.size() >= seeds) break;
                        JsonNode puuidNode = entry.get("puuid");
                        String puuid = puuidNode != null ? puuidNode.asText() : null;
                        if (puuid != null && !puuid.isBlank()) {
                            puuids.add(puuid);
                        }
                    }
                }
                if (puuids.size() >= seeds) break;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (Exception e) {
            System.err.println("[MatchFetcher] Failed to fetch challenger names: " + e.getMessage());
        }
        if (puuids.isEmpty()) {
            System.err.println("[MatchFetcher] No puuids parsed; response may be empty or rate-limited.");
            try {
                String host = hostForPlatform(platform);
                String url = "https://" + host + "/lol/league-exp/v4/entries/RANKED_SOLO_5x5/EMERALD/I?page=1";
                String body = apiClient.get(url);
                System.err.println("[MatchFetcher] Debug body: " + truncate(body, 500));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (Exception debugEx) {
                System.err.println("[MatchFetcher] Debug fetch failed: " + debugEx.getMessage());
            }
        }
        return puuids;
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private List<String> fetchMatchIdsForPuuid(String puuid, Queue queue, int count) throws IOException, InterruptedException {
        int fetchCount = Math.min(Math.max(count, 1), 100); // Riot allows 1..100
        String regionHost = routingHostForPlatform(platform);
        String url = "https://" + regionHost + "/lol/match/v5/matches/by-puuid/" + puuid + "/ids?queue=" + queue.getId() + "&count=" + fetchCount;
        String body = apiClient.get(url);
        JsonNode root = mapper.readTree(body);
        List<String> ids = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode id : root) {
                ids.add(id.asText());
            }
        }
        return ids;
    }

    public static String hostForPlatform(Platform p) {
        String tag = p.getTag().toUpperCase(); // e.g., EUW, NA, KR, JP
        return switch (tag) {
            case "EUW" -> "euw1.api.riotgames.com";
            case "EUNE" -> "eun1.api.riotgames.com";
            case "NA" -> "na1.api.riotgames.com";
            case "KR" -> "kr.api.riotgames.com";
            case "JP" -> "jp1.api.riotgames.com";
            case "BR" -> "br1.api.riotgames.com";
            case "LA1" -> "la1.api.riotgames.com";
            case "LA2" -> "la2.api.riotgames.com";
            case "OC" -> "oc1.api.riotgames.com";
            case "RU" -> "ru.api.riotgames.com";
            case "TR" -> "tr1.api.riotgames.com";
            default -> tag.toLowerCase() + ".api.riotgames.com";
        };
    }

    public static String routingHostForPlatform(Platform p) {
        String tag = p.getTag().toUpperCase();
        return switch (tag) {
            case "EUW", "EUNE", "TR", "RU" -> "europe.api.riotgames.com";
            case "NA", "BR", "LA1", "LA2" -> "americas.api.riotgames.com";
            case "KR", "JP" -> "asia.api.riotgames.com";
            case "OC" -> "sea.api.riotgames.com";
            default -> "europe.api.riotgames.com";
        };
    }
}
