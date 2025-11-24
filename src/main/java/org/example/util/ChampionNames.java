package org.example.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChampionNames {
    private static final Map<String, String> DISPLAY_BY_CANONICAL;
    private static final Map<String, String> CANONICAL_BY_NORMALIZED;
    private static final List<String> CANONICAL_LIST;

    static {
        Map<String, String> display = new LinkedHashMap<>();
        Map<String, String> normalized = new ConcurrentHashMap<>();
        try (InputStream stream = ChampionNames.class.getResourceAsStream("/org/example/data/champion-map.json")) {
            if (stream != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(stream);
                JsonNode data = root.path("data");
                if (data.isObject()) {
                    List<String> ids = new ArrayList<>();
                    data.fieldNames().forEachRemaining(ids::add);
                    ids.sort(String.CASE_INSENSITIVE_ORDER);
                    for (String id : ids) {
                        JsonNode node = data.path(id);
                        String displayName = node.path("name").asText(id);
                        display.put(id, displayName);
                        normalized.putIfAbsent(normalize(id), id);
                        normalized.putIfAbsent(normalize(displayName), id);
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[ChampionNames] Failed to load champion list: " + ex.getMessage());
        }
        registerManual("Zaahen", "Za'ahen", display, normalized, "Zaheen");
        DISPLAY_BY_CANONICAL = Collections.unmodifiableMap(display);
        CANONICAL_LIST = List.copyOf(display.keySet());
        CANONICAL_BY_NORMALIZED = Collections.unmodifiableMap(normalized);
    }

    private ChampionNames() { }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    public static String canonicalName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String normalized = normalize(trimmed);
        return CANONICAL_BY_NORMALIZED.getOrDefault(normalized, trimmed);
    }

    public static String displayName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String canonical = canonicalName(value);
        return DISPLAY_BY_CANONICAL.getOrDefault(canonical, canonical);
    }

    public static List<String> canonicalNames() {
        return CANONICAL_LIST;
    }
    private static void registerManual(String canonical, String displayName, Map<String, String> display, Map<String, String> normalized, String... aliases) {
        display.putIfAbsent(canonical, displayName);
        normalized.putIfAbsent(normalize(canonical), canonical);
        normalized.putIfAbsent(normalize(displayName), canonical);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) continue;
                normalized.putIfAbsent(normalize(alias), canonical);
            }
        }
    }
}
