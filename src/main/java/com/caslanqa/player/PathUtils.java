package com.caslanqa.player;

import javafx.scene.control.TreeItem;
import java.io.File;

public class PathUtils {

    /** Seçili TreeItem'dan tam dosya yolunu oluşturur. */
    public static String buildFullPath(String rootPath, TreeItem<String> node) {
        if (node == null) return null;

        StringBuilder pathBuilder = new StringBuilder(node.getValue());
        TreeItem<String> parent = node.getParent();

        // root TreeItem'ı bul
        TreeItem<String> rootNode = parent;
        while (rootNode != null && rootNode.getParent() != null) {
            rootNode = rootNode.getParent();
        }

        // root TreeItem label'ını eklemeden atağa kadar ilerle
        while (parent != null && parent != rootNode) {
            pathBuilder.insert(0, File.separator).insert(0, parent.getValue());
            parent = parent.getParent();
        }

        return rootPath + File.separator + pathBuilder;
    }

    /** ms -> "mm:ss" veya "hh:mm:ss" formatı */
    public static String formatTimeMillis(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
