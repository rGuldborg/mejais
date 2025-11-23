package org.example.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merakianalytics.orianna.types.common.Platform;
import org.example.model.ChampionStats;
import org.example.model.StatsSnapshot;
import org.example.util.RiotApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchAggregator {
    private final Platform platform;
    private final RiotApiClient apiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MatchAggregator(Platform platform, RiotApiClient apiClient) {
        this.platform = platform;
        this.apiClient = apiClient;
    }

    public StatsSnapshot aggregate(List<String> matchIds) throws InterruptedException {
        Map<String, ChampionStats> champStats = new HashMap<>();

        for (String matchId : matchIds) {
            try {
                JsonNode match = fetchMatch(matchId);
                if (match == null) continue;
                JsonNode info = match.get("info");
                if (info == null) continue;
                JsonNode participantsNode = info.get("participants");
                if (participantsNode == null || !participantsNode.isArray()) continue;

                Map<Integer, List<JsonNode>> byTeam = new HashMap<>();
                for (JsonNode participant : participantsNode) {
                    int teamId = participant.path("teamId").asInt();
                    byTeam.computeIfAbsent(teamId, k -> new ArrayList<>()).add(participant);
                }

                for (JsonNode participant : participantsNode) {
                    String champ = participant.path("championName").asText("");
                    if (champ.isBlank()) continue;
                    ChampionStats stats = champStats.computeIfAbsent(champ, k -> new ChampionStats());
                    boolean win = participant.path("win").asBoolean(false);
                    String role = deriveRole(participant);
                    stats.addGame(win, role);

                    String puuid = participant.path("puuid").asText("");
                    int teamId = participant.path("teamId").asInt();
                    List<JsonNode> allies = byTeam.getOrDefault(teamId, List.of());
                    for (JsonNode ally : allies) {
                        if (puuid.equals(ally.path("puuid").asText(""))) continue;
                        String allyChamp = ally.path("championName").asText("");
                        if (!allyChamp.isBlank()) {
                            stats.addSynergy(allyChamp, win);
                        }
                    }
                    for (Map.Entry<Integer, List<JsonNode>> entry : byTeam.entrySet()) {
                        if (entry.getKey() == teamId) continue;
                        for (JsonNode enemy : entry.getValue()) {
                            String enemyChamp = enemy.path("championName").asText("");
                            if (!enemyChamp.isBlank()) {
                                stats.addCounter(enemyChamp, win);
                            }
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (IOException e) {
                System.err.println("[MatchAggregator] Failed to load match " + matchId + ": " + e.getMessage());
            }
        }

        return new StatsSnapshot(champStats);
    }

    private JsonNode fetchMatch(String matchId) throws IOException, InterruptedException {
        String regionHost = MatchFetcher.routingHostForPlatform(platform);
        String url = "https://" + regionHost + "/lol/match/v5/matches/" + matchId;
        String body = apiClient.get(url);
        return mapper.readTree(body);
    }

    private String deriveRole(JsonNode participant) {
        String role = participant.path("teamPosition").asText("");
        if (role.isBlank()) role = participant.path("role").asText("");
        if (role.isBlank()) role = participant.path("lane").asText("");
        return role == null ? "" : role.toUpperCase();
    }
}
