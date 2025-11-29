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
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ThemeManager;
import org.example.service.lcu.ChampSelectSnapshot;
import org.example.service.lcu.LeagueClientChampSelectWatcher;
import org.example.util.AppPaths;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;


import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class MainController {

    private static final DateTimeFormatter FOOTER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String ACTIVE_TAB_CLASS = "tab-chip-active";
    private static final String DEFAULT_SNAPSHOT_URL = "https://raw.githubusercontent.com/rGuldborg/mejais/master/data/snapshot.db";
    private static final String REMOTE_DB_URL = Optional.ofNullable(System.getProperty("SNAPSHOT_REMOTE_URL"))
            .filter(url -> !url.isBlank())
            .or(() -> Optional.ofNullable(System.getenv("SNAPSHOT_REMOTE_URL")).filter(url -> !url.isBlank()))
            .orElse(DEFAULT_SNAPSHOT_URL);


    @FXML private TextField searchField;
    @FXML private Button themeToggleButton;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    @FXML private Label gameTab;
    @FXML private Label championsTab;
    @FXML private Label helpTab;
    @FXML private BorderPane windowHeader;
    @FXML private StackPane contentArea;
    @FXML private Node moonIcon;
    @FXML private Node sunIcon;
    @FXML private Label footerLastUpdatedLabel;
    @FXML private Label patchLabel;
    @FXML private Label updateAvailableLabel;
    @FXML private Label lcuStatusLabel;
    @FXML private Circle lcuStatusIndicator;
    @FXML private Button updateButton;

    private final LeagueClientChampSelectWatcher clientWatcher = new LeagueClientChampSelectWatcher();
    private final SimpleObjectProperty<ChampSelectSnapshot> lcuSnapshot = new SimpleObjectProperty<>();

    private boolean darkMode = true;
    private double xOffset;
    private double yOffset;
    private boolean draggingWindow;
    private volatile long pendingRemoteSnapshotStamp = -1L;
    private volatile boolean updateInProgress;
    private GameController gameController;
    private ChampionsController championsController;
    private Node gameView;
    private Node championsView;
    private Node helpView;

    @FXML
    public void initialize() {

        ThemeManager.applyTheme("dark.css");

        themeToggleButton.setText("");
        flattenButtons(themeToggleButton,
                minimizeButton, maximizeButton, closeButton);
        if (updateButton != null) {
            updateButton.setVisible(false);
            updateButton.setManaged(false);
        }
        updateThemeIcon();
        Platform.runLater(() -> {
            if (windowHeader != null) {
                windowHeader.requestFocus();
            }
        });

        showGameView();
        setActiveTab(gameTab);
        ensureChampionsViewInitialized();
        updateSnapshotTimestamp();
        updatePatchVersion();
        checkAndUpdateStatus();
        startLcuWatcher();
    }

    public ReadOnlyObjectProperty<ChampSelectSnapshot> lcuSnapshotProperty() {
        return lcuSnapshot;
    }

    public void stop() {
        clientWatcher.stop();
    }

    private void startLcuWatcher() {
        lcuSnapshot.addListener((obs, oldV, newV) -> Platform.runLater(() -> updateLcuStatusIndicator(newV)));
        clientWatcher.addListener(lcuSnapshot::set);
        clientWatcher.start();
    }

    private void updateLcuStatusIndicator(ChampSelectSnapshot snapshot) {
        if (lcuStatusIndicator == null || lcuStatusLabel == null) {
            return;
        }
        lcuStatusIndicator.getStyleClass().removeAll(
                "lcu-status-indicator-connected",
                "lcu-status-indicator-disconnected",
                "lcu-status-indicator-idle",
                "lcu-status-indicator-unknown"
        );

        String styleClass;
        String statusText;
        if (snapshot == null) {
            styleClass = "lcu-status-indicator-unknown";
            statusText = "Unknown";
        } else if (snapshot.inChampSelect()) {
            styleClass = "lcu-status-indicator-connected";
            statusText = "Connected";
        } else if ("Looking for League client...".equals(snapshot.statusText())) {
            styleClass = "lcu-status-indicator-disconnected";
            statusText = "Disconnected";
        } else if ("Client idle (no champ select).".equals(snapshot.statusText())) {
            styleClass = "lcu-status-indicator-idle";
            statusText = "Client Idle (In-game not detected)";
        } else {
            styleClass = "lcu-status-indicator-unknown";
            statusText = "Unknown";
        }
        lcuStatusIndicator.getStyleClass().add(styleClass);
        lcuStatusLabel.setText(statusText);
    }

    @FXML
    private void onThemeToggle() {
        darkMode = !darkMode;

        themeToggleButton.setText("");
        updateThemeIcon();

        if (darkMode) {
            ThemeManager.applyTheme("dark.css");
        } else {
            ThemeManager.applyTheme("light.css");
        }
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
    private void onHelpNav() {
        setActiveTab(helpTab);
        showHelpView();
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
    private void onHelpTabClicked(MouseEvent event) {
        onHelpNav();
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
    private void onHelpTabKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            onHelpNav();
            event.consume();
        }
    }

    @FXML
    private void onUpdateSnapshot() {
        if (updateButton == null || updateInProgress || REMOTE_DB_URL.isBlank()) {
            return;
        }
        updateInProgress = true;
        updateButton.setDisable(true);
        updateButton.setText("Updating...");
        new Thread(this::downloadSnapshot).start();
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
            double localY = event.getY();
            if (localY < 6) {
                draggingWindow = false;
                return;
            }
            draggingWindow = true;
            xOffset = stage.getX() - event.getScreenX();
            yOffset = stage.getY() - event.getScreenY();
        }
    }

    @FXML
    private void onHeaderDragged(MouseEvent event) {
        var stage = getStage();
        if (stage != null && draggingWindow) {
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
                gameController = loader.getController();
                gameController.bindLcu(lcuSnapshot);
            } catch (Exception e) {
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
            e.printStackTrace();
        }
    }

    private void showHelpView() {
        Node view = getHelpView();
        if (view != null) {
            contentArea.getChildren().setAll(view);
        }
    }

    private Node getHelpView() {
        if (helpView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/fxml/help-view.fxml"));
                helpView = loader.load();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return helpView;
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
        File snapshotFile = AppPaths.snapshotPath().toFile();
        if (!snapshotFile.exists()) {
            footerLastUpdatedLabel.setText("Last updated: never");
            return;
        }
        Instant lastModified = Instant.ofEpochMilli(snapshotFile.lastModified());
        LocalDateTime localDateTime = LocalDateTime.ofInstant(lastModified, ZoneId.systemDefault());
        footerLastUpdatedLabel.setText("Last updated: " + FOOTER_TIME_FORMAT.format(localDateTime));
    }

    private void setActiveTab(Label activeTab) {
        Label[] tabs = {gameTab, championsTab, helpTab};
        for (Label tab : tabs) {
            if (tab == null) continue;
            tab.getStyleClass().remove(ACTIVE_TAB_CLASS);
        }
        if (activeTab != null && !activeTab.getStyleClass().contains(ACTIVE_TAB_CLASS)) {
            activeTab.getStyleClass().add(ACTIVE_TAB_CLASS);
        }
    }

    private void updatePatchVersion() {
        if (patchLabel == null) {
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/org/example/data/champion-map.json")) {
            if (is == null) {
                patchLabel.setText("Patch: Unknown");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(is);
            String version = rootNode.get("version").asText();
            patchLabel.setText("Patch: " + version);
        } catch (Exception e) {
            e.printStackTrace();
            patchLabel.setText("Patch: Error");
        }
    }

    private void checkAndUpdateStatus() {
        if ((updateAvailableLabel == null && updateButton == null) || REMOTE_DB_URL.isBlank()) {
            return;
        }

        new Thread(() -> {
            boolean updateFound = false;
            long remoteLastModified = -1L;
            try {
                File localDb = AppPaths.snapshotPath().toFile();
                long localLastModified = localDb.exists() ? localDb.lastModified() : 0;

                URL url = new URL(REMOTE_DB_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD"); 
                connection.connect();

                remoteLastModified = connection.getLastModified();
                
                if (remoteLastModified > localLastModified) {
                    updateFound = true;
                }
            } catch (IOException e) {
                System.err.println("Error checking for updates: " + e.getMessage());
            }

            final boolean finalUpdateFound = updateFound;
            final long remoteStamp = remoteLastModified;
            Platform.runLater(() -> updateUpdateUi(finalUpdateFound, remoteStamp));
        }).start();
    }

    private void updateUpdateUi(boolean available, long remoteStamp) {
        if (updateAvailableLabel != null) {
            updateAvailableLabel.setVisible(available);
            updateAvailableLabel.setManaged(available);
            if (available) {
                updateAvailableLabel.setText("Update Available!");
            }
        }
        if (updateButton != null) {
            updateButton.setVisible(available);
            updateButton.setManaged(available);
            if (available) {
                updateButton.setDisable(updateInProgress);
                if (!updateInProgress) {
                    updateButton.setText("Load new match database");
                }
            } else {
                updateButton.setDisable(false);
                updateButton.setText("Load new match database");
            }
        }
    }

    private void downloadSnapshot() {
        Path target = AppPaths.locateDataFile("snapshot.db");
        Path tempFile = null;
        try {
            Files.createDirectories(target.getParent());
            tempFile = Files.createTempFile("snapshot-update", ".db");
            URL url = new URL(REMOTE_DB_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            Platform.runLater(() -> {
                updateSnapshotTimestamp();
                updateUpdateUi(false, -1L);
            });
        } catch (Exception e) {
            System.err.println("Failed to update snapshot: " + e.getMessage());
            Platform.runLater(() -> {
                if (updateAvailableLabel != null) {
                    updateAvailableLabel.setText("Update failed. Try again.");
                    updateAvailableLabel.setVisible(true);
                    updateAvailableLabel.setManaged(true);
                }
                if (updateButton != null) {
                    updateButton.setDisable(false);
                    updateButton.setText("Retry update");
                }
            });
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
            updateInProgress = false;
            Platform.runLater(() -> {
                if (updateButton != null) {
                    updateButton.setDisable(false);
                    updateButton.setText("Update now");
                }
            });
            checkAndUpdateStatus();
        }
    }
}
