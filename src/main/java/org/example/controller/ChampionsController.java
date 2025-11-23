package org.example.controller;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.example.model.ChampionStats;
import org.example.model.Role;
import org.example.service.MockStatsService;
import org.example.service.RiotStatsService;
import org.example.service.StatsService;
import org.example.util.ChampionIconResolver;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChampionsController {
    private static final int MIN_MATCHUP_GAMES = 5;
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("+0.0;-0.0");

    @FXML private BorderPane rootPane;
    @FXML private TilePane championsGrid;
    @FXML private Label championNameLabel;
    @FXML private ImageView championImageView;
    @FXML private Label winRateLabel;
    @FXML private HBox detailRoleBar;
    @FXML private VBox matchupList;

    private final List<ChampionInfo> championInfos = new ArrayList<>();
    private final Map<String, ChampionInfo> championIndex = new HashMap<>();
    private final Map<String, ChampionStats> statsIndex = new LinkedHashMap<>();
    private final Map<Role, Image> roleIconCache = new java.util.EnumMap<>(Role.class);

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

    @FXML
    public void initialize() {
        statsService = initStatsService();
        loadChampionInfos();
        populateGrid();
        if (!championInfos.isEmpty()) {
            showChampionDetails(championInfos.get(0));
        } else {
            championNameLabel.setText("No champions found");
        }
    }

    @FXML
    private void onBackClicked() {
        try {
            detachSearchField();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/fxml/game-view.fxml"));
            Node gameView = loader.load();
            StackPane contentArea = (StackPane) rootPane.getScene().lookup("#contentArea");
            if (contentArea != null) {
                contentArea.getChildren().setAll(gameView);
            }
        } catch (IOException ex) {
            System.err.println("Unable to navigate back to game view");
            ex.printStackTrace();
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
                .map(ChampionInfo::name)
                .filter(name -> name.toLowerCase().contains(lowered))
                .sorted()
                .limit(5)
                .toList();
        if (matches.isEmpty()) {
            suggestionPopup.hide();
            return;
        }
        matches.forEach(match -> {
            Label label = new Label(match);
            label.getStyleClass().add("search-suggestion-item");
            CustomMenuItem item = new CustomMenuItem(label, true);
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
        }
        if (boundSearchField != null) {
            boundSearchField.clear();
            if (boundSearchField.getParent() != null) {
                boundSearchField.getParent().requestFocus();
            }
        }
        suggestionPopup.hide();
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
                        ChampionInfo info = new ChampionInfo(entry.getKey());
                        championInfos.add(info);
                        championIndex.put(entry.getKey(), info);
                        statsIndex.put(entry.getKey(), entry.getValue());
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
                            String name = beautifyName(path.getFileName().toString());
                            ChampionInfo info = new ChampionInfo(name);
                            championInfos.add(info);
                            championIndex.put(name, info);
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
        avatar.setImage(ChampionIconResolver.load(champion.name()));

        Label name = new Label(champion.name());
        name.getStyleClass().add("champion-card-name");

        VBox box = new VBox(6, avatar, name);
        box.getStyleClass().add("champion-card");
        box.setOnMouseClicked(event -> showChampionDetails(champion));
        return box;
    }

    private void showChampionDetails(ChampionInfo champion) {
        currentChampion = champion;
        championNameLabel.setText(champion.name());
        championImageView.setImage(ChampionIconResolver.load(champion.name()));

        currentStats = null;
        currentWinRate = 0.0;

        Optional<ChampionStats> statsOpt = Optional.ofNullable(statsIndex.get(champion.name()));
        if (statsOpt.isEmpty()) {
            statsOpt = statsService.findChampionStats(champion.name());
            statsOpt.ifPresent(stats -> statsIndex.putIfAbsent(champion.name(), stats));
        }
        if (statsOpt.isEmpty()) {
            winRateLabel.setText("Stats unavailable");
            detailRoleBar.getChildren().clear();
            matchupList.getChildren().setAll(new Label("No matchup data available."));
            return;
        }

        ChampionStats stats = statsOpt.get();
        currentStats = stats;
        double winRate = stats.winRate();
        currentWinRate = winRate;
        winRateLabel.setText(String.format("Win Rate: %.1f%% (%d games)", winRate * 100, stats.games()));

        availableRoles = resolveRolesFromStats(stats);
        activeRole = availableRoles.get(0);
        roleCache.put(champion.name(), availableRoles);
        renderRoleChips();
        renderMatchups(stats, winRate);
    }

    private List<Role> resolveRolesFromStats(ChampionStats stats) {
        Map<String, Integer> counts = stats.roleCounts();
        if (counts == null || counts.isEmpty()) {
            return List.of(Role.UNKNOWN);
        }
        List<Role> roles = counts.entrySet().stream()
                .sorted(Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> mapRole(entry.getKey()))
                .filter(role -> role != Role.UNKNOWN)
                .distinct()
                .limit(2)
                .toList();
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
        return roleCache.computeIfAbsent(championName, name -> {
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
            chip.getStyleClass().addAll("role-chip", "ally");
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
        matchupList.getChildren().clear();
        if (stats.counters().isEmpty()) {
            matchupList.getChildren().add(new Label("No matchup data available."));
            return;
        }
        List<MatchupRow> rows = stats.counters().entrySet().stream()
                .filter(entry -> shouldIncludeMatchup(entry.getKey()))
                .filter(entry -> entry.getValue().games() >= MIN_MATCHUP_GAMES)
                .map(entry -> new MatchupRow(entry.getKey(), entry.getValue().winRate() - winRate, entry.getValue().games()))
                .sorted(Comparator.comparingDouble(MatchupRow::diff))
                .toList();
        if (rows.isEmpty()) {
            matchupList.getChildren().add(new Label("No matchup data for this role."));
            return;
        }
        List<MatchupRow> negatives = rows.stream().limit(3).toList();
        List<MatchupRow> positives = rows.stream()
                .sorted(Comparator.comparingDouble(MatchupRow::diff).reversed())
                .limit(3)
                .toList();
        if (!positives.isEmpty()) {
            matchupList.getChildren().add(new Label("Favorable Matchups"));
            positives.forEach(row -> matchupList.getChildren().add(buildMatchupRow(row)));
        }
        if (!negatives.isEmpty()) {
            matchupList.getChildren().add(new Label("Challenging Matchups"));
            negatives.forEach(row -> matchupList.getChildren().add(buildMatchupRow(row)));
        }
    }

    private HBox buildMatchupRow(MatchupRow row) {
        HBox container = new HBox(10);
        container.getStyleClass().add("matchup-row");
        ImageView icon = new ImageView(ChampionIconResolver.load(row.enemy()));
        icon.setFitWidth(28);
        icon.setFitHeight(28);
        icon.setPreserveRatio(true);
        Label name = new Label(row.enemy());
        Label diff = new Label(PERCENT_FORMAT.format(row.diff() * 100) + "%");
        diff.getStyleClass().add(row.diff() >= 0 ? "matchup-positive" : "matchup-negative");
        Label games = new Label("(" + row.games() + " games)");
        container.getChildren().addAll(icon, name, diff, games);
        return container;
    }

    private Image roleIcon(Role role) {
        return roleIconCache.computeIfAbsent(role, r -> {
            String path = "/org/example/images/roles/" + r.iconFile();
            try {
                return new Image(Objects.requireNonNull(getClass().getResourceAsStream(path)));
            } catch (Exception ex) {
                return ChampionIconResolver.placeholder();
            }
        });
    }

    private String beautifyName(String rawFileName) {
        String withoutExt = rawFileName;
        int squareIndex = withoutExt.indexOf("Square");
        if (squareIndex >= 0) {
            withoutExt = withoutExt.substring(0, squareIndex);
        }
        withoutExt = withoutExt.replace("_", " ");
        String decoded = URLDecoder.decode(withoutExt, StandardCharsets.UTF_8);
        decoded = decoded.replaceAll("\\s+", " ").trim();
        return decoded.isEmpty() ? rawFileName : decoded;
    }

    private record ChampionInfo(String name) { }

    private record MatchupRow(String enemy, double diff, int games) { }
}
