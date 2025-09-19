module com.caslanqa.bootcampplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.graphics;

    opens com.caslanqa.player to javafx.fxml;
    exports com.caslanqa.player;
}
