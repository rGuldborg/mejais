package org.example.service;

import org.example.model.ChampionStats;
import org.example.model.ChampionSummary;
import org.example.model.PairWinRate;
import org.example.model.RecommendationContext;
import org.example.model.Role;
import org.example.model.SlotSelection;
import org.example.model.Tier;
import org.example.util.ChampionIconResolver;
import org.example.util.ChampionNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MockStatsService implements StatsService {
    private static final double OP_WEIGHT = 0.5;
    private static final double SYN_WEIGHT = 0.25;
    private static final double COUNTER_WEIGHT = 0.25;
    private static final Map<String, Double> WIN_RATES = initWinRates();
    private final Map<String, ChampionStats> cachedStats = new LinkedHashMap<>();

    @Override
    public List<ChampionSummary> fetchRecommended(RecommendationContext context) {
        List<ChampionSummary> result = new ArrayList<>();
        Set<String> excluded = excludedChampions(context);
        int limit = context == null ? 10 : context.limit();

        for (String id : WIN_RATES.keySet()) {
            if (excluded.contains(id)) continue;
            double opWr = clamp(WIN_RATES.getOrDefault(id, 0.48));
            PairResult synResult = simulateSynergy(opWr, id, context);
            PairResult coResult = simulateCounters(opWr, id, context);
            double synWr = synResult.winRate();
            double coWr = coResult.winRate();

            Tier opTier = Tier.fromWinRate(opWr);
            Tier synTier = Tier.fromWinRate(synWr, true);
            Tier coTier = Tier.fromWinRate(coWr, true);

            double score = weightedScore(opTier, synTier, coTier);
            Role role = sampleRole(id);
            result.add(new ChampionSummary(
                    id,
                    id,
                    opTier,
                    synTier,
                    coTier,
                    score,
                    ChampionIconResolver.load(id),
                    role,
                    opWr,
                    synWr,
                    coWr,
                    synResult.pairs(),
                    coResult.pairs()
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
        String canonical = ChampionNames.canonicalName(championId);
        return Optional.of(cachedStats.computeIfAbsent(canonical, this::generateStats));
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

    private Role sampleRole(String championId) {
        int hash = Math.abs(championId.hashCode());
        return switch (hash % 5) {
            case 0 -> Role.TOP;
            case 1 -> Role.JUNGLE;
            case 2 -> Role.MID;
            case 3 -> Role.BOTTOM;
            default -> Role.SUPPORT;
        };
    }

    private PairResult simulateSynergy(double base, String champId, RecommendationContext context) {
        if (context == null) {
            return PairResult.EMPTY;
        }
        List<SlotSelection> allies = context.allyPerspective()
                ? context.allySelections()
                : context.enemySelections();
        if (allies.isEmpty()) {
            return PairResult.EMPTY;
        }
        return simulatePairs(base, champId, allies, true);
    }

    private PairResult simulateCounters(double base, String champId, RecommendationContext context) {
        if (context == null) {
            return PairResult.EMPTY;
        }
        List<SlotSelection> opponents = context.allyPerspective()
                ? context.enemySelections()
                : context.allySelections();
        if (opponents.isEmpty()) {
            return PairResult.EMPTY;
        }
        return simulatePairs(base, champId, opponents, false);
    }

    private PairResult simulatePairs(double base,
                                     String champId,
                                     List<SlotSelection> selections,
                                     boolean allies) {
        double total = 0;
        int count = 0;
        List<PairWinRate> pairs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (SlotSelection selection : selections) {
            String canonical = ChampionNames.canonicalName(selection.champion());
            if (canonical == null || canonical.isBlank() || !seen.add(canonical)) {
                continue;
            }
            double delta = seededDelta(champId, canonical, allies ? 0.004 : 0.005);
            double value = clamp(allies ? base + delta : base - delta);
            total += value;
            count++;
            pairs.add(new PairWinRate(ChampionNames.displayName(canonical), value));
        }
        double average = count > 0 ? clamp(total / count) : Double.NaN;
        return count > 0 ? new PairResult(average, List.copyOf(pairs)) : PairResult.EMPTY;
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
                .map(ChampionNames::canonicalName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static Map<String, Double> initWinRates() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("Aatrox", 0.54);
        rates.put("Ahri", 0.52);
        rates.put("Darius", 0.50);
        rates.put("Garen", 0.49);
        rates.put("Vayne", 0.55);
        rates.put("Anivia", 0.53);
        for (String champion : ChampionNames.canonicalNames()) {
            rates.putIfAbsent(champion, autoWinRate(champion));
        }
        return Collections.unmodifiableMap(rates);
    }

    private static double autoWinRate(String champion) {
        int hash = Math.abs(champion.hashCode());
        double offset = ((hash % 21) - 10) * 0.002;
        double base = 0.5 + offset;
        return Math.max(0.45, Math.min(0.57, base));
    }

    private static List<String> canonicalList(String... names) {
        List<String> canonicals = new ArrayList<>(names.length);
        for (String name : names) {
            canonicals.add(ChampionNames.canonicalName(name));
        }
        return List.copyOf(canonicals);
    }

    private static final List<String> SAMPLE_SYNERGY = canonicalList("Ahri", "Thresh", "Jarvan IV", "Lux");
    private static final List<String> SAMPLE_COUNTERS = canonicalList("Yorick", "Camille", "Fizz", "Lucian", "Vayne");
    private record PairResult(double winRate, List<PairWinRate> pairs) {
        private static final PairResult EMPTY = new PairResult(Double.NaN, List.of());
    }
}
