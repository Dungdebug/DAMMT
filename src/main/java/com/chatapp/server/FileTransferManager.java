package com.chatapp.server;

import com.chatapp.model.ProtocolMessage;
import com.chatapp.model.TransferStatus;
import com.chatapp.protocol.MessageType;
import com.chatapp.protocol.ProtocolConstants;
import com.chatapp.utils.FileUtils;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Manages file transfer sessions on the server side.
 *
 * <h3>Architecture:</h3>
 * <p>File data flows through a <b>separate TCP connection</b> (file connection)
 * from the control connection. This separation avoids mixing binary file data
 * with JSON control messages in the same byte stream.</p>
 *
 * <h3>Transfer flow:</h3>
 * <ol>
 *   <li>Sender sends FILE_REQUEST via control → Server forwards to Receiver</li>
 *   <li>Receiver sends FILE_ACCEPT via control → Server forwards to Sender</li>
 *   <li>Both Sender and Receiver open new TCP connections (file connections)</li>
 *   <li>Server relays binary data: Sender → Server → Receiver (chunk by chunk)</li>
 *   <li>Server reports progress via control connection</li>
 *   <li>On completion, receiver verifies SHA-256 hash</li>
 * </ol>
 *
 * <h3>Why relay through server?</h3>
 * <p>Direct peer-to-peer would require NAT traversal (complex). Relaying through
 * the server is simpler and clearly demonstrates the Client-Server model
 * required for the Computer Networks course.</p>
 */
public class FileTransferManager {

    private static final Logger LOGGER = ServerLogger.getLogger();

    /** Active transfer sessions: transferId → session */
    private final ConcurrentHashMap<String, TransferSession> sessions = new ConcurrentHashMap<>();

    private final ClientManager clientManager;
    private final ExecutorService executorService;

    public FileTransferManager(ClientManager clientManager, ExecutorService executorService) {
        this.clientManager = clientManager;
        this.executorService = executorService;
    }

    /**
     * Internal class representing an active file transfer session.
     */
    static class TransferSession {
        final String transferId;
        final String sender;
        final String receiver;
        final long fileSize;
        final String fileName;
        final String sha256Hash;
        volatile TransferStatus status = TransferStatus.PENDING;

        // File transfer sockets (set when sender/receiver connect)
        volatile Socket senderFileSocket;
        volatile Socket receiverFileSocket;
        volatile boolean cancelled = false;

        TransferSession(String transferId, String sender, String receiver,
                        long fileSize, String fileName, String sha256Hash) {
            this.transferId = transferId;
            this.sender = sender;
            this.receiver = receiver;
            this.fileSize = fileSize;
            this.fileName = fileName;
            this.sha256Hash = sha256Hash;
        }

        synchronized void setSenderSocket(Socket s) {
            this.senderFileSocket = s;
        }

        synchronized void setReceiverSocket(Socket s) {
            this.receiverFileSocket = s;
        }

        synchronized boolean isReady() {
            return senderFileSocket != null && receiverFileSocket != null;
        }
    }

    /**
     * Creates a new transfer session when a FILE_REQUEST is received.
     */
    public void createSession(String transferId, JsonObject data,
                              String sender, String receiver) {
        long fileSize = data.get("fileSize").getAsLong();
        String fileName = data.get("fileName").getAsString();
        String sha256 = data.has("sha256Hash") ? data.get("sha256Hash").getAsString() : "";

        TransferSession session = new TransferSession(
                transferId, sender, receiver, fileSize, fileName, sha256);
        sessions.put(transferId, session);

        LOGGER.info("Transfer session created: " + transferId +
                " [" + fileName + " " + FileUtils.formatSize(fileSize) + "]");
    }

    /**
     * Marks a transfer as accepted.
     */
    public void acceptTransfer(String transferId) {
        TransferSession session = sessions.get(transferId);
        if (session != null) {
            session.status = TransferStatus.ACCEPTED;
        }
    }

    /**
     * Handles a new file transfer TCP connection from sender or receiver.
     *
     * <p>When a client connects with CONNECTION_TYPE_FILE, they send their
     * transferId and role (SENDER/RECEIVER). Once both parties have connected,
     * the server starts relaying file data.</p>
     */
    public void handleFileConnection(Socket socket, DataInputStream in,
                                     DataOutputStream out,
                                     String transferId, String role) {
        TransferSession session = sessions.get(transferId);
        if (session == null) {
            LOGGER.warning("File connection for unknown transfer: " + transferId);
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        if (session.cancelled) {
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        LOGGER.info("File connection: " + role + " connected for transfer " + transferId);

        if ("SENDER".equalsIgnoreCase(role)) {
            session.setSenderSocket(socket);
        } else if ("RECEIVER".equalsIgnoreCase(role)) {
            session.setReceiverSocket(socket);
        } else {
            LOGGER.warning("Unknown file connection role: " + role);
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        // Check if both parties are now connected
        if (session.isReady()) {
            session.status = TransferStatus.TRANSFERRING;
            LOGGER.info("Both parties connected — starting file relay for: " + transferId);
            // Start relay in current thread (already in thread pool)
            relayFileData(session);
        }
        // If only one party connected, the thread returns to pool.
        // The relay will start when the other party connects.
    }

    /**
     * Relays file data from sender to receiver through the server.
     *
     * <p>Data is read from the sender's file socket in chunks and immediately
     * written to the receiver's file socket. This streaming approach uses
     * only O(CHUNK_SIZE) RAM regardless of file size.</p>
     *
     * <p>Wire format for file chunks:</p>
     * <pre>
     *   [4-byte chunk length][chunk data bytes]   (repeated)
     *   [4-byte = 0]                              (EOF marker)
     * </pre>
     */
    private void relayFileData(TransferSession session) {
        try {
            session.senderFileSocket.setReceiveBufferSize(ProtocolConstants.CHUNK_SIZE * 2);
            session.receiverFileSocket.setSendBufferSize(ProtocolConstants.CHUNK_SIZE * 2);
            session.senderFileSocket.setTcpNoDelay(true);
            session.receiverFileSocket.setTcpNoDelay(true);
        } catch (IOException ignored) {}

        try (
            DataInputStream senderIn = new DataInputStream(
                    new BufferedInputStream(session.senderFileSocket.getInputStream(), ProtocolConstants.CHUNK_SIZE));
            DataOutputStream receiverOut = new DataOutputStream(
                    new BufferedOutputStream(session.receiverFileSocket.getOutputStream(), ProtocolConstants.CHUNK_SIZE))
        ) {
            long totalTransferred = 0;
            long lastProgressTime = 0;
            int lastReportedProgress = -1;

            LOGGER.info("File transfer started: " + session.sender + " -> " + session.receiver +
                    " [" + session.fileName + "]");

            // Relay loop: read chunks from sender, write to receiver
            while (!session.cancelled) {
                // Read chunk size from sender
                int chunkSize = senderIn.readInt();

                // EOF marker: chunk size = 0
                if (chunkSize == 0) {
                    receiverOut.writeInt(0); // Forward EOF to receiver
                    receiverOut.flush();
                    break;
                }

                // Validate chunk size
                if (chunkSize < 0 || chunkSize > ProtocolConstants.CHUNK_SIZE * 2) {
                    throw new IOException("Invalid chunk size: " + chunkSize);
                }

                // Read chunk data from sender
                byte[] chunk = new byte[chunkSize];
                senderIn.readFully(chunk);

                // Write chunk to receiver
                receiverOut.writeInt(chunkSize);
                receiverOut.write(chunk);
                receiverOut.flush();

                totalTransferred += chunkSize;

                // Send progress updates via control connection (throttled)
                long now = System.currentTimeMillis();
                if (now - lastProgressTime >= ProtocolConstants.PROGRESS_UPDATE_INTERVAL) {
                    int progress = (int) (totalTransferred * 100 / session.fileSize);
                    if (progress != lastReportedProgress) {
                        sendProgressUpdate(session, progress, totalTransferred);
                        lastReportedProgress = progress;
                        lastProgressTime = now;
                    }
                }
            }

            if (!session.cancelled) {
                session.status = TransferStatus.COMPLETED;
                LOGGER.info("File transfer completed: " + session.transferId +
                        " [" + FileUtils.formatSize(totalTransferred) + "]");

                // Send 100% progress
                sendProgressUpdate(session, 100, totalTransferred);
            }

        } catch (IOException e) {
            if (!session.cancelled) {
                LOGGER.warning("File transfer error: " + session.transferId + " - " + e.getMessage());
                session.status = TransferStatus.FAILED;

                // Notify both parties of the error
                ProtocolMessage errorMsg = new ProtocolMessage(MessageType.FILE_ERROR);
                errorMsg.getData().addProperty("transferId", session.transferId);
                errorMsg.getData().addProperty("reason", "Transfer failed: " + e.getMessage());
                clientManager.sendToUser(session.sender, errorMsg);
                clientManager.sendToUser(session.receiver, errorMsg);
            }
        } finally {
            // Close file sockets (control sockets remain open)
            closeQuietly(session.senderFileSocket);
            closeQuietly(session.receiverFileSocket);
        }
    }

    /**
     * Sends a progress update to both sender and receiver via their control connections.
     */
    private void sendProgressUpdate(TransferSession session, int progress, long bytesTransferred) {
        ProtocolMessage progressMsg = new ProtocolMessage(MessageType.FILE_PROGRESS);
        progressMsg.getData().addProperty("transferId", session.transferId);
        progressMsg.getData().addProperty("progress", progress);
        progressMsg.getData().addProperty("bytesTransferred", bytesTransferred);
        progressMsg.getData().addProperty("totalBytes", session.fileSize);
        progressMsg.getData().addProperty("fileName", session.fileName);

        clientManager.sendToUser(session.sender, progressMsg);
        clientManager.sendToUser(session.receiver, progressMsg);
    }

    /**
     * Cancels an active file transfer.
     */
    public void cancelTransfer(String transferId, String cancelledBy) {
        TransferSession session = sessions.get(transferId);
        if (session == null) return;

        session.cancelled = true;
        session.status = TransferStatus.CANCELLED;

        LOGGER.info("File transfer cancelled: " + transferId + " by " + cancelledBy);

        // Notify the other party
        ProtocolMessage cancelMsg = new ProtocolMessage(MessageType.FILE_CANCEL);
        cancelMsg.getData().addProperty("transferId", transferId);
        cancelMsg.getData().addProperty("reason",
                cancelledBy.equals(session.sender) ? "Sender cancelled the transfer"
                        : "Receiver cancelled the transfer");

        String otherParty = cancelledBy.equals(session.sender)
                ? session.receiver : session.sender;
        clientManager.sendToUser(otherParty, cancelMsg);

        // Close file sockets to interrupt the relay
        closeQuietly(session.senderFileSocket);
        closeQuietly(session.receiverFileSocket);

        sessions.remove(transferId);
    }

    /**
     * Cancels all transfers involving a specific user (called on disconnect).
     */
    public void cancelTransfersForUser(String username) {
        sessions.values().stream()
                .filter(s -> username.equals(s.sender) || username.equals(s.receiver))
                .forEach(s -> cancelTransfer(s.transferId, username));
    }

    /** Gets the sender of a transfer session. */
    public String getSender(String transferId) {
        TransferSession session = sessions.get(transferId);
        return session != null ? session.sender : null;
    }

    /** Gets the receiver of a transfer session. */
    public String getReceiver(String transferId) {
        TransferSession session = sessions.get(transferId);
        return session != null ? session.receiver : null;
    }

    /** Removes a completed/failed transfer session. */
    public void removeSession(String transferId) {
        sessions.remove(transferId);
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
