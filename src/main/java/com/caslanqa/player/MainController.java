package com.caslanqa.player;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import java.io.File;

public class MainController {

    // UI Components
    @FXML private SplitPane splitPane;
    @FXML private TreeView<String> playlistTree;
    @FXML private StackPane videoPane;
    @FXML private MediaView mediaView;
    @FXML private Button btnTogglePlaylist;
    @FXML private Button btnSelectFolder;
    @FXML private Button btnPlay;
    @FXML private Button btnPause;
    @FXML private Button btnStop;
    @FXML private Button btnSkipBack;     // NEW
    @FXML private Button btnSkipForward;  // NEW
    @FXML private Slider volumeSlider;
    @FXML private Slider speedSlider;
    @FXML private Slider progressSlider;
    @FXML private Label timeLabel;

    private String rootPath = null;
    private MediaPlayer mediaPlayer = null;
    private Timeline progressTimer;
    private boolean isSeeking = false;
    private boolean playlistVisible = true;

    @FXML
    private void initialize() {
        // Let SplitPane divider move freely in both directions
        playlistTree.setMinWidth(0);
        videoPane.setMinWidth(0);
        videoPane.setMinHeight(0);
        SplitPane.setResizableWithParent(playlistTree, true);
        SplitPane.setResizableWithParent(videoPane, true);

        mediaView.fitWidthProperty().bind(videoPane.widthProperty());
        mediaView.fitHeightProperty().bind(videoPane.heightProperty());
        mediaView.setPreserveRatio(true);

        if (btnSkipBack != null) {
            btnSkipBack.setOnAction(e -> skipBy(Duration.seconds(-10)));
        }
        if (btnSkipForward != null) {
            btnSkipForward.setOnAction(e -> skipBy(Duration.seconds(10)));
        }

        volumeSlider.setMin(0); volumeSlider.setMax(100); volumeSlider.setValue(70);
        speedSlider.setMin(50); speedSlider.setMax(300); speedSlider.setValue(100);
        progressSlider.setMin(0); progressSlider.setMax(1000); progressSlider.setValue(0);

        btnSelectFolder.setOnAction(e -> onSelectFolder());
        btnTogglePlaylist.setOnAction(e -> togglePlaylist());
        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());

        final Tooltip volumeTip = new Tooltip();
        final Tooltip speedTip = new Tooltip();
        final Tooltip progressTip = new Tooltip();

        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(newV.doubleValue() / 100.0);
            volumeTip.setText(String.format("%.0f%%", newV.doubleValue()));
        });
        volumeSlider.setOnMousePressed(e -> {
            volumeTip.setText(String.format("%.0f%%", volumeSlider.getValue()));
            volumeTip.show(volumeSlider, e.getScreenX(), e.getScreenY() - 30);
        });
        volumeSlider.setOnMouseDragged(e -> {
            volumeTip.setText(String.format("%.0f%%", volumeSlider.getValue()));
            volumeTip.show(volumeSlider, e.getScreenX(), e.getScreenY() - 30);
        });
        volumeSlider.setOnMouseReleased(e -> volumeTip.hide());

        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (mediaPlayer != null) mediaPlayer.setRate(newV.doubleValue() / 100.0);
            speedTip.setText(String.format("%.2fx", newV.doubleValue() / 100.0));
        });
        speedSlider.setOnMousePressed(e -> {
            speedTip.setText(String.format("%.2fx", speedSlider.getValue() / 100.0));
            speedTip.show(speedSlider, e.getScreenX(), e.getScreenY() - 30);
        });
        speedSlider.setOnMouseDragged(e -> {
            speedTip.setText(String.format("%.2fx", speedSlider.getValue() / 100.0));
            speedTip.show(speedSlider, e.getScreenX(), e.getScreenY() - 30);
        });
        speedSlider.setOnMouseReleased(e -> speedTip.hide());

        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            isSeeking = isChanging;
            if (!isChanging) seekToSlider();
        });
        progressSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!progressSlider.isValueChanging() && isSeeking) {
                seekToSlider();
                isSeeking = false;
            }
        });
        progressSlider.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                isSeeking = true;
                double percent = e.getX() / progressSlider.getWidth();
                percent = Math.max(0, Math.min(1, percent));
                progressSlider.setValue(percent * progressSlider.getMax());

                String tipText;
                if (mediaPlayer != null) {
                    Duration total = mediaPlayer.getTotalDuration();
                    if (total != null && !total.isUnknown() && total.greaterThan(Duration.ZERO)) {
                        long millis = (long) (total.toMillis() * percent);
                        tipText = PathUtils.formatTimeMillis(millis);
                    } else {
                        tipText = "00:00";
                    }
                } else {
                    tipText = "00:00";
                }
                progressTip.setText(tipText);
                progressTip.show(progressSlider, e.getScreenX(), e.getScreenY() - 30);

                seekToSlider();
                e.consume();
            }
        });
        progressSlider.setOnMouseDragged(e -> {
            double percent = e.getX() / progressSlider.getWidth();
            percent = Math.max(0, Math.min(1, percent));
            progressSlider.setValue(percent * progressSlider.getMax());

            String tipText;
            if (mediaPlayer != null) {
                Duration total = mediaPlayer.getTotalDuration();
                if (total != null && !total.isUnknown() && total.greaterThan(Duration.ZERO)) {
                    long millis = (long) (total.toMillis() * percent);
                    tipText = PathUtils.formatTimeMillis(millis);
                } else {
                    tipText = "00:00";
                }
            } else {
                tipText = "00:00";
            }
            progressTip.setText(tipText);
            progressTip.show(progressSlider, e.getScreenX(), e.getScreenY() - 30);
            e.consume();
        });
        progressSlider.setOnMouseReleased(e -> {
            progressTip.hide();
            seekToSlider();
            isSeeking = false;
        });

        playlistTree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                TreeItem<String> item = playlistTree.getSelectionModel().getSelectedItem();
                if (item != null && item.isLeaf() && rootPath != null) {
                    String path = PathUtils.buildFullPath(rootPath, item);
                    loadAndPlay(path);
                }
            }
        });

        btnPlay.setOnAction(e -> {
            TreeItem<String> item = playlistTree.getSelectionModel().getSelectedItem();
            if (item != null && item.isLeaf() && rootPath != null) {
                String path = PathUtils.buildFullPath(rootPath, item);
                if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
                    mediaPlayer.play();
                } else {
                    loadAndPlay(path);
                }
            }
        });

        progressTimer = new Timeline(new KeyFrame(Duration.millis(500), e -> updateProgress()));
        progressTimer.setCycleCount(Timeline.INDEFINITE);
        progressTimer.play();
    }

    private void onSelectFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Course Folder");
        File dir = chooser.showDialog(playlistTree.getScene().getWindow());
        if (dir != null) {
            rootPath = dir.getAbsolutePath();
            TreeItem<String> rootNode = PlaylistBuilder.buildPlaylist(rootPath);
            playlistTree.setRoot(rootNode);
            if (!playlistVisible) togglePlaylist();
        }
    }

    private void togglePlaylist() {
        if (playlistVisible) {
            playlistTree.setManaged(false);
            playlistTree.setVisible(false);
            splitPane.setDividerPositions(0.0);
            btnTogglePlaylist.setText("Show Playlist");
        } else {
            playlistTree.setManaged(true);
            playlistTree.setVisible(true);
            splitPane.setDividerPositions(0.2);
            btnTogglePlaylist.setText("Hide Playlist");
        }
        playlistVisible = !playlistVisible;
    }

    private void loadAndPlay(String filePath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }
            Media media = new Media(new File(filePath).toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            mediaPlayer.setRate(speedSlider.getValue() / 100.0);

            mediaPlayer.setOnReady(() -> {
                progressSlider.setValue(0);
                timeLabel.setText("00:00 / " + PathUtils.formatTimeMillis((long)mediaPlayer.getTotalDuration().toMillis()));
                play();
            });

            mediaPlayer.setOnEndOfMedia(this::stop);

        } catch (MediaException ex) {
            showAlert("Media Error", "Dosya açılamadı:" + filePath + " " + ex.getMessage());
        }
    }

    private void play() {
        if (mediaPlayer != null) mediaPlayer.play();
    }

    private void pause() {
        if (mediaPlayer != null) mediaPlayer.pause();
    }

    private void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            progressSlider.setValue(0);
        }
    }

    private void seekToSlider() {
        if (mediaPlayer == null) return;
        Duration total = mediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) return;
        double frac = progressSlider.getValue() / 1000.0;
        mediaPlayer.seek(total.multiply(frac));
    }

    private void updateProgress() {
        if (mediaPlayer == null) return;
        if (isSeeking) return;

        Duration current = mediaPlayer.getCurrentTime();
        Duration total = mediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) {
            timeLabel.setText("00:00 / 00:00");
            return;
        }
        double frac = current.toMillis() / total.toMillis();
        progressSlider.setValue(frac * 1000.0);

        String cur = PathUtils.formatTimeMillis((long) current.toMillis());
        String ttl = PathUtils.formatTimeMillis((long) total.toMillis());
        timeLabel.setText(cur + " / " + ttl);
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void skipBy(Duration delta) {
        if (mediaPlayer == null) return;
        Duration total = mediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) return;

        Duration current = mediaPlayer.getCurrentTime();
        Duration target = current.add(delta);

        if (target.lessThan(Duration.ZERO)) {
            target = Duration.ZERO;
        } else if (target.greaterThan(total)) {
            target = total;
        }

        mediaPlayer.seek(target);

        // Optional immediate UI feedback; timer will update too
        double frac = target.toMillis() / total.toMillis();
        progressSlider.setValue(frac * progressSlider.getMax());
    }
}
