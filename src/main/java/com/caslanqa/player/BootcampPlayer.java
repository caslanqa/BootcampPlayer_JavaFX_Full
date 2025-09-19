package com.caslanqa.player;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class BootcampPlayer extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainView.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 700);
        stage.setTitle("Bootcamp Player (JavaFX)");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
