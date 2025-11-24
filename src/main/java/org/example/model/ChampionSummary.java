package org.example.model;

import javafx.scene.image.Image;

import java.util.List;

public record ChampionSummary(
        String id,
        String name,
        Tier opTier,
        Tier synTier,
        Tier coTier,
        double score,
        Image icon,
        Role preferredRole,
        double opWinRate,
        double synWinRate,
        double coWinRate,
        List<PairWinRate> synergyPartners,
        List<PairWinRate> counterOpponents
) { }
