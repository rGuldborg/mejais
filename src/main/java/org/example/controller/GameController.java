package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Objects;

public class GameController {

    @FXML private ImageView allyBan1, allyBan2, allyBan3, allyBan4, allyBan5;
    @FXML private ImageView allyPick1, allyPick2, allyPick3, allyPick4, allyPick5;

    @FXML private ImageView enemyBan1, enemyBan2, enemyBan3, enemyBan4, enemyBan5;
    @FXML private ImageView enemyPick1, enemyPick2, enemyPick3, enemyPick4, enemyPick5;

    @FXML private ImageView mapView;

    @FXML private ListView<String> recommendedList;

    @FXML
    public void initialize() {
        System.out.println("[GameController] Loaded game-view!");

        mapView.setImage(loadImage("map.png"));

        loadDummyData();
    }

    private void loadDummyData() {
        Image dummy = loadImage("placeholder.png");

        allyBan1.setImage(dummy);
        allyBan2.setImage(dummy);
        allyBan3.setImage(dummy);
        allyBan4.setImage(dummy);
        allyBan5.setImage(dummy);

        allyPick1.setImage(dummy);
        allyPick2.setImage(dummy);
        allyPick3.setImage(dummy);
        allyPick4.setImage(dummy);
        allyPick5.setImage(dummy);

        // enemy
        enemyBan1.setImage(dummy);
        enemyBan2.setImage(dummy);
        enemyBan3.setImage(dummy);
        enemyBan4.setImage(dummy);
        enemyBan5.setImage(dummy);

        enemyPick1.setImage(dummy);
        enemyPick2.setImage(dummy);
        enemyPick3.setImage(dummy);
        enemyPick4.setImage(dummy);
        enemyPick5.setImage(dummy);

        recommendedList.getItems().addAll(
                "Anivia — S+ (OP)",
                "Rell — A (Synergy)",
                "Darius — B+ (Counter)"
        );
    }

    private Image loadImage(String name) {
        return new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/org/example/images/" + name))
        );
    }
}
