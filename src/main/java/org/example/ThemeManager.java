package org.example;

import javafx.application.Platform;
import javafx.scene.Scene;

public class ThemeManager {

    private static Scene scene;

    public static void setScene(Scene sc) {
        scene = sc;
    }

    public static void applyTheme(String cssFile) {
        if (scene == null) return;

        Platform.runLater(() -> {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(
                    ThemeManager.class.getResource("/org/example/css/" + cssFile).toExternalForm()
            );
        });
    }
}
