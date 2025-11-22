package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import org.example.ThemeManager;

public class MainController {

    @FXML private TextField searchField;
    @FXML private Button themeToggleButton;
    @FXML private StackPane contentArea;

    private boolean darkMode = true; // start in DARK MODE

    @FXML
    public void initialize() {
        System.out.println("MainController initialized");

        // Start med DARK mode
        ThemeManager.applyTheme("dark.css");
        themeToggleButton.setText("🌙 Dark");

        // Load the primary GAME view by default so placeholders/map are visible immediately
        loadView("game-view.fxml");
    }

    @FXML
    private void onThemeToggle() {
        darkMode = !darkMode;

        if (darkMode) {
            themeToggleButton.setText("🌙 Dark");
            ThemeManager.applyTheme("dark.css");
        } else {
            themeToggleButton.setText("☀ Light");
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

    private void loadView(String fxmlName) {
        try {
            var loader = new FXMLLoader(
                    getClass().getResource("/org/example/fxml/" + fxmlName)
            );
            var node = loader.load();
            contentArea.getChildren().setAll((Node) node);
        } catch (Exception e) {
            System.err.println("Could not load view: " + fxmlName);
            e.printStackTrace();
        }
    }
}
