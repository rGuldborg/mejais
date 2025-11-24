package org.example.controller;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.example.ThemeManager;
import org.example.model.ChampionStats;
import org.example.model.Role;
import org.example.service.MockStatsService;
import org.example.service.RiotStatsService;
import org.example.service.StatsService;
import org.example.util.ChampionIconResolver;
import org.example.util.ChampionNames;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ChampionsController {
    private static final int MIN_MATCHUP_GAMES = 5;
    private static final double ROLE_SHARE_THRESHOLD = 0.02; // 2% of a champion's games
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("+0.0;-0.0");

    @FXML private TilePane championsGrid;
    @FXML private Label championNameLabel;
    @FXML private ImageView championImageView;
    @FXML private Label winRateLabel;
    @FXML private HBox detailRoleBar;
    @FXML private VBox favorableMatchups;
    @FXML private VBox challengingMatchups;

    private final List<ChampionInfo> championInfos = new ArrayList<>();
    private final Map<String, ChampionInfo> championIndex = new HashMap<>();
    private final Map<String, ChampionStats> statsIndex = new LinkedHashMap<>();
    private final Map<ThemeManager.Theme, Map<Role, Image>> roleIconCache = new java.util.EnumMap<>(ThemeManager.Theme.class);

    private StatsService statsService;
    private ChampionInfo currentChampion;
    private Role activeRole = Role.UNKNOWN;
    private List<Role> availableRoles = List.of(Role.UNKNOWN);
    private ChampionStats currentStats;
    private double currentWinRate;
    private final Map<String, List<Role>> roleCache = new HashMap<>();
    private TextField boundSearchField;
    private ChangeListener<String> searchListener;
    private ChangeListener<Boolean> focusListener;
    private final ContextMenu suggestionPopup = new ContextMenu();
    private final Consumer<ThemeManager.Theme> themeListener = theme -> Platform.runLater(this::refreshRoleIcons);

    public ChampionsController() {
        suggestionPopup.getStyleClass().add("search-suggestions");
    }
    private Runnable showViewRequest;

    @FXML
    public void initialize() {
        ThemeManager.addThemeChangeListener(themeListener);
        statsService = initStatsService();
        loadChampionInfos();
        populateGrid();
        if (!championInfos.isEmpty()) {
            showChampionDetails(championInfos.get(0));
        } else {
            championNameLabel.setText("No champions found");
        }
    }

    private StatsService initStatsService() {
        String apiKey = System.getenv("RIOT_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            String platformTag = System.getenv().getOrDefault("RIOT_PLATFORM", "EUROPE_WEST");
            return new RiotStatsService(apiKey, platformTag);
        }
        return new MockStatsService();
    }

    public void bindSearchField(TextField searchField) {
        detachSearchField();
        if (searchField == null) {
            return;
        }
        boundSearchField = searchField;
        searchListener = (obs, oldVal, text) -> updateSuggestions(text);
        searchField.textProperty().addListener(searchListener);
        searchField.setOnAction(event -> acceptFirstSuggestion());
        focusListener = (obs, oldFocused, focused) -> {
            if (!focused) {
                suggestionPopup.hide();
            }
        };
        searchField.focusedProperty().addListener(focusListener);
    }

    public void detachSearchField() {
        if (boundSearchField != null && searchListener != null) {
            boundSearchField.textProperty().removeListener(searchListener);
            boundSearchField.setOnAction(null);
            if (focusListener != null) {
                boundSearchField.focusedProperty().removeListener(focusListener);
            }
        }
        searchListener = null;
        focusListener = null;
        boundSearchField = null;
        suggestionPopup.hide();
    }

    private void updateSuggestions(String text) {
        suggestionPopup.getItems().clear();
        if (boundSearchField == null || text == null || text.isBlank()) {
            suggestionPopup.hide();
            return;
        }
        String lowered = text.toLowerCase();
        List<String> matches = championInfos.stream()
                .map(ChampionInfo::displayName)
                .filter(name -> name.toLowerCase().contains(lowered))
                .sorted()
                .limit(5)
                .toList();
        if (matches.isEmpty()) {
            suggestionPopup.hide();
            return;
        }
        matches.forEach(match -> {
            HBox row = suggestionRow(match);
            CustomMenuItem item = new CustomMenuItem(row, true);
            item.setOnAction(e -> selectSuggestion(match));
            suggestionPopup.getItems().add(item);
        });
        if (!suggestionPopup.isShowing()) {
            suggestionPopup.show(boundSearchField, Side.BOTTOM, 0, 0);
        }
    }

    private void acceptFirstSuggestion() {
        if (suggestionPopup.getItems().isEmpty()) {
            return;
        }
        MenuItem first = suggestionPopup.getItems().get(0);
        if (first instanceof CustomMenuItem custom && custom.getContent() instanceof Label label) {
            selectSuggestion(label.getText());
        }
    }

    private void selectSuggestion(String name) {
        ChampionInfo info = championIndex.get(name);
        if (info != null) {
            showChampionDetails(info);
            requestShowView();
        }
        if (boundSearchField != null) {
            boundSearchField.clear();
            if (boundSearchField.getParent() != null) {
                boundSearchField.getParent().requestFocus();
            }
        }
        suggestionPopup.hide();
    }

    private HBox suggestionRow(String displayName) {
        ChampionInfo info = championIndex.get(displayName);
        String canonical = info != null ? info.id() : ChampionNames.canonicalName(displayName);
        String fallback = canonical != null ? canonical : displayName;
        HBox box = new HBox(8);
        box.getStyleClass().add("search-suggestion-item");
        ImageView icon = new ImageView(ChampionIconResolver.load(fallback));
        icon.setFitWidth(24);
        icon.setFitHeight(24);
        icon.setPreserveRatio(true);
        Label label = new Label(displayName);
        label.getStyleClass().add("search-suggestion-label");
        box.getChildren().addAll(icon, label);
        return box;
    }

    public void setShowViewRequest(Runnable showViewRequest) {
        this.showViewRequest = showViewRequest;
    }

    private void requestShowView() {
        if (showViewRequest != null) {
            showViewRequest.run();
        }
    }

    private void loadChampionInfos() {
        championInfos.clear();
        championIndex.clear();
        statsIndex.clear();

        Map<String, ChampionStats> championStats = statsService.allChampionStats();
        if (championStats != null && !championStats.isEmpty()) {
            championStats.entrySet().stream()
                    .sorted(Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> {
                        String id = entry.getKey();
                        ChampionInfo info = new ChampionInfo(id, ChampionNames.displayName(id));
                        championInfos.add(info);
                        championIndex.put(info.displayName(), info);
                        statsIndex.put(id, entry.getValue());
                    });
            return;
        }
        loadChampionInfosFromAssets();
    }

    private void loadChampionInfosFromAssets() {
        var directoryUrl = getClass().getResource("/org/example/images/champSquare");
        if (directoryUrl == null) {
            System.err.println("champSquare directory not found on classpath.");
            return;
        }
        try {
            Path dirPath = Paths.get(directoryUrl.toURI());
            try (Stream<Path> stream = Files.list(dirPath)) {
                stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> {
                            String base = path.getFileName().toString();
                            String canonical = extractCanonical(base);
                            String display = ChampionNames.displayName(canonical);
                            ChampionInfo info = new ChampionInfo(canonical, display);
                            championInfos.add(info);
                            championIndex.put(display, info);
                        });
            }
        } catch (URISyntaxException | IOException e) {
            System.err.println("Failed to read champion assets:");
            e.printStackTrace();
        }
    }

    private void populateGrid() {
        championsGrid.getChildren().clear();
        for (ChampionInfo champion : championInfos) {
            championsGrid.getChildren().add(createChampionCard(champion));
        }
    }

    private VBox createChampionCard(ChampionInfo champion) {
        ImageView avatar = new ImageView();
        avatar.setFitWidth(72);
        avatar.setFitHeight(72);
        avatar.setPreserveRatio(true);
        avatar.setSmooth(true);
        avatar.setImage(ChampionIconResolver.load(champion.id()));

        Label name = new Label(champion.displayName());
        name.getStyleClass().add("champion-card-name");

        VBox box = new VBox(6, avatar, name);
        box.getStyleClass().add("champion-card");
        box.setOnMouseClicked(event -> showChampionDetails(champion));
        return box;
    }

    private void showChampionDetails(ChampionInfo champion) {
        currentChampion = champion;
        championNameLabel.setText(champion.displayName());
        championImageView.setImage(ChampionIconResolver.load(champion.id()));

        currentStats = null;
        currentWinRate = 0.0;
        resetWinRateStyling();

        Optional<ChampionStats> statsOpt = Optional.ofNullable(statsIndex.get(champion.id()));
        if (statsOpt.isEmpty()) {
            statsOpt = statsService.findChampionStats(champion.id());
            statsOpt.ifPresent(stats -> statsIndex.putIfAbsent(champion.id(), stats));
        }
        if (statsOpt.isEmpty()) {
            winRateLabel.setText("Stats unavailable");
            detailRoleBar.getChildren().clear();
            displayMatchupPlaceholder("No matchup data available.");
            return;
        }

        ChampionStats stats = statsOpt.get();
        currentStats = stats;
        double winRate = stats.winRate();
        currentWinRate = winRate;
        winRateLabel.setText(String.format("Win Rate: %.1f%%", winRate * 100));
        applyWinRateStyling(winRate);

        availableRoles = resolveRolesFromStats(stats);
        activeRole = availableRoles.get(0);
        roleCache.put(champion.id(), availableRoles);
        renderRoleChips();
        renderMatchups(stats, winRate);
    }

    private List<Role> resolveRolesFromStats(ChampionStats stats) {
        Map<String, Integer> counts = stats.roleCounts();
        if (counts == null || counts.isEmpty()) {
            return List.of(Role.UNKNOWN);
        }
        int totalGames = Math.max(stats.games(), counts.values().stream().mapToInt(Integer::intValue).sum());
        int minimumRoleGames = totalGames > 0 ? (int) Math.ceil(totalGames * ROLE_SHARE_THRESHOLD) : 0;

        List<RoleShare> sortedRoles = counts.entrySet().stream()
                .sorted(Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> new RoleShare(mapRole(entry.getKey()), entry.getValue()))
                .filter(share -> share.role() != Role.UNKNOWN)
                .toList();

        List<Role> roles = sortedRoles.stream()
                .filter(share -> share.count() >= minimumRoleGames)
                .map(RoleShare::role)
                .distinct()
                .limit(2)
                .toList();

        if (roles.isEmpty()) {
            roles = sortedRoles.stream()
                    .map(RoleShare::role)
                    .distinct()
                    .limit(Math.min(2, sortedRoles.size()))
                    .toList();
        }

        return roles.isEmpty() ? List.of(Role.UNKNOWN) : roles;
    }

    private Role mapRole(String lane) {
        if (lane == null) return Role.UNKNOWN;
        return switch (lane.toUpperCase()) {
            case "TOP" -> Role.TOP;
            case "JUNGLE" -> Role.JUNGLE;
            case "MID", "MIDDLE" -> Role.MID;
            case "ADC", "BOTTOM", "BOT" -> Role.BOTTOM;
            case "SUPPORT", "UTILITY" -> Role.SUPPORT;
            default -> Role.UNKNOWN;
        };
    }

    private List<Role> rolesForChampion(String championName) {
        String canonical = ChampionNames.canonicalName(championName);
        return roleCache.computeIfAbsent(canonical, name -> {
            ChampionStats stats = statsIndex.get(name);
            if (stats == null) {
                stats = statsService.findChampionStats(name).orElse(null);
                if (stats != null) {
                    statsIndex.putIfAbsent(name, stats);
                }
            }
            if (stats == null) {
                return List.of();
            }
            return resolveRolesFromStats(stats);
        });
    }

    private boolean shouldIncludeMatchup(String opponent) {
        if (activeRole == Role.UNKNOWN) {
            return true;
        }
        List<Role> roles = rolesForChampion(opponent);
        if (roles.isEmpty()) {
            return false;
        }
        return roles.contains(activeRole);
    }

    private void renderRoleChips() {
        detailRoleBar.getChildren().clear();
        for (Role role : availableRoles) {
            StackPane chip = new StackPane();
            chip.getStyleClass().addAll("role-chip", "detail-role");
            if (role == activeRole) {
                chip.getStyleClass().add("role-chip-active");
            }
            ImageView icon = new ImageView(roleIcon(role));
            icon.setFitWidth(20);
            icon.setFitHeight(20);
            icon.setPreserveRatio(true);
            chip.getChildren().add(icon);
            chip.setOnMouseClicked(event -> {
                if (activeRole != role) {
                    activeRole = role;
                    renderRoleChips();
                    if (currentStats != null) {
                        renderMatchups(currentStats, currentWinRate);
                    }
                }
            });
            detailRoleBar.getChildren().add(chip);
        }
    }

    private void renderMatchups(ChampionStats stats, double winRate) {
        favorableMatchups.getChildren().clear();
        challengingMatchups.getChildren().clear();
        if (stats.counters().isEmpty()) {
            addEmptyMatchupMessage(favorableMatchups, "No matchup data available.");
            addEmptyMatchupMessage(challengingMatchups, "No matchup data available.");
            return;
        }
        List<MatchupRow> rows = stats.counters().entrySet().stream()
                .filter(entry -> shouldIncludeMatchup(entry.getKey()))
                .filter(entry -> entry.getValue().games() >= MIN_MATCHUP_GAMES)
                .map(entry -> new MatchupRow(entry.getKey(), entry.getValue().winRate() - winRate, entry.getValue().games()))
                .toList();
        if (rows.isEmpty()) {
            addEmptyMatchupMessage(favorableMatchups, "No matchup data for this role.");
            addEmptyMatchupMessage(challengingMatchups, "No matchup data for this role.");
            return;
        }

        List<MatchupRow> positives = rows.stream()
                .filter(row -> row.diff() >= 0)
                .sorted(Comparator.comparingDouble(MatchupRow::diff).reversed())
                .limit(10)
                .toList();

        List<MatchupRow> negatives = rows.stream()
                .filter(row -> row.diff() < 0)
                .sorted(Comparator.comparingDouble(MatchupRow::diff))
                .limit(10)
                .toList();

        populateMatchupColumn(favorableMatchups, positives, "No favorable matchups.");
        populateMatchupColumn(challengingMatchups, negatives, "No challenging matchups.");
    }

    private void populateMatchupColumn(VBox container, List<MatchupRow> rows, String emptyMessage) {
        container.getChildren().clear();
        if (rows.isEmpty()) {
            addEmptyMatchupMessage(container, emptyMessage);
        } else {
            rows.forEach(row -> container.getChildren().add(buildMatchupRow(row)));
        }
    }

    private void addEmptyMatchupMessage(VBox container, String message) {
        Label placeholder = new Label(message);
        placeholder.getStyleClass().add("matchup-empty");
        container.getChildren().add(placeholder);
    }

    private void displayMatchupPlaceholder(String message) {
        if (favorableMatchups != null) {
            favorableMatchups.getChildren().clear();
            addEmptyMatchupMessage(favorableMatchups, message);
        }
        if (challengingMatchups != null) {
            challengingMatchups.getChildren().clear();
            addEmptyMatchupMessage(challengingMatchups, message);
        }
    }

    private HBox buildMatchupRow(MatchupRow row) {
        HBox container = new HBox(10);
        container.getStyleClass().add("matchup-row");
        container.setAlignment(javafx.geometry.Pos.CENTER);
        ImageView icon = new ImageView(ChampionIconResolver.load(row.enemy()));
        icon.setFitWidth(28);
        icon.setFitHeight(28);
        icon.setPreserveRatio(true);
        Label name = new Label(ChampionNames.displayName(row.enemy()));
        name.getStyleClass().add("matchup-name");
        Label diff = new Label(PERCENT_FORMAT.format(row.diff() * 100) + "%");
        diff.getStyleClass().add(row.diff() >= 0 ? "matchup-positive" : "matchup-negative");
        container.getChildren().addAll(icon, name, diff);
        return container;
    }

    private Image roleIcon(Role role) {
        ThemeManager.Theme theme = ThemeManager.currentTheme();
        Map<Role, Image> themedCache = roleIconCache.computeIfAbsent(theme, t -> new java.util.EnumMap<>(Role.class));
        return themedCache.computeIfAbsent(role, r -> {
            String folder = theme == ThemeManager.Theme.DARK ? "dark" : "light";
            String path = "/org/example/images/roles/" + folder + "/" + r.iconFile();
            try {
                return new Image(Objects.requireNonNull(getClass().getResourceAsStream(path)));
            } catch (Exception ex) {
                return ChampionIconResolver.placeholder();
            }
        });
    }

    private String extractCanonical(String rawFileName) {
        String withoutExt = rawFileName;
        int squareIndex = withoutExt.indexOf("Square");
        if (squareIndex >= 0) {
            withoutExt = withoutExt.substring(0, squareIndex);
        }
        String decoded = URLDecoder.decode(withoutExt, StandardCharsets.UTF_8);
        decoded = decoded.replaceAll("\\s+", "");
        return ChampionNames.canonicalName(decoded);
    }

    private record ChampionInfo(String id, String displayName) { }

    private record MatchupRow(String enemy, double diff, int games) { }

    private record RoleShare(Role role, int count) { }

    private void resetWinRateStyling() {
        winRateLabel.getStyleClass().removeAll("winrate-positive", "winrate-negative");
    }

    private void applyWinRateStyling(double winRate) {
        resetWinRateStyling();
        if (winRate >= 0.5) {
            winRateLabel.getStyleClass().add("winrate-positive");
        } else {
            winRateLabel.getStyleClass().add("winrate-negative");
        }
    }

    private void refreshRoleIcons() {
        roleIconCache.clear();
        if (detailRoleBar == null || detailRoleBar.getChildren().isEmpty()) {
            return;
        }
        renderRoleChips();
    }
}
