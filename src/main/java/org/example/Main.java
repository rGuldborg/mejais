package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.util.WindowResizer;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        System.out.println("Launching JavaFX...");

        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/org/example/fxml/main-view.fxml"));

        System.out.println("Loading FXML: /org/example/fxml/main-view.fxml");
        Parent mainContent = fxmlLoader.load();

        StackPane contentWrapper = new StackPane(mainContent);
        contentWrapper.getStyleClass().add("app-window");
        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        clip.widthProperty().bind(contentWrapper.widthProperty());
        clip.heightProperty().bind(contentWrapper.heightProperty());
        contentWrapper.setClip(clip);

        StackPane windowRoot = new StackPane(contentWrapper);
        windowRoot.getStyleClass().add("app-window-shadow");

        Scene scene = new Scene(windowRoot, 1400, 1000);
        scene.setFill(Color.TRANSPARENT);
        System.out.println("FXML loaded into Scene");

        ThemeManager.setScene(scene);
        System.out.println("Scene registered in ThemeManager");

        ThemeManager.applyTheme("dark.css");
        System.out.println("Dark theme applied at startup");

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("League Assistant FX");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setResizable(true);
        WindowResizer.makeResizable(stage, windowRoot);
        stage.show();

        System.out.println("=== App Started Successfully ===");
    }

    public static void main(String[] args) {
        launch();
    }
}
