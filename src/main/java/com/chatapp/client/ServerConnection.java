package com.chatapp.client;

import com.chatapp.model.ProtocolMessage;
import com.chatapp.protocol.MessageSerializer;
import com.chatapp.protocol.MessageType;
import com.chatapp.protocol.ProtocolConstants;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Manages the TCP connection from client to server.
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   GUI (Swing EDT)
 *        │
 *        │ sendMessage() — non-blocking
 *        ▼
 *   ServerConnection
 *        │
 *        │ TCP Socket
 *        ▼
 *   Server
 * </pre>
 *
 * <h3>Threading model:</h3>
 * <ul>
 *   <li>Receive loop runs in a dedicated background thread (NOT on EDT)</li>
 *   <li>Send operations are synchronized to prevent interleaved writes</li>
 *   <li>Messages are dispatched to a {@link MessageListener} callback</li>
 * </ul>
 *
 * <h3>Key MMT concepts:</h3>
 * <ul>
 *   <li>TCP Socket connection with timeout</li>
 *   <li>Connection type byte (application protocol)</li>
 *   <li>Background receive loop for non-blocking GUI</li>
 *   <li>Length-prefix framing via {@link MessageSerializer}</li>
 * </ul>
 */
public class ServerConnection {

    private static final Logger LOGGER = Logger.getLogger("ChatClient");

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected = false;
    private MessageListener listener;
    private ExecutorService receiveExecutor;
    private String host;
    private int port;

    /**
     * Callback interface for received messages and connection events.
     */
    public interface MessageListener {
        /** Called when a message is received from the server. */
        void onMessageReceived(ProtocolMessage message);
        /** Called when the connection is lost. */
        void onDisconnected(String reason);
    }

    /**
     * Sets the message listener. Must be called BEFORE connect().
     */
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Connects to the server via TCP.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Create a TCP Socket</li>
     *   <li>Connect to server with timeout</li>
     *   <li>Wrap streams with Buffered + Data streams for efficient I/O</li>
     *   <li>Send connection type byte (0x01 = CONTROL)</li>
     *   <li>Start background receive loop</li>
     * </ol>
     *
     * @param host server IP address or hostname
     * @param port server TCP port
     * @throws IOException if connection fails
     */
    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        // Create TCP socket and connect with timeout
        socket = new Socket();
        socket.connect(
                new InetSocketAddress(host, port),
                ProtocolConstants.CONNECT_TIMEOUT);

        // Wrap with buffered streams for efficient I/O
        out = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));

        // Send connection type header (first byte of our application protocol)
        // This tells the server this is a CONTROL connection (not file transfer)
        out.writeByte(ProtocolConstants.CONNECTION_TYPE_CONTROL);
        out.flush();

        connected = true;

        // Start background receive loop
        receiveExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ReceiveLoop");
            t.setDaemon(true);
            return t;
        });
        receiveExecutor.submit(this::receiveLoop);

        LOGGER.info("Connected to server at " + host + ":" + port);
    }

    /**
     * Background receive loop — reads messages from the server continuously.
     *
     * <p>This runs in a dedicated thread, NOT on the Swing EDT, to prevent
     * blocking the GUI. When a message arrives, it's dispatched to the
     * registered MessageListener.</p>
     */
    private void receiveLoop() {
        while (connected) {
            try {
                // readMessage() blocks until a complete message arrives
                // (using length-prefix framing to handle TCP byte stream)
                ProtocolMessage msg = MessageSerializer.readMessage(in);

                // Handle PING automatically (heartbeat response)
                if (msg.getType() == MessageType.PING) {
                    sendMessage(new ProtocolMessage(MessageType.PONG));
                    continue;
                }

                // Dispatch to listener
                if (listener != null) {
                    listener.onMessageReceived(msg);
                }

            } catch (IOException e) {
                if (connected) {
                    connected = false;
                    LOGGER.warning("Connection lost: " + e.getMessage());
                    if (listener != null) {
                        listener.onDisconnected(e.getMessage());
                    }
                }
                break;
            }
        }
    }

    /**
     * Sends a protocol message to the server.
     * Thread-safe: synchronized inside MessageSerializer.
     *
     * @param message the message to send
     * @throws IOException if sending fails
     */
    public void sendMessage(ProtocolMessage message) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to server");
        }
        MessageSerializer.writeMessage(out, message);
    }

    /**
     * Opens a dedicated file transfer TCP connection to the server.
     *
     * <p>This creates a NEW socket (separate from the control connection)
     * for binary file data. The server identifies this connection by the
     * CONNECTION_TYPE_FILE byte followed by the transferId and role.</p>
     *
     * @param transferId the transfer session identifier
     * @param role       "SENDER" or "RECEIVER"
     * @return the connected file transfer socket
     * @throws IOException if connection fails
     */
    public Socket openFileConnection(String transferId, String role) throws IOException {
        Socket fileSocket = new Socket();
        fileSocket.connect(
                new InetSocketAddress(host, port),
                ProtocolConstants.FILE_CONNECT_TIMEOUT);

        DataOutputStream fileOut = new DataOutputStream(
                new BufferedOutputStream(fileSocket.getOutputStream()));

        // Send file connection header
        fileOut.writeByte(ProtocolConstants.CONNECTION_TYPE_FILE);
        fileOut.writeUTF(transferId);
        fileOut.writeUTF(role);
        fileOut.flush();

        return fileSocket;
    }

    /**
     * Disconnects from the server gracefully.
     */
    public void disconnect() {
        if (!connected) return;

        // Send DISCONNECT message before closing
        try {
            sendMessage(new ProtocolMessage(MessageType.DISCONNECT));
        } catch (IOException ignored) {}

        connected = false;

        // Close socket
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        // Shutdown receive thread
        if (receiveExecutor != null) {
            receiveExecutor.shutdownNow();
        }

        LOGGER.info("Disconnected from server");
    }

    // ==================== Getters ====================

    public boolean isConnected() {
        return connected;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
