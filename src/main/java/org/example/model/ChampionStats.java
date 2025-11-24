package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ChampionStats {
    private int wins;
    private int games;
    private final Map<String, Integer> roleCounts = new HashMap<>();
    private final Map<String, WinPlay> synergy = new HashMap<>();
    private final Map<String, WinPlay> counters = new HashMap<>();

    public void addGame(boolean win, String role) {
        games++;
        if (win) wins++;
        if (role != null && !role.isBlank()) {
            roleCounts.merge(role, 1, Integer::sum);
        }
    }

    public void addSynergy(String allyChamp, boolean win) {
        synergy.computeIfAbsent(allyChamp, k -> new WinPlay()).add(win);
    }

    public void addCounter(String enemyChamp, boolean win) {
        counters.computeIfAbsent(enemyChamp, k -> new WinPlay()).add(win);
    }

    // Existing convenience methods for business logic
    public int wins() { return wins; }
    public int games() { return games; }
    public Map<String, Integer> roleCounts() { return roleCounts; }
    public Map<String, WinPlay> synergy() { return synergy; }
    public Map<String, WinPlay> counters() { return counters; }

    // Jackson-friendly getters
    public int getWins() { return wins; }
    public int getGames() { return games; }
    public Map<String, Integer> getRoleCounts() { return roleCounts; }
    public Map<String, WinPlay> getSynergy() { return synergy; }
    public Map<String, WinPlay> getCounters() { return counters; }

    @JsonIgnore
    public double winRate() {
        return games == 0 ? 0.0 : (double) wins / games;
    }

    @JsonIgnore
    public Role primaryRole() {
        return roleCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> mapRole(entry.getKey()))
                .orElse(Role.UNKNOWN);
    }

    private Role mapRole(String lane) {
        if (lane == null) return Role.UNKNOWN;
        return switch (lane.toUpperCase()) {
            case "TOP" -> Role.TOP;
            case "JUNGLE" -> Role.JUNGLE;
            case "MIDDLE", "MID" -> Role.MID;
            case "ADC", "BOTTOM", "BOT" -> Role.BOTTOM;
            case "SUPPORT", "UTILITY" -> Role.SUPPORT;
            default -> Role.UNKNOWN;
        };
    }
}
