package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.ThemeManager;

public class MainController {

    @FXML private TextField searchField;
    @FXML private Button themeToggleButton;
    @FXML private Button refreshButton;
    @FXML private Button gameButton;
    @FXML private Button championsButton;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    @FXML private BorderPane windowHeader;
    @FXML private StackPane contentArea;
    @FXML private Node moonIcon;
    @FXML private Node sunIcon;

    private boolean darkMode = true; // start in DARK MODE
    private double xOffset;
    private double yOffset;
    private ChampionsController championsController;

    @FXML
    public void initialize() {
        System.out.println("MainController initialized");

        // Start med DARK mode
        ThemeManager.applyTheme("dark.css");

        // Ingen tekst - kun SVG ikoner i FXML
        themeToggleButton.setText("");
        if (refreshButton != null) refreshButton.setText("");
        flattenButtons(themeToggleButton, refreshButton, gameButton, championsButton,
                minimizeButton, maximizeButton, closeButton);
        updateThemeIcon();
        Platform.runLater(() -> {
            if (windowHeader != null) {
                windowHeader.requestFocus();
            }
        });

        // Load the primary GAME view by default so placeholders/map are visible immediately
        loadView("game-view.fxml");
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
    }

    @FXML
    private void onGameNav() {
        loadView("game-view.fxml");
    }

    @FXML
    private void onChampionsNav() {
        loadView("champions-view.fxml");
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

    private void loadView(String fxmlName) {
        try {
            var loader = new FXMLLoader(getClass().getResource("/org/example/fxml/" + fxmlName));
            var node = loader.load();
            Object controller = loader.getController();
            if (controller instanceof ChampionsController champController) {
                champController.bindSearchField(searchField);
                championsController = champController;
            } else if (championsController != null) {
                championsController.detachSearchField();
                championsController = null;
            }
            contentArea.getChildren().setAll((Node) node);
        } catch (Exception e) {
            System.err.println("Could not load view: " + fxmlName);
            e.printStackTrace();
        }
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
}

