package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        System.out.println("Launching JavaFX...");

        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/org/example/fxml/main-view.fxml"));

        System.out.println("Loading FXML: /org/example/fxml/main-view.fxml");
        Scene scene = new Scene(fxmlLoader.load(), 1400, 1000);
        System.out.println("FXML loaded into Scene");

        ThemeManager.setScene(scene);
        System.out.println("Scene registered in ThemeManager");

        ThemeManager.applyTheme("dark.css");
        System.out.println("Dark theme applied at startup");

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("League Assistant FX");
        stage.setScene(scene);
        stage.show();

        System.out.println("=== App Started Successfully ===");
    }

    public static void main(String[] args) {
        launch();
    }
}
