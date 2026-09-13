package com.chatapp.protocol;

/**
 * Protocol-level constants shared between Client and Server.
 *
 * <p>These values define the TCP communication parameters including port numbers,
 * timeouts, buffer sizes, and connection type identifiers. Centralizing constants
 * prevents magic numbers and ensures consistency.</p>
 *
 * <p>Key networking concepts demonstrated:</p>
 * <ul>
 *   <li>Port: identifies the service on the server machine</li>
 *   <li>Timeout: prevents indefinite blocking on network I/O</li>
 *   <li>Connection type byte: application-level protocol for multiplexing
 *       control and file transfer on the same port</li>
 *   <li>Chunk size: enables file streaming without loading entire file into RAM</li>
 * </ul>
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
        // Utility class — prevent instantiation
    }

    // ==================== Network ====================

    /** Default TCP port for the chat server */
    public static final int DEFAULT_PORT = 5000;

    /** TCP connection timeout in milliseconds (5 seconds) */
    public static final int CONNECT_TIMEOUT = 5_000;

    /**
     * Socket read timeout. Set to 0 (infinite) because the heartbeat
     * mechanism handles dead connection detection instead.
     */
    public static final int READ_TIMEOUT = 0;

    // ==================== Connection Types ====================
    // First byte sent after TCP handshake to identify connection purpose.
    // This is an application-level protocol design: one port, multiple roles.

    /**
     * Control connection: carries JSON protocol messages
     * (login, chat, file handshake, heartbeat, notifications).
     */
    public static final byte CONNECTION_TYPE_CONTROL = 0x01;

    /**
     * File transfer connection: carries raw binary file data.
     * Separated from control to avoid mixing JSON text with binary streams.
     */
    public static final byte CONNECTION_TYPE_FILE = 0x02;

    // ==================== Message Limits ====================

    /**
     * Maximum size of a single JSON protocol message in bytes (1 MB).
     * Prevents memory exhaustion from malformed/malicious length prefixes.
     */
    public static final int MAX_MESSAGE_SIZE = 1_048_576;

    // ==================== File Transfer ====================

    /**
     * Chunk size for high-throughput file streaming in bytes (1 MB = 1,048,576 bytes).
     * Files are read and transmitted in chunks of this size to maximize throughput
     * while avoiding loading the entire file into RAM.
     */
    public static final int CHUNK_SIZE = 1_048_576; // 1 MB

    /**
     * Maximum allowed file size in bytes (2 GB = 2,147,483,648 bytes).
     * Validated before transfer begins to prevent unnecessary network usage.
     */
    public static final long MAX_FILE_SIZE = 2_147_483_648L; // 2 GB

    /** Timeout for file transfer socket connection (30 seconds) */
    public static final int FILE_CONNECT_TIMEOUT = 30_000;

    /** Timeout waiting for both sender and receiver to connect file sockets (30s) */
    public static final int FILE_HANDSHAKE_TIMEOUT = 30_000;

    // ==================== Heartbeat ====================

    /**
     * Interval between PING messages in milliseconds (30 seconds).
     * Server sends PING; client responds with PONG.
     */
    public static final long HEARTBEAT_INTERVAL = 30_000;

    /**
     * Maximum time to wait for PONG response before declaring connection dead
     * (60 seconds = 2 missed heartbeats).
     */
    public static final long HEARTBEAT_TIMEOUT = 60_000;

    // ==================== Username Validation ====================

    /** Minimum username length */
    public static final int MIN_USERNAME_LENGTH = 2;

    /** Maximum username length */
    public static final int MAX_USERNAME_LENGTH = 20;

    /**
     * Regex pattern for valid usernames: 2-20 alphanumeric characters or underscores.
     * Prevents injection attacks and ensures display compatibility.
     */
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]{2,20}$";

    // ==================== Progress Reporting ====================

    /** Minimum interval between progress updates in milliseconds */
    public static final long PROGRESS_UPDATE_INTERVAL = 300;
}
