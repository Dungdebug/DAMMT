package com.chatapp.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File utility methods for safe file handling during transfers.
 *
 * <p>Key security concern: file names received from remote clients must be
 * sanitized to prevent path traversal attacks (e.g., "../../etc/passwd").
 * Only the base name is kept, and dangerous characters are removed.</p>
 */
public final class FileUtils {

    private FileUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Sanitizes a file name received from a remote client.
     *
     * <p>Removes path separators and dangerous characters to prevent
     * path traversal attacks. Only keeps the base file name.</p>
     *
     * @param fileName the raw file name from the network
     * @return sanitized file name safe for local storage
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unnamed_file";
        }

        // Extract just the file name (remove any path components)
        Path path = Paths.get(fileName);
        String baseName = path.getFileName().toString();

        // Remove any remaining path separators and dangerous characters
        baseName = baseName.replaceAll("[/\\\\:*?\"<>|]", "_");

        // Remove leading dots (prevents hidden files on Unix)
        baseName = baseName.replaceAll("^\\.+", "");

        // Ensure not empty after sanitization
        if (baseName.isBlank()) {
            baseName = "unnamed_file";
        }

        // Limit length
        if (baseName.length() > 255) {
            String ext = getExtension(baseName);
            baseName = baseName.substring(0, 255 - ext.length()) + ext;
        }

        return baseName;
    }

    /**
     * Gets the file extension including the dot.
     *
     * @param fileName the file name
     * @return extension (e.g., ".pdf") or empty string if none
     */
    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) return "";
        return fileName.substring(lastDot);
    }

    /**
     * Formats a byte count into a human-readable string.
     *
     * @param bytes the number of bytes
     * @return formatted string (e.g., "10.5 MB")
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Formats a speed in bytes/second to a human-readable string.
     *
     * @param bytesPerSecond the speed
     * @return formatted string (e.g., "4.8 MB/s")
     */
    public static String formatSpeed(double bytesPerSecond) {
        return formatSize((long) bytesPerSecond) + "/s";
    }

    /**
     * Formats remaining seconds into MM:SS or HH:MM:SS.
     *
     * @param seconds remaining seconds
     * @return formatted ETA string
     */
    public static String formatETA(long seconds) {
        if (seconds < 0) return "--:--";
        if (seconds < 60) return String.format("00:%02d", seconds);
        if (seconds < 3600) return String.format("%02d:%02d", seconds / 60, seconds % 60);
        return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /**
     * Generates a unique file path by appending a counter if the file already exists.
     *
     * @param directory the target directory
     * @param fileName  the desired file name
     * @return a File that does not already exist
     */
    public static File getUniqueFile(File directory, String fileName) {
        File file = new File(directory, fileName);
        if (!file.exists()) return file;

        String baseName = fileName;
        String ext = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = fileName.substring(0, lastDot);
            ext = fileName.substring(lastDot);
        }

        int counter = 1;
        while (file.exists()) {
            file = new File(directory, baseName + " (" + counter + ")" + ext);
            counter++;
        }
        return file;
    }
}
