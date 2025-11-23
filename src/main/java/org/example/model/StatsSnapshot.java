package org.example.model;

import java.util.Map;

public record StatsSnapshot(Map<String, ChampionStats> champions) {
    public ChampionStats getOrDefault(String champ, ChampionStats fallback) {
        return champions != null && champions.containsKey(champ) ? champions.get(champ) : fallback;
    }
}
