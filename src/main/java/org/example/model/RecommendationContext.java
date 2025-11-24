package org.example.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RecommendationContext(
        List<SlotSelection> allySelections,
        List<SlotSelection> enemySelections,
        List<String> bannedChampions,
        Role targetRole,
        boolean allyPerspective,
        int limit
) {
    private static final int DEFAULT_LIMIT = 20;

    public RecommendationContext {
        allySelections = sanitize(allySelections);
        enemySelections = sanitize(enemySelections);
        bannedChampions = normalize(bannedChampions);
        targetRole = targetRole == null ? Role.UNKNOWN : targetRole;
        limit = limit <= 0 ? DEFAULT_LIMIT : limit;
    }

    private static List<SlotSelection> sanitize(List<SlotSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        List<SlotSelection> cleaned = new ArrayList<>();
        for (SlotSelection sel : selections) {
            if (sel == null) continue;
            String champ = sel.champion();
            if (champ == null || champ.isBlank()) continue;
            Role role = sel.role() == null ? Role.UNKNOWN : sel.role();
            cleaned.add(new SlotSelection(champ.trim(), role));
        }
        return cleaned.isEmpty() ? List.of() : List.copyOf(cleaned);
    }

    private static List<String> normalize(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : input) {
            if (value == null) continue;
            String cleaned = value.trim();
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
            }
        }
        return unique.isEmpty() ? List.of() : List.copyOf(unique);
    }
}
