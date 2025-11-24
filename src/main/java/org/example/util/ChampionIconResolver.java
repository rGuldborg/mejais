package org.example.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChampionIconResolver {
    private static final String BASE = "/org/example/images/champSquare/";
    private static final Image PLACEHOLDER = new Image(
            Objects.requireNonNull(ChampionIconResolver.class.getResourceAsStream("/org/example/images/placeholder.png"))
    );
    private static final Image ALLY_PLACEHOLDER = loadImageOrFallback(
            "/org/example/images/placeholder-ally.png",
            PLACEHOLDER
    );
    private static final Image ENEMY_PLACEHOLDER = loadImageOrFallback(
            "/org/example/images/placeholder-enemy.png",
            PLACEHOLDER
    );
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> NAME_ALIASES = Map.ofEntries(
            Map.entry("Renata", "RenataGlasc"),
            Map.entry("Renata Glasc", "RenataGlasc"),
            Map.entry("Nunu", "NunuWillump"),
            Map.entry("MonkeyKing", "Wukong"),
            Map.entry("Mel", "Mel"),
            Map.entry("Yunara", "Yunara")
    );

    static {
        ImageIO.scanForPlugins();
    }

    private ChampionIconResolver() { }

    public static Image load(String championName) {
        if (championName == null || championName.isBlank()) {
            return PLACEHOLDER;
        }
        String canonical = ChampionNames.canonicalName(championName);
        if (canonical == null || canonical.isBlank()) {
            canonical = championName;
        }
        String key = canonical;
        return CACHE.computeIfAbsent(key, ChampionIconResolver::resolveImage);
    }

    public static Image placeholder() {
        return PLACEHOLDER;
    }

    public static Image allyPlaceholder() {
        return ALLY_PLACEHOLDER;
    }

    public static Image enemyPlaceholder() {
        return ENEMY_PLACEHOLDER;
    }

    private static Image resolveImage(String championName) {
        for (String candidate : buildCandidates(championName)) {
            String resource = BASE + candidate;
            if (candidate.endsWith(".webp")) {
                Image webp = loadWebp(resource);
                if (webp != null) {
                    return webp;
                }
            } else {
                Image img = loadStandard(resource);
                if (img != null) {
                    return img;
                }
            }
        }
        return PLACEHOLDER;
    }

    private static Image loadImageOrFallback(String resource, Image fallback) {
        try (InputStream stream = ChampionIconResolver.class.getResourceAsStream(resource)) {
            if (stream == null) return fallback;
            return new Image(stream);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Image loadStandard(String resource) {
        try (InputStream stream = ChampionIconResolver.class.getResourceAsStream(resource)) {
            if (stream == null) return null;
            return new Image(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Image loadWebp(String resource) {
        try (InputStream stream = ChampionIconResolver.class.getResourceAsStream(resource)) {
            if (stream == null) return null;
            BufferedImage buffered = ImageIO.read(stream);
            if (buffered == null) return null;
            return SwingFXUtils.toFXImage(buffered, null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static List<String> buildCandidates(String championName) {
        String canonical = ChampionNames.canonicalName(championName);
        if (canonical == null || canonical.isBlank()) {
            canonical = championName;
        }
        String alias = NAME_ALIASES.getOrDefault(canonical, canonical);
        Set<String> bases = new LinkedHashSet<>();
        addBaseVariants(canonical, bases);
        if (!alias.equals(canonical)) {
            addBaseVariants(alias, bases);
        }
        List<String> candidates = new ArrayList<>();
        for (String base : bases) {
            if (base == null || base.isBlank()) continue;
            candidates.add(base + "Square.png");
            candidates.add(base + "Square.webp");
        }
        return candidates;
    }

    private static void addBaseVariants(String source, Set<String> collector) {
        if (source == null || source.isBlank()) {
            return;
        }
        collector.add(source);
        collector.add(source.replace(" ", ""));
        collector.add(source.replace(" ", "_"));
        collector.add(source.replace("'", "").replace(" ", ""));
        collector.add(source.replace("'", "").replace(" ", "_"));
        collector.add(source.replaceAll("[^A-Za-z0-9]", ""));
    }
}
