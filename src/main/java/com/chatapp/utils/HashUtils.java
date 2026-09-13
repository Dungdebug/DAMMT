package com.chatapp.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hash computation utilities for file integrity verification.
 *
 * <h3>Why SHA-256 for file transfer?</h3>
 * <p>When transmitting files over a network, data corruption can occur due to
 * network errors, buffer overflows, or incomplete transfers. Computing a
 * SHA-256 hash before and after transfer allows us to verify that the
 * received file is identical to the original.</p>
 *
 * <h3>Streaming approach:</h3>
 * <p>The hash is computed by reading the file in chunks (streaming), NOT by
 * loading the entire file into memory. This is critical for large files
 * that could exceed available RAM.</p>
 */
public final class HashUtils {

    /** Buffer size for reading file during hash computation (8 KB) */
    private static final int HASH_BUFFER_SIZE = 8192;

    private HashUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Computes the SHA-256 hash of a file using streaming (chunk-by-chunk reading).
     *
     * <p>The file is read in 8 KB chunks through a BufferedInputStream,
     * each chunk is fed to the MessageDigest, and the final hash is computed
     * when the entire file has been read. This uses O(8KB) RAM regardless
     * of file size.</p>
     *
     * @param file the file to hash
     * @return hex string of the SHA-256 hash (64 characters)
     * @throws IOException if the file cannot be read
     */
    public static String computeSHA256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Stream the file in chunks — never load entire file into RAM
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                byte[] buffer = new byte[HASH_BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            return bytesToHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies that a file matches an expected SHA-256 hash.
     *
     * @param file         the file to verify
     * @param expectedHash the expected SHA-256 hash (hex string)
     * @return true if the computed hash matches the expected hash
     * @throws IOException if the file cannot be read
     */
    public static boolean verifySHA256(File file, String expectedHash) throws IOException {
        String actualHash = computeSHA256(file);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes the byte array
     * @return hex string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
