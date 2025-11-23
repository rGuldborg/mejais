package org.example.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.example.model.ChampionSummary;
import org.example.model.RecommendationContext;
import org.example.model.Role;
import org.example.model.SlotSelection;
import org.example.service.MockStatsService;
import org.example.service.RiotStatsService;
import org.example.service.StatsService;
import org.example.util.ChampionIconResolver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameController {
    private static final int SLOT_COUNT = 5;
    private static final int RECOMMENDATION_LIMIT = 100;
    private static final Role[] ROLE_ORDER = {Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT};

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

    private final List<String> allyBans = createSlotList();
    private final List<String> enemyBans = createSlotList();
    private final List<String> allyPicks = createSlotList();
    private final List<String> enemyPicks = createSlotList();
    private final List<Role> allyPickRoles = new ArrayList<>(List.of(Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT));
    private final List<Role> enemyPickRoles = new ArrayList<>(List.of(Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT));
    private final List<Slot> slots = new ArrayList<>();
    private final DropShadow activeEffect = new DropShadow(18, Color.web("#73c0ff"));
    private final Map<Role, Image> roleIconCache = new EnumMap<>(Role.class);

    private StatsService statsService;
    private ObservableList<ChampionSummary> tableData;
    private Slot activeSlot;

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

    private static class RoleChip {
        final Role role;
        final StackPane container;

        RoleChip(Role role, StackPane container) {
            this.role = role;
            this.container = container;
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
        statsService = initStatsService();
        configureTable();
        configureSlots();
        setPlaceholderImages();
        slots.stream().filter(s -> s.type == SlotType.ALLY_PICK).findFirst().ifPresent(this::setActiveSlot);
        refreshRecommendations();
        recommendedTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                assignSelectedChampion();
            }
        });
    }

    private void configureTable() {
        tableData = FXCollections.observableArrayList();
        recommendedTable.setItems(tableData);
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
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                ChampionSummary row = getTableView().getItems().get(getIndex());
                iconView.setImage(row.icon());
                nameText.setText(row.name());
                setGraphic(box);
                setText(null);
            }
        });

        scoreCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(String.format("%.1f", item.doubleValue()));
            }
        });

        scoreCol.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void configureSlots() {
        slots.add(new Slot(SlotType.ALLY_BAN, 0, allyBan1, null));
        slots.add(new Slot(SlotType.ALLY_BAN, 1, allyBan2, null));
        slots.add(new Slot(SlotType.ALLY_BAN, 2, allyBan3, null));
        slots.add(new Slot(SlotType.ALLY_BAN, 3, allyBan4, null));
        slots.add(new Slot(SlotType.ALLY_BAN, 4, allyBan5, null));

        slots.add(new Slot(SlotType.ENEMY_BAN, 0, enemyBan1, null));
        slots.add(new Slot(SlotType.ENEMY_BAN, 1, enemyBan2, null));
        slots.add(new Slot(SlotType.ENEMY_BAN, 2, enemyBan3, null));
        slots.add(new Slot(SlotType.ENEMY_BAN, 3, enemyBan4, null));
        slots.add(new Slot(SlotType.ENEMY_BAN, 4, enemyBan5, null));

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
                slot.pickView.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        setActiveSlot(slot);
                        if (event.getClickCount() == 2) {
                            clearSlot(slot);
                        }
                    } else if (event.getButton() == MouseButton.SECONDARY) {
                        clearSlot(slot);
                    }
                });
            }
            if (slot.roleRow != null) {
                buildRoleChips(slot);
            }
        });
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
            slot.roleChips.add(new RoleChip(role, chip));
            Tooltip.install(chip, new Tooltip(role.label()));
        }
        updateRoleHighlight(slot);
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
        if (activeSlot == null) {
            updateStatus("Select a slot first.");
            return;
        }
        placeChampion(activeSlot, selection.name());
    }

    private void placeChampion(Slot slot, String champion) {
        switch (slot.type) {
            case ALLY_BAN -> allyBans.set(slot.index, champion);
            case ENEMY_BAN -> enemyBans.set(slot.index, champion);
            case ALLY_PICK -> allyPicks.set(slot.index, champion);
            case ENEMY_PICK -> enemyPicks.set(slot.index, champion);
        }
        updateIcon(slot.pickView, champion);
        updateStatus("Placed " + champion + " into " + describeSlot(slot) + ".");
        refreshRecommendations();
    }

    private void clearSlot(Slot slot) {
        switch (slot.type) {
            case ALLY_BAN -> allyBans.set(slot.index, null);
            case ENEMY_BAN -> enemyBans.set(slot.index, null);
            case ALLY_PICK -> allyPicks.set(slot.index, null);
            case ENEMY_PICK -> enemyPicks.set(slot.index, null);
        }
        updateIcon(slot.pickView, null);
        updateStatus("Cleared " + describeSlot(slot) + ".");
        refreshRecommendations();
    }

    private void setActiveSlot(Slot slot) {
        activeSlot = slot;
        updateStatus("Selected " + describeSlot(slot) + ". Double-click a champion to lock it.");
        slots.forEach(s -> {
            if (s.pickView != null) {
                s.pickView.setEffect(s == activeSlot ? activeEffect : null);
            }
        });
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

        RecommendationContext context = new RecommendationContext(
                allySelections,
                enemySelections,
                bans,
                targetRole,
                RECOMMENDATION_LIMIT
        );

        List<ChampionSummary> summaries = statsService.fetchRecommended(context);
        tableData.setAll(summaries);
        recommendedTable.getSortOrder().setAll(scoreCol);
        recommendedTable.sort();
    }

    @FXML
    private void onClearSelections() {
        clearList(allyBans);
        clearList(enemyBans);
        clearList(allyPicks);
        clearList(enemyPicks);
        slots.forEach(slot -> updateIcon(slot.pickView, valueForSlot(slot)));
        slots.forEach(this::updateRoleHighlight);
        slots.stream().filter(s -> s.type == SlotType.ALLY_PICK).findFirst().ifPresent(this::setActiveSlot);
        updateStatus("All slots cleared. Select a role to continue.");
        refreshRecommendations();
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

    private void setPlaceholderImages() {
        slots.forEach(slot -> updateIcon(slot.pickView, valueForSlot(slot)));
        slots.forEach(this::updateRoleHighlight);
        slots.forEach(slot -> {
            if (slot.pickView != null) {
                slot.pickView.setEffect(null);
            }
        });
        activeSlot = null;
        updateStatus("Select a slot to begin.");
    }

    private String valueForSlot(Slot slot) {
        return switch (slot.type) {
            case ALLY_BAN -> allyBans.get(slot.index);
            case ENEMY_BAN -> enemyBans.get(slot.index);
            case ALLY_PICK -> allyPicks.get(slot.index);
            case ENEMY_PICK -> enemyPicks.get(slot.index);
        };
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

    private Image roleIcon(Role role) {
        return roleIconCache.computeIfAbsent(role, r -> {
            String path = "/org/example/images/roles/" + r.iconFile();
            try {
                return new Image(getClass().getResourceAsStream(path));
            } catch (Exception ex) {
                return ChampionIconResolver.placeholder();
            }
        });
    }

    private void updateIcon(ImageView view, String champion) {
        if (view == null) return;
        view.setImage(champion == null ? ChampionIconResolver.placeholder() : ChampionIconResolver.load(champion));
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
