package com.caslanqa.player;

import javafx.scene.control.TreeItem;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class PlaylistBuilder {

    public static TreeItem<String> buildPlaylist(String rootPath) {
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("Geçersiz dizin: " + rootPath);
        }

        TreeItem<String> rootNode = new TreeItem<>(rootDir.getName());
        rootNode.setExpanded(true);
        buildNodesRecursive(rootNode, rootDir);
        return rootNode;
    }

    private static void buildNodesRecursive(TreeItem<String> parentNode, File parentFile) {
        File[] files = parentFile.listFiles();
        if (files == null) return;

        File[] dirs = Arrays.stream(files)
                .filter(File::isDirectory)
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toArray(File[]::new);

        File[] videos = Arrays.stream(files)
                .filter(f -> f.isFile() && isVideo(f) && !f.isHidden())
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toArray(File[]::new);

        for (File dir : dirs) {
            TreeItem<String> dirNode = new TreeItem<>(dir.getName());
            dirNode.setExpanded(false);
            parentNode.getChildren().add(dirNode);
            buildNodesRecursive(dirNode, dir);
        }

        for (File video : videos) {
            TreeItem<String> videoNode = new TreeItem<>(video.getName());
            parentNode.getChildren().add(videoNode);
        }
    }

    private static boolean isVideo(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mkv")
                || lower.endsWith(".avi") || lower.endsWith(".mov");
    }
}
