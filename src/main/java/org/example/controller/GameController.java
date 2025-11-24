package org.example.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.example.ThemeManager;
import org.example.model.ChampionSummary;
import org.example.model.PairWinRate;
import org.example.model.RecommendationContext;
import org.example.model.Role;
import org.example.model.SlotSelection;
import org.example.model.Tier;
import org.example.service.MockStatsService;
import org.example.service.RiotStatsService;
import org.example.service.StatsService;
import org.example.service.lcu.ChampSelectSnapshot;
import org.example.service.lcu.LeagueClientChampSelectWatcher;
import org.example.util.ChampionIconResolver;
import org.example.util.ChampionNames;
import org.example.util.RoleFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GameController {
    private static final int SLOT_COUNT = 5;
    private static final int RECOMMENDATION_LIMIT = Integer.MAX_VALUE;
    private static final Role[] ROLE_ORDER = {Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT};
    private static final double STRONG_PAIR_THRESHOLD = 0.05;
    private static final int MAX_PAIR_LINES = 2;
    private static final String TOOLTIP_PROPERTY_KEY = "champTooltipRef";

    @FXML private ImageView allyBan1, allyBan2, allyBan3, allyBan4, allyBan5;
    @FXML private ImageView enemyBan1, enemyBan2, enemyBan3, enemyBan4, enemyBan5;
    @FXML private ImageView allyPick1, allyPick2, allyPick3, allyPick4, allyPick5;
    @FXML private ImageView enemyPick1, enemyPick2, enemyPick3, enemyPick4, enemyPick5;
    @FXML private HBox allyRoleRow1, allyRoleRow2, allyRoleRow3, allyRoleRow4, allyRoleRow5;
    @FXML private HBox enemyRoleRow1, enemyRoleRow2, enemyRoleRow3, enemyRoleRow4, enemyRoleRow5;
    @FXML private TableView<ChampionSummary> recommendedTable;
    @FXML private TableColumn<ChampionSummary, String> championCol;
    @FXML private TableColumn<ChampionSummary, String> opCol;
    @FXML private TableColumn<ChampionSummary, String> synCol;
    @FXML private TableColumn<ChampionSummary, String> coCol;
    @FXML private TableColumn<ChampionSummary, Number> scoreCol;
    @FXML private Label selectionStatusLabel;
    @FXML private TextField championFilterField;
    @FXML private HBox roleFilterBar;
    @FXML private VBox firstPickPrompt;
    @FXML private Button finishBansButton;

    private final List<String> allyBans = createSlotList();
    private final List<String> enemyBans = createSlotList();
    private final List<String> allyPicks = createSlotList();
    private final List<String> enemyPicks = createSlotList();
    private final List<Role> allyPickRoles = new ArrayList<>(List.of(Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT));
    private final List<Role> enemyPickRoles = new ArrayList<>(List.of(Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT));
    private final List<Slot> slots = new ArrayList<>();
    private final List<Slot> banSlots = new ArrayList<>();
    private final DropShadow activeEffect = new DropShadow(18, Color.web("#73c0ff"));
    private final Map<ThemeManager.Theme, Map<Role, Image>> roleIconCache = new EnumMap<>(ThemeManager.Theme.class);
    private final Map<String, Image> tierBadgeCache = new java.util.HashMap<>();
    private DraftPhase draftPhase = DraftPhase.SELECT_FIRST_PICK;
    private Side firstPickSide;
    private List<Slot> pickOrder = List.of();
    private int pickIndex;
    private final List<Slot> unlockedSlots = new ArrayList<>();
    private boolean liveMirrorActive;
    private boolean watcherAttached;

    private StatsService statsService;
    private ObservableList<ChampionSummary> tableData;
    private FilteredList<ChampionSummary> filteredTableData;
    private RoleFilter activeRoleFilter = RoleFilter.FLEX;
    private Slot activeSlot;
    private final Consumer<ThemeManager.Theme> themeListener = theme -> Platform.runLater(this::refreshRoleIcons);
    private final LeagueClientChampSelectWatcher clientWatcher = new LeagueClientChampSelectWatcher();
    private final Consumer<ChampSelectSnapshot> clientSnapshotConsumer =
            snapshot -> Platform.runLater(() -> handleClientSnapshot(snapshot));

    private enum SlotType {
        ALLY_BAN(true), ENEMY_BAN(false), ALLY_PICK(true), ENEMY_PICK(false);

        private final boolean allySide;

        SlotType(boolean allySide) {
            this.allySide = allySide;
        }

        boolean isAlly() {
            return allySide;
        }
    }

    private enum Side {
        ALLY, ENEMY
    }

    private enum DraftPhase {
        SELECT_FIRST_PICK,
        BAN_PHASE,
        PICK_PHASE,
        COMPLETE,
        LIVE_MIRROR
    }

    private static class RoleChip {
        final Role role;
        final StackPane container;
        final ImageView iconView;

        RoleChip(Role role, StackPane container, ImageView iconView) {
            this.role = role;
            this.container = container;
            this.iconView = iconView;
        }
    }

    private static class Slot {
        final SlotType type;
        final int index;
        final ImageView pickView;
        final HBox roleRow;
        final List<RoleChip> roleChips = new ArrayList<>();

        Slot(SlotType type, int index, ImageView pickView, HBox roleRow) {
            this.type = type;
            this.index = index;
            this.pickView = pickView;
            this.roleRow = roleRow;
        }
    }

    @FXML
    public void initialize() {
        System.out.println("[GameController] Loaded game-view!");
        ThemeManager.addThemeChangeListener(themeListener);
        statsService = initStatsService();
        configureTable();
        configureSlots();
        configureFirstPickPrompt();
        configureChampionFilter();
        resetBoardToInitial();
        refreshRecommendations();
        recommendedTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                assignSelectedChampion();
            }
        });
        recommendedTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                attachClientWatcher();
            } else {
                detachClientWatcher();
            }
        });
        Platform.runLater(() -> {
            if (recommendedTable.getScene() != null) {
                attachClientWatcher();
            }
        });
    }

    private void configureTable() {
        tableData = FXCollections.observableArrayList();
        filteredTableData = new FilteredList<>(tableData, summary -> true);
        recommendedTable.setItems(filteredTableData);
        recommendedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        championCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::name));
        opCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> data.getValue().opTier().label()));
        synCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> data.getValue().synTier().label()));
        coCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> data.getValue().coTier().label()));
        scoreCol.setCellValueFactory(data -> Bindings.createDoubleBinding(data.getValue()::score));

        championCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView iconView = new ImageView();
            private final Text nameText = new Text();
            private final HBox box = new HBox(8, iconView, nameText);
            {
                iconView.setFitWidth(28);
                iconView.setFitHeight(28);
                iconView.setPreserveRatio(true);
                HBox.setHgrow(nameText, Priority.ALWAYS);
                nameText.getStyleClass().add("recommendation-name");
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    applyTooltip(box, null);
                    setTooltip(null);
                    return;
                }
                ChampionSummary row = getTableView().getItems().get(getIndex());
                iconView.setImage(row.icon());
                nameText.setText(row.name());
                setGraphic(box);
                setAlignment(Pos.CENTER_LEFT);
                setText(null);
                Tooltip tooltip = buildChampionTooltip(row);
                setTooltip(tooltip);
                applyTooltip(box, tooltip);
            }
        });
        opCol.setCellFactory(col -> tierBadgeCell(ChampionSummary::opTier, false, null));
        synCol.setCellFactory(col -> tierBadgeCell(ChampionSummary::synTier, false, null));
        coCol.setCellFactory(col -> tierBadgeCell(ChampionSummary::coTier, false, "co-cell"));
        scoreCol.setCellFactory(col -> new ScoreCell());
        scoreCol.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void applyChampionFilter(String text) {
        if (filteredTableData == null) return;
        String query = text == null ? "" : text.trim().toLowerCase();
        filteredTableData.setPredicate(summary -> {
            if (summary == null) return false;
            boolean matchesSearch = query.isEmpty() || summary.name().toLowerCase().contains(query);
            boolean matchesRole = activeRoleFilter.mappedRole() == null
                    || summary.preferredRole() == null
                    || summary.preferredRole() == activeRoleFilter.mappedRole();
            return matchesSearch && matchesRole;
        });
    }

    private TableCell<ChampionSummary, String> tierBadgeCell(Function<ChampionSummary, Tier> extractor, boolean showLabel, String labelStyle) {
        return new TableCell<>() {
            private final ImageView badge = new ImageView();
            private final Label text = new Label();
            private final HBox box = showLabel ? new HBox(8, badge, text) : new HBox(badge);
            {
                badge.setFitWidth(36);
                badge.setFitHeight(42);
                badge.setPreserveRatio(true);
                box.getStyleClass().add("tier-badge-box");
                if (showLabel) {
                    text.getStyleClass().add(labelStyle != null ? labelStyle : "tier-badge-label");
                }
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                ChampionSummary summary = getTableView().getItems().get(getIndex());
                Tier tier = extractor.apply(summary);
                badge.setImage(tierBadgeFor(tier));
                if (showLabel) {
                    text.setText(tier != null ? tier.label() : "-");
                }
                setGraphic(box);
                setText(null);
            }
        };
    }

    private Tooltip buildChampionTooltip(ChampionSummary summary) {
        if (summary == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        String opLine = describeOpInsight(summary.opWinRate());
        if (opLine != null) {
            lines.add(opLine);
        }
        lines.addAll(describeSynergyInsights(summary.synergyPartners()));
        lines.addAll(describeCounterInsights(summary.counterOpponents()));
        if (lines.isEmpty()) {
            return null;
        }
        Tooltip tooltip = new Tooltip(String.join("\n", lines));
        tooltip.setWrapText(true);
        tooltip.setShowDelay(Duration.millis(500));
        tooltip.setHideDelay(Duration.ZERO);
        tooltip.setShowDuration(Duration.seconds(60));
        return tooltip;
    }

    private String describeOpInsight(double winRate) {
        if (Double.isNaN(winRate) || winRate <= 0) {
            return null;
        }
        String sentiment;
        if (winRate >= 0.53) {
            sentiment = "Generally good right now";
        } else if (winRate <= 0.47) {
            sentiment = "Generally bad right now";
        } else {
            sentiment = "Fair pick in the current patch";
        }
        return "OP: " + formatPercent(winRate) + " — " + sentiment;
    }

    private List<String> describeSynergyInsights(List<PairWinRate> pairs) {
        return buildPairLines(
                pairs,
                "SYN: Pairs great with %s (%s)",
                "SYN: Struggles with %s (%s)"
        );
    }

    private List<String> describeCounterInsights(List<PairWinRate> pairs) {
        return buildPairLines(
                pairs,
                "CO: Counters %s (%s)",
                "CO: %s counters this pick (%s)"
        );
    }

    private List<String> buildPairLines(List<PairWinRate> pairs, String positiveTemplate, String negativeTemplate) {
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        List<PairWinRate> positives = pairs.stream()
                .filter(pair -> pair.delta() >= STRONG_PAIR_THRESHOLD)
                .sorted(Comparator.comparingDouble(PairWinRate::delta).reversed())
                .limit(MAX_PAIR_LINES)
                .collect(Collectors.toList());
        for (PairWinRate pair : positives) {
            lines.add(String.format(positiveTemplate, pair.champion(), formatDelta(pair.delta())));
        }
        List<PairWinRate> negatives = pairs.stream()
                .filter(pair -> pair.delta() <= -STRONG_PAIR_THRESHOLD)
                .sorted(Comparator.comparingDouble(PairWinRate::delta))
                .limit(MAX_PAIR_LINES)
                .collect(Collectors.toList());
        for (PairWinRate pair : negatives) {
            lines.add(String.format(negativeTemplate, pair.champion(), formatDelta(pair.delta())));
        }
        return lines;
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    private String formatDelta(double delta) {
        return String.format("%+.1f%%", delta * 100);
    }

    private void applyTooltip(Node node, Tooltip tooltip) {
        if (node == null) {
            return;
        }
        Object stored = node.getProperties().remove(TOOLTIP_PROPERTY_KEY);
        if (stored instanceof Tooltip oldTooltip) {
            Tooltip.uninstall(node, oldTooltip);
        }
        if (tooltip != null) {
            Tooltip.install(node, tooltip);
            node.getProperties().put(TOOLTIP_PROPERTY_KEY, tooltip);
        }
    }

    private void configureSlots() {
        slots.add(trackBanSlot(new Slot(SlotType.ALLY_BAN, 0, allyBan1, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ALLY_BAN, 1, allyBan2, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ALLY_BAN, 2, allyBan3, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ALLY_BAN, 3, allyBan4, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ALLY_BAN, 4, allyBan5, null)));

        slots.add(trackBanSlot(new Slot(SlotType.ENEMY_BAN, 0, enemyBan1, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ENEMY_BAN, 1, enemyBan2, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ENEMY_BAN, 2, enemyBan3, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ENEMY_BAN, 3, enemyBan4, null)));
        slots.add(trackBanSlot(new Slot(SlotType.ENEMY_BAN, 4, enemyBan5, null)));

        slots.add(new Slot(SlotType.ALLY_PICK, 0, allyPick1, allyRoleRow1));
        slots.add(new Slot(SlotType.ALLY_PICK, 1, allyPick2, allyRoleRow2));
        slots.add(new Slot(SlotType.ALLY_PICK, 2, allyPick3, allyRoleRow3));
        slots.add(new Slot(SlotType.ALLY_PICK, 3, allyPick4, allyRoleRow4));
        slots.add(new Slot(SlotType.ALLY_PICK, 4, allyPick5, allyRoleRow5));

        slots.add(new Slot(SlotType.ENEMY_PICK, 0, enemyPick1, enemyRoleRow1));
        slots.add(new Slot(SlotType.ENEMY_PICK, 1, enemyPick2, enemyRoleRow2));
        slots.add(new Slot(SlotType.ENEMY_PICK, 2, enemyPick3, enemyRoleRow3));
        slots.add(new Slot(SlotType.ENEMY_PICK, 3, enemyPick4, enemyRoleRow4));
        slots.add(new Slot(SlotType.ENEMY_PICK, 4, enemyPick5, enemyRoleRow5));

        slots.forEach(slot -> {
            if (slot.pickView != null) {
                slot.pickView.setCursor(Cursor.HAND);
                slot.pickView.setPickOnBounds(true);
                slot.pickView.setOnMouseClicked(event -> handleSlotClick(slot, event));
            }
            if (slot.roleRow != null) {
                buildRoleChips(slot);
            }
        });
    }

    private Slot trackBanSlot(Slot slot) {
        if (slot.type == SlotType.ALLY_BAN || slot.type == SlotType.ENEMY_BAN) {
            banSlots.add(slot);
        }
        return slot;
    }

    private void configureFirstPickPrompt() {
        if (finishBansButton != null) {
            finishBansButton.setDisable(true);
        }
        showFirstPickPrompt(true);
    }

    private void configureChampionFilter() {
        if (championFilterField == null) {
            return;
        }
        showChampionFilter(false);
        championFilterField.textProperty().addListener((obs, old, text) -> applyChampionFilter(text));
        configureRoleFilterBar();
    }

    private void showFirstPickPrompt(boolean show) {
        if (firstPickPrompt == null) {
            return;
        }
        firstPickPrompt.setManaged(show);
        firstPickPrompt.setVisible(show);
    }

    private void hideFirstPickPrompt() {
        showFirstPickPrompt(false);
    }

    private void showChampionFilter(boolean show) {
        if (championFilterField == null) {
            return;
        }
        championFilterField.setManaged(show);
        championFilterField.setVisible(show);
        if (!show) {
            championFilterField.clear();
            applyChampionFilter("");
        }
        if (roleFilterBar != null) {
            roleFilterBar.setManaged(show);
            roleFilterBar.setVisible(show);
        }
    }

    private void configureRoleFilterBar() {
        if (roleFilterBar == null) return;
        roleFilterBar.getChildren().clear();
        for (RoleFilter filter : RoleFilter.values()) {
            StackPane button = buildRoleFilterButton(filter);
            roleFilterBar.getChildren().add(button);
        }
        roleFilterBar.setManaged(false);
        roleFilterBar.setVisible(false);
    }

    private StackPane buildRoleFilterButton(RoleFilter filter) {
        StackPane container = new StackPane();
        container.getStyleClass().add("role-filter-chip");
        container.getStyleClass().add(filter == activeRoleFilter ? "active" : "inactive");
        ImageView icon = new ImageView(loadFilterIcon(filter));
        icon.setFitWidth(20);
        icon.setFitHeight(20);
        icon.setPreserveRatio(true);
        container.getChildren().add(icon);
        container.setOnMouseClicked(event -> {
            if (activeRoleFilter != filter) {
                activeRoleFilter = filter;
                updateRoleFilterSelection();
                applyChampionFilter(championFilterField.getText());
            }
        });
        container.setUserData(filter);
        return container;
    }

    private Image loadFilterIcon(RoleFilter filter) {
        if (filter.mappedRole() != null) {
            return roleIcon(filter.mappedRole());
        }
        return flexFilterIcon();
    }

    private Image flexFilterIcon() {
        ThemeManager.Theme theme = ThemeManager.currentTheme();
        String suffix = theme == ThemeManager.Theme.DARK ? "dark" : "light";
        String resource = "/org/example/images/roles/" + suffix + "/flex.png";
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resource)));
        } catch (Exception ex) {
            return ChampionIconResolver.placeholder();
        }
    }

    private Image scoreBadgeFor(double value) {
        int bucket = (int) Math.max(1, Math.min(10, Math.round(value)));
        String resource = "/org/example/images/score/score-" + bucket + ".png";
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resource)));
        } catch (Exception ex) {
            return null;
        }
    }

    private void updateRoleFilterSelection() {
        if (roleFilterBar == null) return;
        for (javafx.scene.Node node : roleFilterBar.getChildren()) {
            if (node instanceof StackPane stackPane && stackPane.getUserData() instanceof RoleFilter filter) {
                stackPane.getStyleClass().removeAll("active", "inactive");
                stackPane.getStyleClass().add(filter == activeRoleFilter ? "active" : "inactive");
            }
        }
    }


    private void handleFirstPickSelection(Side side) {
        if (liveMirrorActive) {
            updateStatus("Live mirroring is active when the League client is in champ select.");
            return;
        }
        if (side == null) {
            resetBoardToInitial();
            return;
        }
        firstPickSide = side;
        hideFirstPickPrompt();
        showChampionFilter(true);
        restartDraftForSide(side);
    }

    private void resetBoardToInitial() {
        clearAllSlotsAndEffects();
        firstPickSide = null;
        pickOrder = List.of();
        pickIndex = 0;
        unlockedSlots.clear();
        draftPhase = liveMirrorActive ? DraftPhase.LIVE_MIRROR : DraftPhase.SELECT_FIRST_PICK;
        if (finishBansButton != null) {
            finishBansButton.setDisable(true);
        }
        if (liveMirrorActive) {
            hideFirstPickPrompt();
        } else {
            showFirstPickPrompt(true);
            updateStatus("Choose who has first pick to start drafting.");
        }
        showChampionFilter(false);
        updateFinishBansState();
        refreshRecommendations();
    }

    private void restartDraftForSide(Side side) {
        clearAllSlotsAndEffects();
        pickOrder = buildPickOrder(side);
        pickIndex = 0;
        draftPhase = DraftPhase.BAN_PHASE;
        updateFinishBansState();
        updateStatus("Ban Phase: fill bans for both teams, then click Finish Bans.");
        refreshRecommendations();
    }

    private void clearAllSlotsAndEffects() {
        clearList(allyBans);
        clearList(enemyBans);
        clearList(allyPicks);
        clearList(enemyPicks);
        slots.forEach(slot -> updateSlotIcon(slot, null));
        slots.forEach(this::updateRoleHighlight);
        slots.forEach(slot -> {
            if (slot.pickView != null) {
                slot.pickView.setEffect(null);
            }
        });
        activeSlot = null;
        unlockedSlots.clear();
    }

    private void attachClientWatcher() {
        if (watcherAttached) {
            return;
        }
        clientWatcher.addListener(clientSnapshotConsumer);
        clientWatcher.start();
        watcherAttached = true;
    }

    private void detachClientWatcher() {
        if (!watcherAttached) {
            return;
        }
        clientWatcher.removeListener(clientSnapshotConsumer);
        clientWatcher.stop();
        watcherAttached = false;
        if (liveMirrorActive) {
            liveMirrorActive = false;
            resetBoardToInitial();
        }
    }

    private void handleClientSnapshot(ChampSelectSnapshot snapshot) {
        if (snapshot.inChampSelect()) {
            if (!liveMirrorActive) {
                liveMirrorActive = true;
                draftPhase = DraftPhase.LIVE_MIRROR;
                clearAllSlotsAndEffects();
                hideFirstPickPrompt();
                updateFinishBansState();
            }
            firstPickSide = convertSnapshotSide(snapshot.firstPickSide());
            copySnapshotList(snapshot.allyBans(), allyBans);
            copySnapshotList(snapshot.enemyBans(), enemyBans);
            copySnapshotList(snapshot.allyPicks(), allyPicks);
            copySnapshotList(snapshot.enemyPicks(), enemyPicks);
            slots.forEach(slot -> updateSlotIcon(slot, valueForSlot(slot)));
            slots.forEach(this::updateRoleHighlight);
            setActiveSlot(null);
            updateStatus(snapshot.statusText());
            return;
        }
        if (liveMirrorActive) {
            liveMirrorActive = false;
            resetBoardToInitial();
            updateStatus(snapshot.statusText());
        }
    }

    private void copySnapshotList(List<String> source, List<String> target) {
        for (int i = 0; i < target.size(); i++) {
            String value = (source != null && i < source.size()) ? normalizeChampionName(source.get(i)) : null;
            target.set(i, value);
        }
    }

    private Side convertSnapshotSide(ChampSelectSnapshot.Side side) {
        return switch (side) {
            case ALLY -> Side.ALLY;
            case ENEMY -> Side.ENEMY;
            default -> null;
        };
    }

    private String normalizeChampionName(String name) {
        String canonical = ChampionNames.canonicalName(name);
        if (canonical == null || canonical.isBlank()) {
            return null;
        }
        return canonical;
    }

    private List<Slot> buildPickOrder(Side side) {
        List<Slot> order = new ArrayList<>();
        if (side == Side.ALLY) {
            order.add(findSlot(SlotType.ALLY_PICK, 0));
            order.add(findSlot(SlotType.ENEMY_PICK, 0));
            order.add(findSlot(SlotType.ENEMY_PICK, 1));
            order.add(findSlot(SlotType.ALLY_PICK, 1));
            order.add(findSlot(SlotType.ALLY_PICK, 2));
            order.add(findSlot(SlotType.ENEMY_PICK, 2));
            order.add(findSlot(SlotType.ENEMY_PICK, 3));
            order.add(findSlot(SlotType.ALLY_PICK, 3));
            order.add(findSlot(SlotType.ALLY_PICK, 4));
            order.add(findSlot(SlotType.ENEMY_PICK, 4));
        } else {
            order.add(findSlot(SlotType.ENEMY_PICK, 0));
            order.add(findSlot(SlotType.ALLY_PICK, 0));
            order.add(findSlot(SlotType.ALLY_PICK, 1));
            order.add(findSlot(SlotType.ENEMY_PICK, 1));
            order.add(findSlot(SlotType.ENEMY_PICK, 2));
            order.add(findSlot(SlotType.ALLY_PICK, 2));
            order.add(findSlot(SlotType.ALLY_PICK, 3));
            order.add(findSlot(SlotType.ENEMY_PICK, 3));
            order.add(findSlot(SlotType.ENEMY_PICK, 4));
            order.add(findSlot(SlotType.ALLY_PICK, 4));
        }
        return order;
    }

    private Slot findSlot(SlotType type, int index) {
        return slots.stream()
                .filter(slot -> slot.type == type && slot.index == index)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing slot " + type + " #" + index));
    }

    private void activatePickSlot(Slot slot) {
        unlockedSlots.clear();
        if (slot != null) {
            unlockedSlots.add(slot);
        }
        setActiveSlot(slot);
        if (slot != null) {
            updateStatus("Pick Phase: " + describeSlot(slot) + " is up.");
        }
    }

    private void buildRoleChips(Slot slot) {
        slot.roleRow.getChildren().clear();
        boolean allySide = slot.type.isAlly();
        for (Role role : ROLE_ORDER) {
            StackPane chip = new StackPane();
            chip.setPrefSize(28, 28);
            chip.setMinSize(28, 28);
            chip.setMaxSize(28, 28);
            chip.getStyleClass().addAll("role-chip", allySide ? "ally" : "enemy");
            ImageView icon = new ImageView(roleIcon(role));
            icon.setFitWidth(18);
            icon.setFitHeight(18);
            icon.setPreserveRatio(true);
            chip.getChildren().add(icon);
            chip.setOnMouseClicked(e -> {
                selectRole(slot, role);
            });
            HBox.setMargin(chip, new Insets(0, 3, 0, 0));
            slot.roleRow.getChildren().add(chip);
            slot.roleChips.add(new RoleChip(role, chip, icon));
            Tooltip.install(chip, new Tooltip(role.label()));
        }
        updateRoleHighlight(slot);
    }

    private void handleSlotClick(Slot slot, MouseEvent event) {
        if (draftPhase == DraftPhase.LIVE_MIRROR) {
            updateStatus("Viewing live client. Wait until champ select ends to edit manually.");
            return;
        }
        if (draftPhase == DraftPhase.SELECT_FIRST_PICK) {
            updateStatus("Choose who has first pick before interacting with slots.");
            return;
        }
        if (draftPhase == DraftPhase.COMPLETE) {
            updateStatus("Draft complete. Clear selections to start over.");
            return;
        }
        boolean banSlot = isBanSlot(slot);
        boolean pickSlot = isPickSlot(slot);
        if (banSlot && draftPhase != DraftPhase.BAN_PHASE) {
            updateStatus("Bans are locked once pick phase begins.");
            return;
        }
        if (pickSlot) {
            if (draftPhase != DraftPhase.PICK_PHASE) {
                updateStatus("Finish bans before moving on to picks.");
                return;
            }
            if (!unlockedSlots.contains(slot)) {
                updateStatus(describeSlot(slot) + " is locked until it is that side's turn.");
                return;
            }
        }
        setActiveSlot(slot);
        if (event.getButton() == MouseButton.SECONDARY || event.getClickCount() == 2) {
            clearSlot(slot);
        }
    }

    private void selectRole(Slot slot, Role role) {
        List<Role> roles = rolesForSlot(slot);
        if (roles == null) return;
        roles.set(slot.index, role);
        updateRoleHighlight(slot);
        updateStatus("Set " + describeSlot(slot) + " role to " + role.label() + ".");
        refreshRecommendations();
    }

    private void assignSelectedChampion() {
        ChampionSummary selection = recommendedTable.getSelectionModel().getSelectedItem();
        if (selection == null) {
            updateStatus("Double-click a champion row to assign it.");
            return;
        }
        if (draftPhase == DraftPhase.LIVE_MIRROR) {
            updateStatus("Live mirror is active. Disable it to edit selections.");
            return;
        }
        if (activeSlot == null) {
            updateStatus("Select a slot first.");
            return;
        }
        placeChampion(activeSlot, selection.name());
    }

    @FXML
    private void onAllyFirstPickSelected() {
        handleFirstPickSelection(Side.ALLY);
    }

    @FXML
    private void onEnemyFirstPickSelected() {
        handleFirstPickSelection(Side.ENEMY);
    }

    private void placeChampion(Slot slot, String champion) {
        String canonical = ChampionNames.canonicalName(champion);
        if (canonical == null || canonical.isBlank()) {
            updateStatus("Unable to place champion. Please try again.");
            return;
        }
        switch (slot.type) {
            case ALLY_BAN -> allyBans.set(slot.index, canonical);
            case ENEMY_BAN -> enemyBans.set(slot.index, canonical);
            case ALLY_PICK -> allyPicks.set(slot.index, canonical);
            case ENEMY_PICK -> enemyPicks.set(slot.index, canonical);
        }
        updateSlotIcon(slot, canonical);
        updateStatus("Placed " + ChampionNames.displayName(canonical) + " into " + describeSlot(slot) + ".");
        handleSlotCommit(slot);
        updateFinishBansState();
    }

    private void clearSlot(Slot slot) {
        clearSlot(slot, false);
    }

    private void clearSlot(Slot slot, boolean force) {
        if (slot == null) return;
        if (!force && !canModifySlot(slot)) {
            updateStatus("Cannot modify " + describeSlot(slot) + " right now.");
            return;
        }
        switch (slot.type) {
            case ALLY_BAN -> allyBans.set(slot.index, null);
            case ENEMY_BAN -> enemyBans.set(slot.index, null);
            case ALLY_PICK -> allyPicks.set(slot.index, null);
            case ENEMY_PICK -> enemyPicks.set(slot.index, null);
        }
        updateSlotIcon(slot, null);
        if (!force) {
            updateStatus("Cleared " + describeSlot(slot) + ".");
            refreshRecommendations();
            updateFinishBansState();
        }
    }

    private boolean canModifySlot(Slot slot) {
        if (slot == null) return false;
        if (draftPhase == DraftPhase.LIVE_MIRROR) return false;
        if (isBanSlot(slot)) {
            return draftPhase == DraftPhase.BAN_PHASE;
        }
        if (isPickSlot(slot)) {
            return unlockedSlots.contains(slot);
        }
        return false;
    }

    private void handleSlotCommit(Slot slot) {
        if (draftPhase == DraftPhase.PICK_PHASE && isPickSlot(slot)) {
            pickIndex++;
            if (pickIndex < pickOrder.size()) {
                Slot next = pickOrder.get(pickIndex);
                activatePickSlot(next);
            } else {
                activatePickSlot(null);
                draftPhase = DraftPhase.COMPLETE;
                updateStatus("Draft complete.");
            }
        }
    }

    private boolean isBanSlot(Slot slot) {
        return slot.type == SlotType.ALLY_BAN || slot.type == SlotType.ENEMY_BAN;
    }

    private boolean isPickSlot(Slot slot) {
        return slot.type == SlotType.ALLY_PICK || slot.type == SlotType.ENEMY_PICK;
    }

    private void setActiveSlot(Slot slot) {
        Slot previous = this.activeSlot;
        this.activeSlot = slot;
        slots.forEach(s -> {
            if (s.pickView != null) {
                s.pickView.setEffect(s == this.activeSlot ? activeEffect : null);
            }
        });
        if (!Objects.equals(previous, slot)) {
            refreshRecommendations();
        }
    }

    private String describeSlot(Slot slot) {
        String prefix = switch (slot.type) {
            case ALLY_BAN -> "Ally Ban";
            case ENEMY_BAN -> "Enemy Ban";
            case ALLY_PICK -> "Ally Pick";
            case ENEMY_PICK -> "Enemy Pick";
        };
        if (slot.type == SlotType.ALLY_PICK || slot.type == SlotType.ENEMY_PICK) {
            Role role = rolesForSlot(slot).get(slot.index);
            return prefix + " #" + (slot.index + 1) + " (" + role.label() + ")";
        }
        return prefix + " #" + (slot.index + 1);
    }

    private void refreshRecommendations() {
        List<SlotSelection> allySelections = buildSelections(allyPicks, allyPickRoles);
        List<SlotSelection> enemySelections = buildSelections(enemyPicks, enemyPickRoles);
        List<String> bans = mergeLists(allyBans, enemyBans);
        Role targetRole = activeSlot != null && (activeSlot.type == SlotType.ALLY_PICK || activeSlot.type == SlotType.ENEMY_PICK)
                ? rolesForSlot(activeSlot).get(activeSlot.index)
                : Role.UNKNOWN;

        boolean allyPerspective = activeSlot == null || activeSlot.type.isAlly();
        RecommendationContext context = new RecommendationContext(
                allySelections,
                enemySelections,
                bans,
                targetRole,
                allyPerspective,
                RECOMMENDATION_LIMIT
        );

        List<ChampionSummary> summaries = statsService.fetchRecommended(context);
        tableData.setAll(summaries);
        recommendedTable.getSortOrder().setAll(scoreCol);
        recommendedTable.sort();
    }

    @FXML
    private void onClearSelections() {
        if (liveMirrorActive) {
            updateStatus("Live mirroring is active while the client is in champ select.");
            return;
        }
        resetBoardToInitial();
    }

    @FXML
    private void onFinishBans() {
        if (draftPhase != DraftPhase.BAN_PHASE) {
            updateStatus("Ban phase is already complete.");
            return;
        }
        draftPhase = DraftPhase.PICK_PHASE;
        pickIndex = 0;
        if (!pickOrder.isEmpty()) {
            Slot slot = pickOrder.get(0);
            activatePickSlot(slot);
        } else {
            activatePickSlot(null);
            draftPhase = DraftPhase.COMPLETE;
            updateStatus("No pick order configured.");
        }
        if (finishBansButton != null) {
            finishBansButton.setDisable(true);
        }
        updateFinishBansState();
    }

    private List<SlotSelection> buildSelections(List<String> champions, List<Role> roles) {
        List<SlotSelection> selections = new ArrayList<>();
        for (int i = 0; i < champions.size(); i++) {
            String champion = champions.get(i);
            if (champion == null || champion.isBlank()) continue;
            Role role = roles.get(i);
            selections.add(new SlotSelection(champion, role));
        }
        return selections;
    }

    private List<String> mergeLists(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(name -> name != null && !name.isBlank()).forEach(merged::add);
        }
        if (second != null) {
            second.stream().filter(name -> name != null && !name.isBlank()).forEach(merged::add);
        }
        return merged.isEmpty() ? List.of() : List.copyOf(merged);
    }

    private void updateStatus(String message) {
        if (selectionStatusLabel != null) {
            selectionStatusLabel.setText(message);
        }
    }

    private StatsService initStatsService() {
        String apiKey = System.getenv("RIOT_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            String platformTag = System.getenv().getOrDefault("RIOT_PLATFORM", "EUROPE_WEST");
            return new RiotStatsService(apiKey, platformTag);
        }
        System.out.println("[GameController] Missing RIOT_API_KEY, using mock data.");
        return new MockStatsService();
    }

    private String valueForSlot(Slot slot) {
        return switch (slot.type) {
            case ALLY_BAN -> allyBans.get(slot.index);
            case ENEMY_BAN -> enemyBans.get(slot.index);
            case ALLY_PICK -> allyPicks.get(slot.index);
            case ENEMY_PICK -> enemyPicks.get(slot.index);
        };
    }

    private void updateFinishBansState() {
        if (finishBansButton == null) return;
        boolean enabled = !liveMirrorActive
                && draftPhase == DraftPhase.BAN_PHASE;
        finishBansButton.setDisable(!enabled);
    }

    private void updateRoleHighlight(Slot slot) {
        if (slot.roleRow == null || slot.roleChips.isEmpty()) return;
        List<Role> roles = rolesForSlot(slot);
        if (roles == null) return;
        Role selected = roles.get(slot.index);
        for (RoleChip chip : slot.roleChips) {
            chip.container.getStyleClass().remove("role-chip-active");
            if (chip.role == selected) {
                chip.container.getStyleClass().add("role-chip-active");
            }
        }
    }

    private Image tierBadgeFor(Tier tier) {
        String key = tierBadgeKey(tier);
        return tierBadgeCache.computeIfAbsent(key, this::loadTierBadge);
    }

    private Image loadTierBadge(String key) {
        String resource = "/org/example/images/tiers/tier-" + key + ".png";
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resource)));
        } catch (Exception ex) {
            return ChampionIconResolver.placeholder();
        }
    }

    private String tierBadgeKey(Tier tier) {
        if (tier == null) {
            return "na";
        }
        return switch (tier) {
            case S_PLUS -> "s-plus";
            case S -> "s";
            case S_MINUS -> "s-minus";
            case A_PLUS -> "a-plus";
            case A -> "a";
            case A_MINUS -> "a-minus";
            case B_PLUS -> "b-plus";
            case B -> "b";
            case B_MINUS -> "b-minus";
            case C_PLUS -> "c-plus";
            case C -> "c";
            case C_MINUS -> "c-minus";
            case D_PLUS -> "d-plus";
            case D -> "d";
            case D_MINUS -> "d-minus";
            case NA -> "na";
        };
    }

    private final class ScoreCell extends TableCell<ChampionSummary, Number> {
        private final Label scoreLabel = new Label();
        private final ImageView meterView = new ImageView();
        private final HBox container = new HBox(8, scoreLabel, meterView);

        ScoreCell() {
            container.getStyleClass().add("score-cell");
            scoreLabel.getStyleClass().add("score-label");
            scoreLabel.setMinWidth(Region.USE_PREF_SIZE);
            scoreLabel.setPrefWidth(44);
            scoreLabel.setMaxWidth(Region.USE_PREF_SIZE);
            scoreLabel.setAlignment(Pos.CENTER_RIGHT);
            scoreLabel.setTextOverrun(OverrunStyle.CLIP);
            meterView.setFitWidth(90);
            meterView.setFitHeight(12);
            meterView.setPreserveRatio(true);
        }

        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            double value = item.doubleValue();
            scoreLabel.setText(String.format("%.1f", value));
            meterView.setImage(scoreBadgeFor(value));
            setGraphic(container);
            setText(null);
        }
    }

    private void refreshRoleIcons() {
        roleIconCache.clear();
        for (Slot slot : slots) {
            for (RoleChip chip : slot.roleChips) {
                if (chip.iconView != null) {
                    chip.iconView.setImage(roleIcon(chip.role));
                }
            }
        }
        refreshRoleFilterBarIcons();
    }

    private void refreshRoleFilterBarIcons() {
        if (roleFilterBar == null) return;
        for (javafx.scene.Node node : roleFilterBar.getChildren()) {
            if (node instanceof StackPane stackPane && stackPane.getUserData() instanceof RoleFilter filter) {
                ImageView iconView = null;
                for (javafx.scene.Node child : stackPane.getChildren()) {
                    if (child instanceof ImageView view) {
                        iconView = view;
                        break;
                    }
                }
                if (iconView != null) {
                    iconView.setImage(loadFilterIcon(filter));
                }
            }
        }
    }

    private Image roleIcon(Role role) {
        ThemeManager.Theme theme = ThemeManager.currentTheme();
        Map<Role, Image> cache = roleIconCache.computeIfAbsent(theme, t -> new EnumMap<>(Role.class));
        return cache.computeIfAbsent(role, r -> {
            String folder = theme == ThemeManager.Theme.DARK ? "dark" : "light";
            String path = "/org/example/images/roles/" + folder + "/" + r.iconFile();
            try {
                return new Image(getClass().getResourceAsStream(path));
            } catch (Exception ex) {
                return ChampionIconResolver.placeholder();
            }
        });
    }

    private void updateSlotIcon(Slot slot, String champion) {
        if (slot == null || slot.pickView == null) {
            return;
        }
        Image image = (champion == null || champion.isBlank())
                ? placeholderForSlot(slot)
                : ChampionIconResolver.load(champion);
        slot.pickView.setImage(image);
    }

    private Image placeholderForSlot(Slot slot) {
        return slot.type.isAlly()
                ? ChampionIconResolver.allyPlaceholder()
                : ChampionIconResolver.enemyPlaceholder();
    }

    private List<Role> rolesForSlot(Slot slot) {
        return switch (slot.type) {
            case ALLY_PICK -> allyPickRoles;
            case ENEMY_PICK -> enemyPickRoles;
            default -> null;
        };
    }

    private static List<String> createSlotList() {
        return new ArrayList<>(java.util.Collections.nCopies(SLOT_COUNT, null));
    }

    private void clearList(List<String> list) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            list.set(i, null);
        }
    }
}
