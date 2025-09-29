package com.caslanqa.player;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.prefs.Preferences;

public class MainController {

    private Canvas audioVisualizerCanvas;
    private GraphicsContext gc;
    // UI Components
    @FXML
    private SplitPane splitPane;
    @FXML
    private TreeView<String> playlistTree;
    @FXML
    private StackPane videoPane;
    @FXML
    private MediaView mediaView;
    @FXML
    private Button btnTogglePlaylist;
    @FXML
    private Button btnSelectFolder;
    @FXML
    private Button btnPlay;
    @FXML
    private Button btnPause;
    @FXML
    private Button btnStop;
    @FXML
    private Button btnSkipBack;     // NEW
    @FXML
    private Button btnSkipForward;  // NEW
    @FXML
    private Slider volumeSlider;
    @FXML
    private Slider speedSlider;
    @FXML
    private Slider progressSlider;
    @FXML
    private Label timeLabel;
    @FXML private ToggleButton btnThemeToggle; // THEME TOGGLE
    @FXML private Label overlayIcon; // PLAY/PAUSE OVERLAY

    private String rootPath = null;
    private MediaPlayer mediaPlayer = null;
    private Timeline progressTimer;
    private boolean isSeeking = false;
    private boolean playlistVisible = true;
    private Stage stage;

    private static final String PREF_KEY_THEME = "theme";
    private static final String THEME_DARK = "dark";
    private static final String THEME_LIGHT = "light";
    private static final String DARK_CSS = "/css/dark.css";
    private static final String LIGHT_CSS = "/css/light.css";

    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    private static final double[] PRESET_SPEEDS = {1.0, 1.25, 1.5, 1.75, 2.0};
    private static final double SNAP_THRESHOLD = 0.035; // 3.5% yakınsa preset'e yapış

    private float[] previousMagnitudes; // Smoothing için
    private static final double SMOOTHING_ALPHA = 0.45; // Yeni değere ağırlık
    private static final double DECAY_FACTOR = 0.08; // Sessizlikte yavaş düşüş

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

        // Hız slider yeniden yapılandırma: 1.0 - 2.0 arası
        speedSlider.setMin(0.0);
        speedSlider.setMax(3.0);
        speedSlider.setValue(1.0);
        speedSlider.setShowTickMarks(true);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(0.25);
        speedSlider.setMinorTickCount(0);
        speedSlider.setSnapToTicks(false); // Manuel snapping

        mediaView.setOnMouseEntered(e -> showOverlayTemporary());
        mediaView.setOnMouseMoved(e -> showOverlayTemporary());
        mediaView.setOnMouseExited(e -> hideOverlay());

        mediaView.setOnMouseClicked(e -> {
            if (mediaPlayer != null) {
                MediaPlayer.Status status = mediaPlayer.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                } else if (status == MediaPlayer.Status.PAUSED || status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.READY) {
                    mediaPlayer.play();
                }
                updateOverlaySymbol();
                showOverlayTemporary();
            }
        });

        if (btnSkipBack != null) btnSkipBack.setOnAction(e -> skipBy(Duration.seconds(-10)));
        if (btnSkipForward != null) btnSkipForward.setOnAction(e -> skipBy(Duration.seconds(10)));

        volumeSlider.setMin(0);
        volumeSlider.setMax(100);
        volumeSlider.setValue(70);
        progressSlider.setMin(0);
        progressSlider.setMax(1000);
        progressSlider.setValue(0);

        btnSelectFolder.setOnAction(e -> onSelectFolder());
        btnTogglePlaylist.setOnAction(e -> togglePlaylist());
        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());

        // Tooltip & erişilebilirlik etiketleri
        btnPlay.setTooltip(new Tooltip("Play"));
        btnPause.setTooltip(new Tooltip("Pause"));
        btnStop.setTooltip(new Tooltip("Stop"));
        btnSkipBack.setTooltip(new Tooltip("10 saniye geri"));
        btnSkipForward.setTooltip(new Tooltip("10 saniye ileri"));

        btnPlay.setAccessibleText("Play Button");
        btnPause.setAccessibleText("Pause Button");
        btnStop.setAccessibleText("Stop Button");

        final Tooltip volumeTip = new Tooltip();
        final Tooltip speedTip = new Tooltip();
        final Tooltip progressTip = new Tooltip();

        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(newV.doubleValue() / 100.0);
            volumeTip.setText(String.format("%.0f%%", newV.doubleValue()));
        });
        volumeSlider.setOnMousePressed(e -> { volumeTip.setText(String.format("%.0f%%", volumeSlider.getValue())); volumeTip.show(volumeSlider, e.getScreenX(), e.getScreenY() - 30); });
        volumeSlider.setOnMouseDragged(e -> { volumeTip.setText(String.format("%.0f%%", volumeSlider.getValue())); volumeTip.show(volumeSlider, e.getScreenX(), e.getScreenY() - 30); });
        volumeSlider.setOnMouseReleased(e -> volumeTip.hide());

        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double val = newV.doubleValue();
            for (double preset : PRESET_SPEEDS) {
                if (Math.abs(val - preset) < SNAP_THRESHOLD) { val = preset; speedSlider.setValue(preset); break; }
            }
            if (mediaPlayer != null) mediaPlayer.setRate(val);
            speedTip.setText(String.format("%.2fx", val));
        });
        speedSlider.setOnMousePressed(e -> { speedTip.setText(String.format("%.2fx", speedSlider.getValue())); speedTip.show(speedSlider, e.getScreenX(), e.getScreenY() - 30); });
        speedSlider.setOnMouseDragged(e -> { speedTip.setText(String.format("%.2fx", speedSlider.getValue())); speedTip.show(speedSlider, e.getScreenX(), e.getScreenY() - 30); });
        speedSlider.setOnMouseReleased(e -> speedTip.hide());

        // Context menu presetleri
        ContextMenu speedMenu = new ContextMenu();
        for (double preset : PRESET_SPEEDS) {
            MenuItem mi = new MenuItem(String.format("%.2fx", preset));
            mi.setOnAction(e -> { speedSlider.setValue(preset); if (mediaPlayer != null) mediaPlayer.setRate(preset); });
            speedMenu.getItems().add(mi);
        }
        speedSlider.setOnContextMenuRequested(e -> speedMenu.show(speedSlider, e.getScreenX(), e.getScreenY()));

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

                    stage.setTitle("Bootcamp Player (JavaFX)");
                    String itemValue = item.getValue().split("\\.")[0];
                    stage.setTitle(stage.getTitle() + " - " + itemValue);

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
                    stop();
                    loadAndPlay(path);
                }
            }
        });

        // Theme toggle handler (will run after scene is ready via Platform.runLater in setStage)
        if (btnThemeToggle != null) {
            btnThemeToggle.setOnAction(e -> {
                boolean dark = btnThemeToggle.isSelected();
                applyTheme(dark ? THEME_DARK : THEME_LIGHT, true);
            });
        }

        progressTimer = new Timeline(new KeyFrame(Duration.millis(500), e -> updateProgress()));
        progressTimer.setCycleCount(Timeline.INDEFINITE);
        progressTimer.play();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        // Scene set edildikten sonra temayı uygula
        Platform.runLater(this::initThemeFromPreferences);
    }

    private void initThemeFromPreferences() {
        String saved = prefs.get(PREF_KEY_THEME, null);
        if (saved == null) { // sistem temasını algıla (#7)
            saved = detectSystemTheme();
            prefs.put(PREF_KEY_THEME, saved);
        }
        boolean dark = THEME_DARK.equals(saved);
        if (btnThemeToggle != null) btnThemeToggle.setSelected(dark);
        applyTheme(saved, false);
    }

    private void applyTheme(String theme, boolean persist) {
        if (stage == null || stage.getScene() == null) return;
        var stylesheets = stage.getScene().getStylesheets();
        stylesheets.removeIf(s -> s.endsWith("dark.css") || s.endsWith("light.css"));
        if (THEME_DARK.equals(theme)) {
            stylesheets.add(getClass().getResource(DARK_CSS).toExternalForm());
            if (btnThemeToggle != null) btnThemeToggle.setText("Light Mode");
        } else {
            stylesheets.add(getClass().getResource(LIGHT_CSS).toExternalForm());
            if (btnThemeToggle != null) btnThemeToggle.setText("Dark Mode");
        }
        if (persist) {
            prefs.put(PREF_KEY_THEME, theme);
        }
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
            if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); }
            Media media = new Media(new File(filePath).toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            // Her yeni medya başladığında hız 1.0x'e resetlensin
            speedSlider.setValue(1.0);

            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            mediaPlayer.setRate(speedSlider.getValue());
            mediaPlayer.setOnReady(() -> {
                stop();
                progressSlider.setValue(0);
                timeLabel.setText("00:00 / " + PathUtils.formatTimeMillis((long) mediaPlayer.getTotalDuration().toMillis()));
                if (filePath.endsWith(".mp3")) { setupAudioVisualizer(mediaPlayer); } else { clearAudioSpectrum(); }
                play();
            });
            mediaPlayer.setOnEndOfMedia(this::playNextMedia);
        } catch (MediaException ex) { showAlert("Media Error", "Dosya açılamadı:" + filePath + " " + ex.getMessage()); }
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

    private void playNextMedia() {
        if (rootPath == null) return;

        TreeItem<String> currentItem = playlistTree.getSelectionModel().getSelectedItem();
        if (currentItem == null || !currentItem.isLeaf()) return;

        TreeItem<String> parent = currentItem.getParent();
        if (parent == null) return;

        // Find current item index in parent's children
        int currentIndex = parent.getChildren().indexOf(currentItem);
        if (currentIndex == -1) return;

        // Look for next media file
        for (int i = currentIndex + 1; i < parent.getChildren().size(); i++) {
            TreeItem<String> nextItem = parent.getChildren().get(i);
            if (nextItem.isLeaf()) {
                // Found next media file
                playlistTree.getSelectionModel().select(nextItem);
                String nextPath = PathUtils.buildFullPath(rootPath, nextItem);
                loadAndPlay(nextPath);
                return;
            }
        }
    }

    private void initializeAudioVisualizer() {
        audioVisualizerCanvas = new Canvas();
        audioVisualizerCanvas.widthProperty().bind(mediaView.fitWidthProperty());
        audioVisualizerCanvas.heightProperty().bind(mediaView.fitHeightProperty());
        gc = audioVisualizerCanvas.getGraphicsContext2D();
        previousMagnitudes = null; // reset smoothing
    }

    private void setupAudioVisualizer(MediaPlayer mediaPlayer) {
        // Eski MediaPlayer'ın listener'ını temizle
        if (this.mediaPlayer != null)
            this.mediaPlayer.setAudioSpectrumListener(null);


        clearAudioSpectrum();

        // Sadece ses dosyalarında görselleştirici ekle
        Object videoMeta = mediaPlayer.getMedia().getMetadata().get("video");
        if (videoMeta == null || Boolean.FALSE.equals(videoMeta)) {
            // Yeni canvas oluştur
            initializeAudioVisualizer();

            StackPane stackPane = (StackPane) mediaView.getParent();
            if (stackPane != null) {
                stackPane.getChildren().add(audioVisualizerCanvas);
            }

            // Yeni MediaPlayer için listener ayarla
            mediaPlayer.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
                Platform.runLater(() -> {
                    drawAudioSpectrum(magnitudes);
                });
            });

            mediaPlayer.setAudioSpectrumNumBands(64);
            mediaPlayer.setAudioSpectrumInterval(0.05);
        } else {
            mediaPlayer.setAudioSpectrumListener(null);
        }
    }

    private void clearAudioSpectrum() {
        if (audioVisualizerCanvas != null) {
            gc.clearRect(0, 0, audioVisualizerCanvas.getWidth(), audioVisualizerCanvas.getHeight());

            StackPane stackPane = (StackPane) mediaView.getParent();
            if (stackPane != null) {
                stackPane.getChildren().remove(audioVisualizerCanvas);
            }
            previousMagnitudes = null; // smoothing reset
        }
    }

    private void drawAudioSpectrum(float[] magnitudes) {
        double width = audioVisualizerCanvas.getWidth();
        double height = audioVisualizerCanvas.getHeight();
        if (width <= 0 || height <= 0) return;
        if (previousMagnitudes == null || previousMagnitudes.length != magnitudes.length) {
            previousMagnitudes = new float[magnitudes.length];
            System.arraycopy(magnitudes, 0, previousMagnitudes, 0, magnitudes.length);
        }
        gc.clearRect(0, 0, width, height);
        double barWidth = width / magnitudes.length;
        for (int i = 0; i < magnitudes.length; i++) {
            float raw = magnitudes[i];
            // Sessizlikte decay uygula
            if (raw < -60) raw = (float)(previousMagnitudes[i] - DECAY_FACTOR * 60.0);
            // Smoothing
            float smoothed = (float)(previousMagnitudes[i] * (1.0 - SMOOTHING_ALPHA) + raw * SMOOTHING_ALPHA);
            previousMagnitudes[i] = smoothed;
            float magnitude = smoothed + 60; // normalize to 0..60
            magnitude = Math.max(0, magnitude);
            double normalizedMagnitude = magnitude / 60.0;
            double barHeight = normalizedMagnitude * height * 0.85;
            double x = i * barWidth;
            double y = height - barHeight;
            double intensity = Math.min(1.0, normalizedMagnitude * 2);
            gc.setFill(Color.hsb(180 + intensity * 60, 1.0, 0.65 + intensity * 0.35));
            gc.fillRect(x, y, barWidth - 1, barHeight);
        }
    }

    private void updateOverlaySymbol() {
        if (overlayIcon == null) return;
        if (mediaPlayer == null) {
            overlayIcon.setText("▶");
            return;
        }
        MediaPlayer.Status st = mediaPlayer.getStatus();
        switch (st) {
            case PLAYING -> overlayIcon.setText("❚❚");
            case PAUSED, READY, STOPPED -> overlayIcon.setText("▶");
            case HALTED -> overlayIcon.setText("!");
            default -> overlayIcon.setText("▶");
        }
    }

    private javafx.animation.PauseTransition overlayHideDelay;

    private void showOverlayTemporary() {
        if (overlayIcon == null) return;
        updateOverlaySymbol();
        if (overlayHideDelay != null) overlayHideDelay.stop();
        if (overlayIcon.getOpacity() < 1.0) {
            var ft = new javafx.animation.FadeTransition(Duration.millis(160), overlayIcon);
            overlayIcon.setVisible(true);
            overlayIcon.setManaged(false);
            ft.setFromValue(overlayIcon.getOpacity());
            ft.setToValue(1.0);
            ft.play();
        }
        overlayHideDelay = new javafx.animation.PauseTransition(Duration.seconds(1.6));
        overlayHideDelay.setOnFinished(ev -> {
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                hideOverlay();
            }
        });
        overlayHideDelay.play();
    }

    private void hideOverlay() {
        if (overlayIcon == null) return;
        if (mediaPlayer != null && mediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) return; // Pause'da görünür kalsın
        var ft = new javafx.animation.FadeTransition(Duration.millis(180), overlayIcon);
        ft.setFromValue(overlayIcon.getOpacity());
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            if (overlayIcon.getOpacity() == 0.0) overlayIcon.setVisible(false);
        });
        ft.play();
    }

    private String detectSystemTheme() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                Process p = new ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle").start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = br.readLine();
                    if (line != null && line.toLowerCase().contains("dark")) {
                        return THEME_DARK;
                    }
                }
            } else if (os.contains("win")) {
                // Windows 10/11 registry sorgusu eklenebilir; basit varsayım: light
                // Gelişmiş kullanım için JNA ile HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize okunabilir.
                return THEME_LIGHT;
            } else {
                // Linux masaüstü ortamlarında genellikle GTK theme okunur; basit fallback
                String gtkTheme = System.getenv("GTK_THEME");
                if (gtkTheme != null && gtkTheme.toLowerCase().contains("dark")) return THEME_DARK;
            }
        } catch (Exception ignored) { }
        return THEME_LIGHT;
    }
}
