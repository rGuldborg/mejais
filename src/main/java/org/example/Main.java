package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.util.WindowResizer;

public class Main extends Application {

    private StackPane contentWrapper;
    private StackPane windowRoot;
    private Rectangle contentClip;
    private Rectangle windowClip;
    private org.example.controller.MainController mainController;

    @Override
    public void start(Stage stage) throws Exception {


        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/org/example/fxml/main-view.fxml"));

        Parent mainContent = fxmlLoader.load();
        mainController = fxmlLoader.getController();

        contentWrapper = new StackPane(mainContent);
        contentWrapper.getStyleClass().add("app-window");
        contentClip = createClip();
        contentClip.widthProperty().bind(contentWrapper.widthProperty());
        contentClip.heightProperty().bind(contentWrapper.heightProperty());
        contentWrapper.setClip(contentClip);

        windowRoot = new StackPane(contentWrapper);
        windowRoot.getStyleClass().add("app-window-shadow");
        windowClip = createClip();
        windowClip.widthProperty().bind(windowRoot.widthProperty());
        windowClip.heightProperty().bind(windowRoot.heightProperty());
        windowRoot.setClip(windowClip);

        Scene scene = new Scene(windowRoot, 1400, 1000);
        scene.setFill(Color.TRANSPARENT);

        ThemeManager.setScene(scene);

        ThemeManager.applyTheme("dark.css");

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("mejais");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setResizable(true);
        WindowResizer.makeResizable(stage, windowRoot);
        updateWindowChrome(stage.isMaximized());
        stage.maximizedProperty().addListener((obs, oldVal, maximized) -> updateWindowChrome(maximized));
        stage.show();

    }

    @Override
    public void stop() {
        if (mainController != null) {
            mainController.stop();
        }
    }

    public static void main(String[] args) {
        launch();
    }

    private Rectangle createClip() {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        return clip;
    }

    private void updateWindowChrome(boolean maximized) {
        if (contentWrapper != null) {
            contentWrapper.setClip(maximized ? null : contentClip);
            toggleStyleClass(contentWrapper, "window-maximized", maximized);
        }
        if (windowRoot != null) {
            windowRoot.setClip(maximized ? null : windowClip);
            toggleStyleClass(windowRoot, "window-maximized", maximized);
        }
    }

    private void toggleStyleClass(Region node, String styleClass, boolean add) {
        if (node == null) return;
        ObservableList<String> styles = node.getStyleClass();
        if (add) {
            if (!styles.contains(styleClass)) {
                styles.add(styleClass);
            }
        } else {
            styles.remove(styleClass);
        }
    }
}
