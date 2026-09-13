package com.chatapp.server;

import com.chatapp.model.ProtocolMessage;
import com.chatapp.protocol.MessageSerializer;
import com.chatapp.protocol.MessageType;
import com.chatapp.protocol.ProtocolConstants;
import com.chatapp.utils.FileUtils;
import com.chatapp.utils.ValidationUtils;
import com.google.gson.JsonArray;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handles communication with a single connected client.
 *
 * <p>Each client gets its own ClientHandler running in a thread from the
 * server's ExecutorService (Thread Pool). This ensures one slow or
 * disconnected client does NOT block others.</p>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Receive loop: continuously read messages from the client's TCP stream</li>
 *   <li>Message dispatch: route each message to the appropriate handler method</li>
 *   <li>Heartbeat: send periodic PING, expect PONG, detect dead connections</li>
 *   <li>Cleanup: unregister on disconnect, notify other clients</li>
 * </ul>
 *
 * <h3>Threading model:</h3>
 * <pre>
 *   ServerSocket.accept() → new ClientHandler → ExecutorService.submit()
 *   → ClientHandler.run() [in pool thread]
 *   → receive loop (blocking read)
 * </pre>
 */
public class ClientHandler implements Runnable {

    private static final Logger LOGGER = ServerLogger.getLogger();

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ClientManager clientManager;
    private final SessionManager sessionManager;
    private final MessageRouter messageRouter;
    private final FileTransferManager fileTransferManager;

    /** Username of the logged-in user (null until LOGIN succeeds) */
    private String username;

    /** Flag to control the receive loop */
    private volatile boolean running = false;

    /** Timestamp of last received PONG (for heartbeat timeout detection) */
    private volatile long lastPongTime;

    /** Scheduler for periodic heartbeat PINGs */
    private ScheduledExecutorService heartbeatScheduler;

    public ClientHandler(Socket socket, DataInputStream in, DataOutputStream out,
                         ClientManager clientManager, SessionManager sessionManager,
                         MessageRouter messageRouter, FileTransferManager fileTransferManager) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.clientManager = clientManager;
        this.sessionManager = sessionManager;
        this.messageRouter = messageRouter;
        this.fileTransferManager = fileTransferManager;
    }

    /**
     * Main receive loop — runs in a thread from the ExecutorService.
     * Continuously reads messages from the client's TCP stream and dispatches
     * them to the appropriate handler method.
     */
    @Override
    public void run() {
        running = true;
        String clientAddr = socket.getInetAddress().getHostAddress();

        try {
            // Main receive loop: read one message at a time
            while (running) {
                ProtocolMessage msg = MessageSerializer.readMessage(in);
                handleMessage(msg);
            }
        } catch (EOFException e) {
            // Client closed connection normally
            LOGGER.info("Client disconnected (EOF): " +
                    (username != null ? username : clientAddr));
        } catch (SocketException e) {
            // Socket closed (possibly by shutdown or heartbeat timeout)
            if (running) {
                LOGGER.info("Client socket closed: " +
                        (username != null ? username : clientAddr) + " - " + e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                LOGGER.warning("Client I/O error: " +
                        (username != null ? username : clientAddr) + " - " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Dispatches a received message to the appropriate handler based on its type.
     */
    private void handleMessage(ProtocolMessage msg) {
        try {
            switch (msg.getType()) {
                case LOGIN       -> handleLogin(msg);
                case CHAT_MESSAGE -> handleChatMessage(msg);
                case FILE_REQUEST -> handleFileRequest(msg);
                case FILE_ACCEPT  -> handleFileAccept(msg);
                case FILE_REJECT  -> handleFileReject(msg);
                case FILE_CANCEL  -> handleFileCancel(msg);
                case FILE_COMPLETE -> handleFileComplete(msg);
                case FILE_ERROR   -> handleFileError(msg);
                case PONG         -> lastPongTime = System.currentTimeMillis();
                case DISCONNECT   -> { running = false; }
                default -> LOGGER.warning("Unhandled message type from " +
                        username + ": " + msg.getType());
            }
        } catch (Exception e) {
            LOGGER.warning("Error handling " + msg.getType() + " from " +
                    username + ": " + e.getMessage());
        }
    }

    // ==================== Login ====================

    private void handleLogin(ProtocolMessage msg) throws IOException {
        String requestedUsername = msg.getData().get("username").getAsString();

        // Validate username format
        String validationError = ValidationUtils.getUsernameError(requestedUsername);
        if (validationError != null) {
            sendLoginFailed(validationError);
            return;
        }

        // Try to create session (checks uniqueness atomically)
        String sessionError = sessionManager.createSession(requestedUsername);
        if (sessionError != null) {
            sendLoginFailed(sessionError);
            return;
        }

        // Login successful
        this.username = requestedUsername;
        clientManager.addClient(username, this);

        LOGGER.info("User logged in: " + username +
                " from " + socket.getInetAddress().getHostAddress());

        // Send LOGIN_SUCCESS with list of online users
        ProtocolMessage success = new ProtocolMessage(MessageType.LOGIN_SUCCESS);
        JsonArray usersArray = new JsonArray();
        for (String user : clientManager.getOnlineUsernames()) {
            if (!user.equals(username)) {
                usersArray.add(user);
            }
        }
        success.getData().add("onlineUsers", usersArray);
        sendMessage(success);

        // Broadcast to all other clients that this user is now online
        ProtocolMessage onlineMsg = new ProtocolMessage(MessageType.USER_ONLINE);
        onlineMsg.getData().addProperty("username", username);
        clientManager.broadcastExcept(onlineMsg, username);

        // Start heartbeat monitoring
        startHeartbeat();
    }

    private void sendLoginFailed(String reason) throws IOException {
        LOGGER.info("Login failed: " + reason);
        ProtocolMessage fail = new ProtocolMessage(MessageType.LOGIN_FAILED);
        fail.getData().addProperty("reason", reason);
        sendMessage(fail);
        running = false; // Close connection after failed login
    }

    // ==================== Chat ====================

    private void handleChatMessage(ProtocolMessage msg) {
        if (username == null) return; // Not logged in

        String receiver = msg.getReceiver();
        msg.setSender(username); // Override sender for security (don't trust client)
        msg.setTimestamp(System.currentTimeMillis());

        String content = msg.getData().has("content")
                ? msg.getData().get("content").getAsString() : "";

        LOGGER.info(username + " -> " + receiver + ": " + content);

        if (clientManager.isOnline(receiver)) {
            messageRouter.routeToUser(receiver, msg);

            // Send delivery confirmation to sender
            ProtocolMessage delivered = new ProtocolMessage(MessageType.CHAT_DELIVERED);
            delivered.getData().addProperty("messageId", msg.getMessageId());
            trySend(delivered);
        } else {
            // Receiver is offline — notify sender
            ProtocolMessage error = new ProtocolMessage(MessageType.CHAT_ERROR);
            error.getData().addProperty("reason",
                    "User '" + receiver + "' is offline");
            error.getData().addProperty("messageId", msg.getMessageId());
            trySend(error);
        }
    }

    // ==================== File Transfer ====================

    private void handleFileRequest(ProtocolMessage msg) {
        if (username == null) return;

        String receiver = msg.getReceiver();
        msg.setSender(username); // Security: override sender

        // Validate file size
        long fileSize = msg.getData().get("fileSize").getAsLong();
        if (fileSize > ProtocolConstants.MAX_FILE_SIZE) {
            ProtocolMessage error = new ProtocolMessage(MessageType.FILE_ERROR);
            error.getData().addProperty("reason", "File too large (max " +
                    FileUtils.formatSize(ProtocolConstants.MAX_FILE_SIZE) + ")");
            error.getData().addProperty("transferId",
                    msg.getData().get("transferId").getAsString());
            trySend(error);
            return;
        }

        if (fileSize <= 0) {
            ProtocolMessage error = new ProtocolMessage(MessageType.FILE_ERROR);
            error.getData().addProperty("reason", "Invalid file size");
            trySend(error);
            return;
        }

        // Check if receiver is online
        if (!clientManager.isOnline(receiver)) {
            ProtocolMessage error = new ProtocolMessage(MessageType.FILE_ERROR);
            error.getData().addProperty("reason",
                    "User '" + receiver + "' is offline");
            trySend(error);
            return;
        }

        // Create transfer session on server
        String transferId = msg.getData().get("transferId").getAsString();
        fileTransferManager.createSession(transferId, msg.getData(), username, receiver);

        LOGGER.info("File transfer request: " + username + " -> " + receiver +
                " [" + msg.getData().get("fileName").getAsString() +
                " " + FileUtils.formatSize(fileSize) + "]");

        // Forward the request to the receiver
        messageRouter.routeToUser(receiver, msg);
    }

    private void handleFileAccept(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        LOGGER.info("File transfer accepted: " + transferId + " by " + username);

        fileTransferManager.acceptTransfer(transferId);

        // Forward acceptance to the sender
        String sender = fileTransferManager.getSender(transferId);
        if (sender != null) {
            msg.setSender(username);
            messageRouter.routeToUser(sender, msg);
        }
    }

    private void handleFileReject(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        LOGGER.info("File transfer rejected: " + transferId + " by " + username);

        // Forward rejection to sender
        String sender = fileTransferManager.getSender(transferId);
        if (sender != null) {
            msg.setSender(username);
            messageRouter.routeToUser(sender, msg);
        }

        fileTransferManager.removeSession(transferId);
    }

    private void handleFileCancel(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        LOGGER.info("File transfer cancelled: " + transferId + " by " + username);
        fileTransferManager.cancelTransfer(transferId, username);
    }

    private void handleFileComplete(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        boolean verified = msg.getData().has("verified")
                && msg.getData().get("verified").getAsBoolean();

        LOGGER.info("File transfer completed: " + transferId +
                (verified ? " ✓ SHA-256 verified" : " (unverified)"));

        // Forward completion to the sender
        String sender = fileTransferManager.getSender(transferId);
        if (sender != null && !sender.equals(username)) {
            msg.setSender(username);
            messageRouter.routeToUser(sender, msg);
        }

        fileTransferManager.removeSession(transferId);
    }

    private void handleFileError(ProtocolMessage msg) {
        String transferId = msg.getData().has("transferId")
                ? msg.getData().get("transferId").getAsString() : "unknown";
        LOGGER.warning("File transfer error: " + transferId + " from " + username);

        // Forward error to the other party
        String sender = fileTransferManager.getSender(transferId);
        String receiver = fileTransferManager.getReceiver(transferId);
        String otherParty = username.equals(sender) ? receiver : sender;
        if (otherParty != null) {
            messageRouter.routeToUser(otherParty, msg);
        }

        fileTransferManager.removeSession(transferId);
    }

    // ==================== Heartbeat ====================

    /**
     * Starts periodic PING messages to detect dead connections.
     *
     * <p>The server sends PING every 30 seconds. If the client doesn't respond
     * with PONG within 60 seconds, the connection is considered dead and
     * is closed.</p>
     */
    private void startHeartbeat() {
        lastPongTime = System.currentTimeMillis();

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Heartbeat-" + username);
            t.setDaemon(true);
            return t;
        });

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                // Check if client responded to previous ping
                long timeSinceLastPong = System.currentTimeMillis() - lastPongTime;
                if (timeSinceLastPong > ProtocolConstants.HEARTBEAT_TIMEOUT) {
                    LOGGER.warning("Heartbeat timeout for " + username +
                            " (no PONG for " + (timeSinceLastPong / 1000) + "s)");
                    running = false;
                    try { socket.close(); } catch (IOException ignored) {}
                    return;
                }

                // Send PING
                sendMessage(new ProtocolMessage(MessageType.PING));

            } catch (IOException e) {
                LOGGER.fine("Heartbeat send failed for " + username);
            }
        }, ProtocolConstants.HEARTBEAT_INTERVAL,
           ProtocolConstants.HEARTBEAT_INTERVAL,
           TimeUnit.MILLISECONDS);
    }

    // ==================== Send & Cleanup ====================

    /**
     * Sends a message to this client via the control TCP connection.
     * Synchronized via MessageSerializer to prevent interleaved writes.
     */
    public void sendMessage(ProtocolMessage msg) throws IOException {
        MessageSerializer.writeMessage(out, msg);
    }

    /** Sends a message, silently ignoring IOException. */
    private void trySend(ProtocolMessage msg) {
        try { sendMessage(msg); } catch (IOException ignored) {}
    }

    /**
     * Gracefully shuts down this client handler.
     */
    public void shutdown() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Cleanup when the client disconnects (normally or due to error).
     * - Stop heartbeat
     * - Cancel active file transfers
     * - Unregister from session and client managers
     * - Broadcast USER_OFFLINE to other clients
     * - Close socket
     */
    private void cleanup() {
        running = false;

        // Stop heartbeat
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }

        if (username != null) {
            LOGGER.info("User disconnected: " + username);

            // Cancel any active file transfers involving this user
            fileTransferManager.cancelTransfersForUser(username);

            // Unregister
            sessionManager.removeSession(username);
            clientManager.removeClient(username);

            // Broadcast offline notification
            ProtocolMessage offlineMsg = new ProtocolMessage(MessageType.USER_OFFLINE);
            offlineMsg.getData().addProperty("username", username);
            clientManager.broadcast(offlineMsg);
        }

        // Close socket
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    // ==================== Getters ====================

    public String getUsername() {
        return username;
    }

    public boolean isRunning() {
        return running;
    }
}
