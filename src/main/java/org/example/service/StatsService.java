package org.example.service;

import org.example.model.ChampionStats;
import org.example.model.ChampionSummary;
import org.example.model.RecommendationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StatsService {
    List<ChampionSummary> fetchRecommended(RecommendationContext context);

    Optional<ChampionStats> findChampionStats(String championId);

    Map<String, ChampionStats> allChampionStats();
}
