package org.example.service;

import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.common.Platform;
import org.example.collector.SnapshotStore;
import org.example.model.ChampionStats;
import org.example.model.ChampionSummary;
import org.example.model.RecommendationContext;
import org.example.model.Role;
import org.example.model.SlotSelection;
import org.example.model.StatsSnapshot;
import org.example.model.Tier;
import org.example.model.WinPlay;
import org.example.util.ChampionIconResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RiotStatsService implements StatsService {
    private static final double OP_WEIGHT = 0.5;
    private static final double SYN_WEIGHT = 0.25;
    private static final double COUNTER_WEIGHT = 0.25;
    private static final int MIN_PAIR_GAMES = 5;
    private static final int MIN_TOTAL_GAMES = 30;
    private final Platform platform;
    private final StatsService fallback = new MockStatsService();
    private final File snapshotFile = new File("data/snapshot.json");
    private StatsSnapshot cachedSnapshot;
    private long cachedStamp = -1L;

    public RiotStatsService(String apiKey, String platformTag) {
        this.platform = parsePlatform(platformTag);
        Orianna.setRiotAPIKey(apiKey);
        Orianna.setDefaultPlatform(this.platform);
    }

    @Override
    public List<ChampionSummary> fetchRecommended(RecommendationContext context) {
        try {
            StatsSnapshot snapshot = snapshot();
            if (snapshot == null || snapshot.champions() == null || snapshot.champions().isEmpty()) {
                System.out.println("[RiotStatsService] No snapshot found, using fallback data.");
                return fallback.fetchRecommended(context);
            }
            List<ChampionSummary> summaries = new ArrayList<>();
            Set<String> excluded = excludedChampions(context);
            int limit = context == null ? 20 : context.limit();

            for (var entry : snapshot.champions().entrySet()) {
                String champion = entry.getKey();
                if (excluded.contains(champion)) continue;
                ChampionStats stats = entry.getValue();
                if (stats.games() < MIN_TOTAL_GAMES) continue;

                double op = clamp(stats.winRate());
                double synergy = synergyScore(stats, context);
                double counter = counterScore(stats, context);
                Tier opTier = Tier.fromWinRate(op);
                Tier synTier = Tier.fromWinRate(synergy, true);
                Tier coTier = Tier.fromWinRate(counter, true);
                double score = weightedScore(opTier, synTier, coTier);

                summaries.add(new ChampionSummary(
                        champion,
                        champion,
                        opTier,
                        synTier,
                        coTier,
                        score,
                        ChampionIconResolver.load(champion)
                ));
            }

            summaries.sort(Comparator.comparingDouble(ChampionSummary::score).reversed());
            return summaries.size() <= limit ? summaries : summaries.subList(0, limit);
        } catch (Exception ex) {
            System.err.println("[RiotStatsService] Failed to use snapshot data, falling back. Reason: " + ex.getMessage());
            return fallback.fetchRecommended(context);
        }
    }

    @Override
    public Optional<ChampionStats> findChampionStats(String championId) {
        StatsSnapshot snapshot = snapshot();
        if (snapshot == null || snapshot.champions() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.champions().get(championId));
    }

    @Override
    public Map<String, ChampionStats> allChampionStats() {
        StatsSnapshot snapshot = snapshot();
        if (snapshot == null || snapshot.champions() == null || snapshot.champions().isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(snapshot.champions());
    }

    private StatsSnapshot snapshot() {
        long modified = snapshotFile.exists() ? snapshotFile.lastModified() : -1L;
        if (cachedSnapshot != null && cachedStamp == modified) {
            return cachedSnapshot;
        }
        try {
            cachedSnapshot = new SnapshotStore(snapshotFile).load();
            cachedStamp = modified;
        } catch (IOException e) {
            System.err.println("[RiotStatsService] Could not load snapshot: " + e.getMessage());
            cachedSnapshot = null;
        }
        return cachedSnapshot;
    }

    private Set<String> excludedChampions(RecommendationContext context) {
        if (context == null) return Set.of();
        Set<String> excluded = new HashSet<>();
        context.allySelections().forEach(sel -> excluded.add(sel.champion()));
        context.enemySelections().forEach(sel -> excluded.add(sel.champion()));
        excluded.addAll(context.bannedChampions());
        return excluded;
    }

    private double synergyScore(ChampionStats stats, RecommendationContext context) {
        if (context == null) return Double.NaN;
        Role role = context.targetRole();
        List<String> partners = context.allySelections().stream()
                .filter(sel -> role.pairsWith(sel.role()))
                .map(SlotSelection::champion)
                .toList();
        return pairAverage(stats.synergy(), partners);
    }

    private double counterScore(ChampionStats stats, RecommendationContext context) {
        if (context == null) return Double.NaN;
        Role role = context.targetRole();
        List<String> opponents = context.enemySelections().stream()
                .filter(sel -> role.contests(sel.role()))
                .map(SlotSelection::champion)
                .toList();
        return pairAverage(stats.counters(), opponents);
    }

    private double pairAverage(java.util.Map<String, WinPlay> data, List<String> opponents) {
        if (opponents == null || opponents.isEmpty()) {
            return Double.NaN;
        }
        double total = 0.0;
        int count = 0;
        for (String name : opponents) {
            WinPlay wp = data.get(name);
            if (wp != null && wp.getGames() >= MIN_PAIR_GAMES) {
                total += wp.winRate();
                count++;
            }
        }
        return count > 0 ? clamp(total / count) : Double.NaN;
    }

    private double clamp(double value) {
        return Math.max(0.35, Math.min(0.70, value));
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

    private Platform parsePlatform(String tag) {
        if (tag == null || tag.isBlank()) return Platform.EUROPE_WEST;
        String normalized = tag.trim().toUpperCase().replace("-", "_");
        try {
            return Platform.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            System.err.println("[RiotStatsService] Unknown platform '" + tag + "', falling back to EUW.");
            return Platform.EUROPE_WEST;
        }
    }
}
