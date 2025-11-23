package org.example.service;

import org.example.model.ChampionStats;
import org.example.model.ChampionSummary;
import org.example.model.RecommendationContext;
import org.example.model.Role;
import org.example.model.SlotSelection;
import org.example.model.Tier;
import org.example.util.ChampionIconResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Optional;
import java.util.Random;

public class MockStatsService implements StatsService {
    private static final double OP_WEIGHT = 0.5;
    private static final double SYN_WEIGHT = 0.25;
    private static final double COUNTER_WEIGHT = 0.25;
    private static final Map<String, Double> WIN_RATES = Map.of(
            "Aatrox", 0.54,
            "Ahri", 0.52,
            "Darius", 0.50,
            "Garen", 0.49,
            "Vayne", 0.55,
            "Anivia", 0.53
    );
    private final Map<String, ChampionStats> cachedStats = new LinkedHashMap<>();

    @Override
    public List<ChampionSummary> fetchRecommended(RecommendationContext context) {
        List<ChampionSummary> result = new ArrayList<>();
        Set<String> excluded = excludedChampions(context);
        int limit = context == null ? 10 : context.limit();

        for (String id : WIN_RATES.keySet()) {
            if (excluded.contains(id)) continue;
            double opWr = clamp(WIN_RATES.getOrDefault(id, 0.48));
            double synWr = adjustForAllies(opWr, id, context);
            double coWr = adjustForEnemies(opWr, id, context);

            Tier opTier = Tier.fromWinRate(opWr);
            Tier synTier = Tier.fromWinRate(synWr, true);
            Tier coTier = Tier.fromWinRate(coWr, true);

            double score = weightedScore(opTier, synTier, coTier);
            result.add(new ChampionSummary(
                    id,
                    id,
                    opTier,
                    synTier,
                    coTier,
                    score,
                    ChampionIconResolver.load(id)
            ));
        }
        result.sort(Comparator.comparingDouble(ChampionSummary::score).reversed());
        return result.size() <= limit ? result : result.subList(0, limit);
    }

    @Override
    public Optional<ChampionStats> findChampionStats(String championId) {
        if (championId == null || championId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(cachedStats.computeIfAbsent(championId, this::generateStats));
    }

    @Override
    public Map<String, ChampionStats> allChampionStats() {
        WIN_RATES.keySet().forEach(id -> cachedStats.computeIfAbsent(id, this::generateStats));
        return Collections.unmodifiableMap(new LinkedHashMap<>(cachedStats));
    }

    private ChampionStats generateStats(String championId) {
        ChampionStats stats = new ChampionStats();
        double baseWr = WIN_RATES.getOrDefault(championId, 0.5);
        Random random = new Random(championId.hashCode());
        Role[] candidateRoles = {Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT};

        for (int i = 0; i < 150; i++) {
            Role role = candidateRoles[random.nextInt(candidateRoles.length)];
            boolean win = random.nextDouble() < baseWr;
            stats.addGame(win, role.name());
        }

        SAMPLE_SYNERGY.forEach(partner -> stats.addSynergy(partner, random.nextDouble() < baseWr));
        SAMPLE_COUNTERS.forEach(enemy -> stats.addCounter(enemy, random.nextDouble() < baseWr - 0.02));
        return stats;
    }

    private double adjustForAllies(double base, String champId, RecommendationContext context) {
        if (context == null || context.allySelections().isEmpty()) {
            return Double.NaN;
        }
        Role target = context.targetRole();
        double value = base;
        boolean matched = false;
        for (var ally : context.allySelections()) {
            if (!target.pairsWith(ally.role())) continue;
            matched = true;
            value += seededDelta(champId, ally.champion(), 0.004);
        }
        return matched ? clamp(value) : Double.NaN;
    }

    private double adjustForEnemies(double base, String champId, RecommendationContext context) {
        if (context == null || context.enemySelections().isEmpty()) {
            return Double.NaN;
        }
        Role target = context.targetRole();
        double value = base;
        boolean matched = false;
        for (var enemy : context.enemySelections()) {
            if (!target.contests(enemy.role())) continue;
            matched = true;
            value -= seededDelta(champId, enemy.champion(), 0.005);
        }
        return matched ? clamp(value) : Double.NaN;
    }

    private double seededDelta(String champ, String partner, double scale) {
        int hash = Math.abs((champ + ":" + partner).hashCode());
        double bias = (hash % 11) - 5; // -5..5
        return bias * scale / 10.0;
    }

    private double clamp(double value) {
        return Math.max(0.40, Math.min(0.65, value));
    }

    private double weightedScore(Tier op, Tier syn, Tier co) {
        double total = contribution(op, OP_WEIGHT) + contribution(syn, SYN_WEIGHT) + contribution(co, COUNTER_WEIGHT);
        double totalWeight = OP_WEIGHT + SYN_WEIGHT + COUNTER_WEIGHT;
        return total / totalWeight;
    }

    private double contribution(Tier tier, double weight) {
        double base = tier == Tier.NA ? Tier.B.score() : tier.score();
        return base * weight;
    }

    private Set<String> excludedChampions(RecommendationContext context) {
        if (context == null) {
            return Set.of();
        }
        return Stream.of(
                        context.allySelections().stream().map(SlotSelection::champion),
                        context.enemySelections().stream().map(SlotSelection::champion),
                        context.bannedChampions().stream()
                )
                .flatMap(s -> s)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static final List<String> SAMPLE_SYNERGY = List.of("Ahri", "Thresh", "Jarvan IV", "Lux");
    private static final List<String> SAMPLE_COUNTERS = List.of("Yorick", "Camille", "Fizz", "Lucian", "Vayne");
}
