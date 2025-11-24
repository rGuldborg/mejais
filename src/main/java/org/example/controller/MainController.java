package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.ThemeManager;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MainController {

    private static final DateTimeFormatter FOOTER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String ACTIVE_TAB_CLASS = "tab-chip-active";

    @FXML private TextField searchField;
    @FXML private Button themeToggleButton;
    @FXML private Button refreshButton;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    @FXML private Label gameTab;
    @FXML private Label championsTab;
    @FXML private BorderPane windowHeader;
    @FXML private StackPane contentArea;
    @FXML private Node moonIcon;
    @FXML private Node sunIcon;
    @FXML private Label footerLastUpdatedLabel;

    private boolean darkMode = true; // start in DARK MODE
    private double xOffset;
    private double yOffset;
    private ChampionsController championsController;
    private Node gameView;
    private Node championsView;

    @FXML
    public void initialize() {
        System.out.println("MainController initialized");

        // Start med DARK mode
        ThemeManager.applyTheme("dark.css");

        // Ingen tekst - kun SVG ikoner i FXML
        themeToggleButton.setText("");
        if (refreshButton != null) refreshButton.setText("");
        flattenButtons(themeToggleButton, refreshButton,
                minimizeButton, maximizeButton, closeButton);
        updateThemeIcon();
        Platform.runLater(() -> {
            if (windowHeader != null) {
                windowHeader.requestFocus();
            }
        });

        // Load the primary GAME view by default so placeholders/map are visible immediately
        showGameView();
        setActiveTab(gameTab);
        ensureChampionsViewInitialized();
        updateSnapshotTimestamp();
    }

    @FXML
    private void onThemeToggle() {
        darkMode = !darkMode;

        themeToggleButton.setText(""); // hold teksten tom
        updateThemeIcon();

        if (darkMode) {
            ThemeManager.applyTheme("dark.css");
        } else {
            ThemeManager.applyTheme("light.css");
        }
    }

    @FXML
    private void onRefresh() {
        System.out.println("REFRESH CLICKED");
        updateSnapshotTimestamp();
    }

    @FXML
    private void onGameNav() {
        setActiveTab(gameTab);
        showGameView();
    }

    @FXML
    private void onChampionsNav() {
        setActiveTab(championsTab);
        showChampionView();
    }

    @FXML
    private void onGameTabClicked(MouseEvent event) {
        onGameNav();
    }

    @FXML
    private void onChampionsTabClicked(MouseEvent event) {
        onChampionsNav();
    }

    @FXML
    private void onGameTabKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            onGameNav();
            event.consume();
        }
    }

    @FXML
    private void onChampionsTabKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            onChampionsNav();
            event.consume();
        }
    }

    @FXML
    private void onMaximize() {
        var stage = getStage();
        if (stage != null) {
            stage.setMaximized(!stage.isMaximized());
        }
    }

    @FXML
    private void onMinimize() {
        var stage = getStage();
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    private void onClose() {
        var stage = getStage();
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void onHeaderPressed(MouseEvent event) {
        var stage = getStage();
        if (stage != null) {
            xOffset = stage.getX() - event.getScreenX();
            yOffset = stage.getY() - event.getScreenY();
        }
    }

    @FXML
    private void onHeaderDragged(MouseEvent event) {
        var stage = getStage();
        if (stage != null) {
            stage.setX(event.getScreenX() + xOffset);
            stage.setY(event.getScreenY() + yOffset);
        }
    }

    private void showGameView() {
        Node view = getGameView();
        if (view != null) {
            contentArea.getChildren().setAll(view);
        }
    }

    private Node getGameView() {
        if (gameView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/fxml/game-view.fxml"));
                gameView = loader.load();
            } catch (Exception e) {
                System.err.println("Could not load game view.");
                e.printStackTrace();
            }
        }
        return gameView;
    }

    private void showChampionView() {
        ensureChampionsViewInitialized();
        if (championsView != null) {
            contentArea.getChildren().setAll(championsView);
        }
    }

    private void ensureChampionsViewInitialized() {
        if (championsView != null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/fxml/champions-view.fxml"));
            championsView = loader.load();
            championsController = loader.getController();
            if (searchField != null) {
                championsController.bindSearchField(searchField);
            }
            championsController.setShowViewRequest(this::showChampionViewFromSearch);
        } catch (Exception e) {
            System.err.println("Could not load champions view.");
            e.printStackTrace();
        }
    }

    private void showChampionViewFromSearch() {
        Platform.runLater(() -> {
            setActiveTab(championsTab);
            showChampionView();
        });
    }

    private Stage getStage() {
        return windowHeader != null && windowHeader.getScene() != null
                ? (Stage) windowHeader.getScene().getWindow()
                : null;
    }

    private void flattenButtons(Button... buttons) {
        for (Button b : buttons) {
            if (b == null) continue;
            b.setBackground(javafx.scene.layout.Background.EMPTY);
            b.setBorder(javafx.scene.layout.Border.EMPTY);
            b.setPadding(javafx.geometry.Insets.EMPTY);
            b.setFocusTraversable(false);
        }
    }

    private void updateThemeIcon() {
        if (moonIcon != null && sunIcon != null) {
            moonIcon.setVisible(darkMode);
            sunIcon.setVisible(!darkMode);
        }
    }

    private void updateSnapshotTimestamp() {
        if (footerLastUpdatedLabel == null) {
            return;
        }
        File snapshotFile = new File("data/snapshot.json");
        if (!snapshotFile.exists()) {
            footerLastUpdatedLabel.setText("Last updated: never");
            return;
        }
        Instant lastModified = Instant.ofEpochMilli(snapshotFile.lastModified());
        LocalDateTime localDateTime = LocalDateTime.ofInstant(lastModified, ZoneId.systemDefault());
        footerLastUpdatedLabel.setText("Last updated: " + FOOTER_TIME_FORMAT.format(localDateTime));
    }

    private void setActiveTab(Label activeTab) {
        Label[] tabs = {gameTab, championsTab};
        for (Label tab : tabs) {
            if (tab == null) continue;
            tab.getStyleClass().remove(ACTIVE_TAB_CLASS);
        }
        if (activeTab != null && !activeTab.getStyleClass().contains(ACTIVE_TAB_CLASS)) {
            activeTab.getStyleClass().add(ACTIVE_TAB_CLASS);
        }
    }
}
