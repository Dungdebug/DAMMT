package com.chatapp.server;

import com.chatapp.protocol.ProtocolConstants;
import com.chatapp.utils.FileUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Main server application — the entry point for the Chat Server.
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   ServerSocket (port 5000)
 *        │
 *        │ accept() — blocks until a client connects
 *        ▼
 *   ExecutorService (Thread Pool)
 *        │
 *        │ submit(task)
 *        ▼
 *   ┌─ handleNewConnection() ──────────────────────────┐
 *   │                                                    │
 *   │  Read first byte (connection type):                │
 *   │                                                    │
 *   │  0x01 (CONTROL) → ClientHandler.run()             │
 *   │    - Login, chat, file handshake, heartbeat        │
 *   │                                                    │
 *   │  0x02 (FILE) → FileTransferManager                │
 *   │    - Binary file data relay                        │
 *   └────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Key MMT concepts demonstrated:</h3>
 * <ul>
 *   <li><b>ServerSocket</b>: listens on a TCP port, accepts incoming connections</li>
 *   <li><b>ExecutorService</b>: Thread Pool for concurrent client handling</li>
 *   <li><b>Connection type byte</b>: application-level protocol multiplexing</li>
 *   <li><b>Shutdown hook</b>: graceful server termination</li>
 * </ul>
 */
public class ServerApplication {

    private static final Logger LOGGER = ServerLogger.getLogger();

    private final int port;
    private final ExecutorService threadPool;
    private final ClientManager clientManager;
    private final SessionManager sessionManager;
    private final MessageRouter messageRouter;
    private final FileTransferManager fileTransferManager;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public ServerApplication(int port) {
        this.port = port;

        // CachedThreadPool: creates threads as needed, reuses idle threads
        // Suitable for I/O-bound tasks like socket handling
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        this.clientManager = new ClientManager();
        this.sessionManager = new SessionManager();
        this.messageRouter = new MessageRouter(clientManager);
        this.fileTransferManager = new FileTransferManager(clientManager, threadPool);
    }

    /**
     * Starts the server: creates ServerSocket and enters the accept loop.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        LOGGER.info("╔══════════════════════════════════════════╗");
        LOGGER.info("║        JAVA TCP CHAT SERVER              ║");
        LOGGER.info("╠══════════════════════════════════════════╣");
        LOGGER.info("║  Port:          " + port + "                       ║");
        LOGGER.info("║  Max file size: " + padRight(FileUtils.formatSize(ProtocolConstants.MAX_FILE_SIZE), 24) + "║");
        LOGGER.info("║  Chunk size:    " + padRight(FileUtils.formatSize(ProtocolConstants.CHUNK_SIZE), 24) + "║");
        LOGGER.info("║  Heartbeat:     " + padRight((ProtocolConstants.HEARTBEAT_INTERVAL / 1000) + "s", 24) + "║");
        LOGGER.info("╚══════════════════════════════════════════╝");
        LOGGER.info("Server started. Waiting for connections...");

        // Shutdown hook for clean shutdown (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received...");
            stop();
        }));

        // ==================== Accept Loop ====================
        // This is the core server loop: accept() blocks until a client connects,
        // then the connection is handed off to a thread from the pool.
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("New connection from: " +
                        clientSocket.getInetAddress().getHostAddress() + ":" +
                        clientSocket.getPort());

                // Handle each connection in a separate thread
                threadPool.submit(() -> handleNewConnection(clientSocket));

            } catch (IOException e) {
                if (running) {
                    LOGGER.warning("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles a new TCP connection.
     *
     * <p>Reads the first byte to determine the connection type:</p>
     * <ul>
     *   <li>0x01 (CONTROL): Creates a ClientHandler for login, chat, etc.</li>
     *   <li>0x02 (FILE): Passes to FileTransferManager for binary data relay</li>
     * </ul>
     */
    private void handleNewConnection(Socket socket) {
        try {
            // Wrap raw streams with buffered + data streams for efficient I/O
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));

            // Read the connection type identifier (first byte of application protocol)
            byte connectionType = in.readByte();

            if (connectionType == ProtocolConstants.CONNECTION_TYPE_CONTROL) {
                // === Control Connection ===
                // Create a ClientHandler and run it in the current pool thread
                ClientHandler handler = new ClientHandler(
                        socket, in, out,
                        clientManager, sessionManager,
                        messageRouter, fileTransferManager);
                handler.run(); // Blocking — runs receive loop until disconnect

            } else if (connectionType == ProtocolConstants.CONNECTION_TYPE_FILE) {
                // === File Transfer Connection ===
                // Read transfer identification
                String transferId = in.readUTF();
                String role = in.readUTF(); // "SENDER" or "RECEIVER"

                LOGGER.info("File connection: " + role + " for transfer " + transferId);
                fileTransferManager.handleFileConnection(socket, in, out, transferId, role);

            } else {
                LOGGER.warning("Unknown connection type: 0x" +
                        String.format("%02X", connectionType));
                socket.close();
            }

        } catch (IOException e) {
            LOGGER.warning("Error handling new connection: " + e.getMessage());
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Stops the server gracefully.
     */
    public void stop() {
        running = false;
        LOGGER.info("Server shutting down...");

        // Close server socket to stop accept loop
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        // Disconnect all clients
        clientManager.disconnectAll();

        // Shutdown thread pool
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }

        LOGGER.info("Server stopped.");
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    // ==================== Main Entry Point ====================

    public static void main(String[] args) {
        int port = ProtocolConstants.DEFAULT_PORT;

        // Load configuration from properties file
        try (InputStream is = ServerApplication.class.getResourceAsStream("/server.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                port = Integer.parseInt(
                        props.getProperty("server.port", String.valueOf(port)));
            }
        } catch (Exception e) {
            LOGGER.warning("Could not load server.properties, using defaults");
        }

        // Allow port override from command line: java ... ServerApplication 8080
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        // Start the server
        try {
            ServerApplication server = new ServerApplication(port);
            server.start();
        } catch (IOException e) {
            LOGGER.severe("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
