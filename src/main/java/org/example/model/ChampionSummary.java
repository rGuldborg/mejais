package org.example.model;

import javafx.scene.image.Image;

public record ChampionSummary(
        String id,
        String name,
        Tier opTier,
        Tier synTier,
        Tier coTier,
        double score,
        Image icon
) { }
