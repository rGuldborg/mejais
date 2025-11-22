package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        System.out.println("Launching JavaFX...");

        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/org/example/fxml/main-view.fxml"));

        System.out.println("Loading FXML: /org/example/fxml/main-view.fxml");
        Scene scene = new Scene(fxmlLoader.load(), 1200, 1000);
        System.out.println("FXML loaded into Scene");

        // Register scene
        ThemeManager.setScene(scene);
        System.out.println("Scene registered in ThemeManager");

        // ⭐ Apply DARK THEME as default (det er HER problemet var!)
        ThemeManager.applyTheme("dark.css");
        System.out.println("Dark theme applied at startup");

        stage.setTitle("League Assistant FX");
        stage.setScene(scene);
        stage.show();

        System.out.println("=== App Started Successfully ===");
    }

    public static void main(String[] args) {
        launch();
    }
}
