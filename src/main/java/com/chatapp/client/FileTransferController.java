package com.chatapp.client;

import com.chatapp.model.FileMetadata;
import com.chatapp.model.ProtocolMessage;
import com.chatapp.model.TransferStatus;
import com.chatapp.protocol.MessageType;
import com.chatapp.protocol.ProtocolConstants;
import com.chatapp.utils.FileUtils;
import com.chatapp.utils.HashUtils;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

/**
 * Controls file transfer functionality: send requests, accept/reject, transfer data.
 *
 * <h3>Transfer flow (sender side):</h3>
 * <ol>
 *   <li>User selects file → {@link #sendFileRequest}</li>
 *   <li>Wait for FILE_ACCEPT → {@link #handleFileAccepted}</li>
 *   <li>Open file socket, stream chunks → {@link #startSendingFile}</li>
 * </ol>
 *
 * <h3>Transfer flow (receiver side):</h3>
 * <ol>
 *   <li>Receive FILE_REQUEST → {@link #handleIncomingFileRequest}</li>
 *   <li>User accepts → {@link #acceptTransfer}</li>
 *   <li>Open file socket, receive chunks → {@link #startReceivingFile}</li>
 *   <li>Verify SHA-256 → send FILE_COMPLETE</li>
 * </ol>
 */
public class FileTransferController {

    private static final Gson GSON = new Gson();

    private final ServerConnection connection;
    private final UserSession session;

    /** Active transfer info: transferId → TransferInfo */
    private final Map<String, TransferInfo> activeTransfers = new ConcurrentHashMap<>();

    /** Callback for UI updates */
    private FileTransferListener uiListener;

    /**
     * Internal class tracking state of an active file transfer.
     */
    public static class TransferInfo {
        public final String transferId;
        public final FileMetadata metadata;
        public File localFile;        // The file to send (sender) or save (receiver)
        public TransferStatus status;
        public volatile boolean cancelled = false;
        public boolean isSender;      // true if we are the sender

        public TransferInfo(String transferId, FileMetadata metadata, boolean isSender) {
            this.transferId = transferId;
            this.metadata = metadata;
            this.status = TransferStatus.PENDING;
            this.isSender = isSender;
        }
    }

    /**
     * Callback interface for file transfer UI events.
     */
    public interface FileTransferListener {
        void onFileRequestReceived(FileMetadata metadata);
        void onTransferStarted(String transferId, boolean isSender);
        void onTransferProgress(String transferId, int percent, long bytesTransferred,
                                long totalBytes, String fileName);
        void onTransferComplete(String transferId, boolean verified);
        void onTransferCancelled(String transferId, String reason);
        void onTransferError(String transferId, String error);
        void onTransferRejected(String transferId);
    }

    public FileTransferController(ServerConnection connection, UserSession session) {
        this.connection = connection;
        this.session = session;
    }

    public void setListener(FileTransferListener listener) {
        this.uiListener = listener;
    }

    // ==================== Sender Side ====================

    /**
     * Initiates a file transfer request to another user.
     *
     * <p>Computes SHA-256 hash, creates FILE_REQUEST, sends to server.</p>
     *
     * @param receiver the target user
     * @param file     the file to send
     * @throws IOException if file cannot be read or hash fails
     */
    public void sendFileRequest(String receiver, File file) throws IOException {
        // Validate file
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + file.getName());
        }
        if (!file.canRead()) {
            throw new IOException("Cannot read file: " + file.getName());
        }
        if (file.length() == 0) {
            throw new IOException("File is empty: " + file.getName());
        }
        if (file.length() > ProtocolConstants.MAX_FILE_SIZE) {
            throw new IOException("File too large: " + FileUtils.formatSize(file.length()) +
                    " (max: " + FileUtils.formatSize(ProtocolConstants.MAX_FILE_SIZE) + ")");
        }

        // Generate unique transfer ID
        String transferId = UUID.randomUUID().toString();

        // Compute SHA-256 hash for integrity verification
        String sha256 = HashUtils.computeSHA256(file);

        // Build file metadata
        FileMetadata metadata = new FileMetadata(
                transferId, file.getName(), file.length(),
                sha256, session.getUsername(), receiver);

        // Create and send FILE_REQUEST
        ProtocolMessage msg = new ProtocolMessage(MessageType.FILE_REQUEST);
        msg.setSender(session.getUsername());
        msg.setReceiver(receiver);
        msg.setData(GSON.toJsonTree(metadata).getAsJsonObject());

        connection.sendMessage(msg);

        // Track the transfer
        TransferInfo info = new TransferInfo(transferId, metadata, true);
        info.localFile = file;
        activeTransfers.put(transferId, info);
    }

    /**
     * Handles FILE_ACCEPT — begins sending the file.
     */
    public void handleFileAccepted(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        TransferInfo info = activeTransfers.get(transferId);
        if (info == null || !info.isSender) return;

        info.status = TransferStatus.ACCEPTED;

        if (uiListener != null) {
            SwingUtilities.invokeLater(() ->
                    uiListener.onTransferStarted(transferId, true));
        }

        // Start sending file in background thread
        startSendingFile(transferId);
    }

    /**
     * Sends file data to the server through a dedicated file socket.
     *
     * <p>The file is read and sent in chunks (64 KB default) to avoid
     * loading the entire file into memory. This is critical for large files.</p>
     */
    private void startSendingFile(String transferId) {
        TransferInfo info = activeTransfers.get(transferId);
        if (info == null) return;

        info.status = TransferStatus.TRANSFERRING;

        // File sending runs in a background thread — NOT on EDT
        Thread sendThread = new Thread(() -> {
            Socket fileSocket = null;
            try {
                // Open dedicated file transfer TCP connection
                fileSocket = connection.openFileConnection(transferId, "SENDER");
                fileSocket.setSendBufferSize(ProtocolConstants.CHUNK_SIZE * 2);
                fileSocket.setTcpNoDelay(true);

                DataOutputStream fileOut = new DataOutputStream(
                        new BufferedOutputStream(fileSocket.getOutputStream(), ProtocolConstants.CHUNK_SIZE));

                // Stream file in chunks
                try (FileInputStream fis = new FileInputStream(info.localFile);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {

                    byte[] buffer = new byte[ProtocolConstants.CHUNK_SIZE];
                    long totalSent = 0;
                    long totalSize = info.metadata.getFileSize();
                    int bytesRead;

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        if (info.cancelled) break;

                        // Write chunk: [4-byte chunk size][chunk data]
                        fileOut.writeInt(bytesRead);
                        fileOut.write(buffer, 0, bytesRead);
                        fileOut.flush();

                        totalSent += bytesRead;
                    }

                    // Write EOF marker: chunk size = 0
                    if (!info.cancelled) {
                        fileOut.writeInt(0);
                        fileOut.flush();
                    }
                }

            } catch (IOException e) {
                if (!info.cancelled) {
                    notifyError(transferId, "Send failed: " + e.getMessage());
                }
            } finally {
                if (fileSocket != null) {
                    try { fileSocket.close(); } catch (IOException ignored) {}
                }
            }
        }, "FileSender-" + transferId);

        sendThread.setDaemon(true);
        sendThread.start();
    }

    // ==================== Receiver Side ====================

    /**
     * Handles an incoming FILE_REQUEST from another user.
     */
    public void handleIncomingFileRequest(ProtocolMessage msg) {
        FileMetadata metadata = GSON.fromJson(msg.getData(), FileMetadata.class);
        metadata.setSender(msg.getSender());

        // Track the transfer as receiver
        TransferInfo info = new TransferInfo(metadata.getTransferId(), metadata, false);
        activeTransfers.put(metadata.getTransferId(), info);

        // Notify UI to show accept/reject dialog
        if (uiListener != null) {
            SwingUtilities.invokeLater(() ->
                    uiListener.onFileRequestReceived(metadata));
        }
    }

    /**
     * Accepts a file transfer — sends FILE_ACCEPT and starts receiving.
     */
    public void acceptTransfer(String transferId, File saveDirectory) {
        TransferInfo info = activeTransfers.get(transferId);
        if (info == null) return;

        info.status = TransferStatus.ACCEPTED;

        // Prepare save path
        String safeName = FileUtils.sanitizeFileName(info.metadata.getFileName());
        if (saveDirectory.isDirectory()) {
            info.localFile = FileUtils.getUniqueFile(saveDirectory, safeName);
        } else if (saveDirectory.isFile()) {
            info.localFile = saveDirectory;
        } else {
            File parent = saveDirectory.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (saveDirectory.getName().contains(".")) {
                info.localFile = saveDirectory;
            } else {
                saveDirectory.mkdirs();
                info.localFile = FileUtils.getUniqueFile(saveDirectory, safeName);
            }
        }

        // Send FILE_ACCEPT to server
        try {
            ProtocolMessage accept = new ProtocolMessage(MessageType.FILE_ACCEPT);
            accept.setSender(session.getUsername());
            accept.setReceiver(info.metadata.getSender());
            accept.getData().addProperty("transferId", transferId);
            connection.sendMessage(accept);
        } catch (IOException e) {
            notifyError(transferId, "Failed to accept: " + e.getMessage());
            return;
        }

        if (uiListener != null) {
            SwingUtilities.invokeLater(() ->
                    uiListener.onTransferStarted(transferId, false));
        }

        // Start receiving in background thread
        startReceivingFile(transferId);
    }

    /**
     * Rejects a file transfer — sends FILE_REJECT.
     */
    public void rejectTransfer(String transferId) {
        TransferInfo info = activeTransfers.get(transferId);
        if (info == null) return;

        info.status = TransferStatus.REJECTED;

        try {
            ProtocolMessage reject = new ProtocolMessage(MessageType.FILE_REJECT);
            reject.setSender(session.getUsername());
            reject.setReceiver(info.metadata.getSender());
            reject.getData().addProperty("transferId", transferId);
            connection.sendMessage(reject);
        } catch (IOException e) {
            notifyError(transferId, "Failed to reject: " + e.getMessage());
        }

        activeTransfers.remove(transferId);
    }

    /**
     * Receives file data from the server through a dedicated file socket.
     *
     * <p>File is written to a .part temporary file first. Only after SHA-256
     * verification succeeds is the file renamed to its final name. This
     * prevents partial/corrupted files from being mistaken as complete.</p>
     */
    private void startReceivingFile(String transferId) {
        TransferInfo info = activeTransfers.get(transferId);
        if (info == null) return;

        info.status = TransferStatus.TRANSFERRING;

        Thread receiveThread = new Thread(() -> {
            Socket fileSocket = null;
            File partFile = new File(info.localFile.getAbsolutePath() + ".part");

            try {
                // Open dedicated file transfer connection
                fileSocket = connection.openFileConnection(transferId, "RECEIVER");
                fileSocket.setReceiveBufferSize(ProtocolConstants.CHUNK_SIZE * 2);
                fileSocket.setTcpNoDelay(true);

                DataInputStream fileIn = new DataInputStream(
                        new BufferedInputStream(fileSocket.getInputStream(), ProtocolConstants.CHUNK_SIZE));

                // Create parent directory if needed
                info.localFile.getParentFile().mkdirs();

                long totalReceived = 0;
                long totalSize = info.metadata.getFileSize();
                long startTime = System.currentTimeMillis();

                // Write to .part file (renamed after verification)
                try (FileOutputStream fos = new FileOutputStream(partFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                    while (!info.cancelled) {
                        // Read chunk size
                        int chunkSize = fileIn.readInt();
                        if (chunkSize == 0) break; // EOF marker

                        if (chunkSize < 0 || chunkSize > ProtocolConstants.CHUNK_SIZE * 2) {
                            throw new IOException("Invalid chunk size: " + chunkSize);
                        }

                        // Read chunk data
                        byte[] chunk = new byte[chunkSize];
                        fileIn.readFully(chunk);

                        // Write to file
                        bos.write(chunk);

                        totalReceived += chunkSize;

                        // Report progress to UI
                        int progress = (int) (totalReceived * 100 / totalSize);
                        long elapsed = System.currentTimeMillis() - startTime;
                        final long received = totalReceived;
                        if (uiListener != null && elapsed > 0) {
                            SwingUtilities.invokeLater(() ->
                                    uiListener.onTransferProgress(transferId, progress,
                                            received, totalSize, info.metadata.getFileName()));
                        }
                    }

                    bos.flush();
                }

                // ==================== SHA-256 Verification ====================
                if (!info.cancelled) {
                    boolean verified = false;
                    String expectedHash = info.metadata.getSha256Hash();

                    if (expectedHash != null && !expectedHash.isEmpty()) {
                        // Compute SHA-256 of the received file
                        String actualHash = HashUtils.computeSHA256(partFile);
                        verified = actualHash.equalsIgnoreCase(expectedHash);

                        if (!verified) {
                            // Hash mismatch — file is corrupted
                            partFile.delete();
                            info.status = TransferStatus.FAILED;

                            // Notify server
                            ProtocolMessage errorMsg = new ProtocolMessage(MessageType.FILE_ERROR);
                            errorMsg.getData().addProperty("transferId", transferId);
                            errorMsg.getData().addProperty("reason", "SHA-256 mismatch — file corrupted");
                            connection.sendMessage(errorMsg);

                            notifyError(transferId, "File integrity check FAILED! SHA-256 mismatch.");
                            return;
                        }
                    } else {
                        verified = true; // No hash to verify against
                    }

                    // Rename .part to final file name
                    if (partFile.renameTo(info.localFile)) {
                        info.status = TransferStatus.COMPLETED;
                    } else {
                        // Rename failed — try copy
                        try (FileInputStream fis = new FileInputStream(partFile);
                             FileOutputStream fos = new FileOutputStream(info.localFile)) {
                            fis.transferTo(fos);
                        }
                        partFile.delete();
                        info.status = TransferStatus.COMPLETED;
                    }

                    // Send FILE_COMPLETE to server
                    ProtocolMessage complete = new ProtocolMessage(MessageType.FILE_COMPLETE);
                    complete.setSender(session.getUsername());
                    complete.getData().addProperty("transferId", transferId);
                    complete.getData().addProperty("verified", verified);
                    connection.sendMessage(complete);

                    // Notify UI
                    final boolean v = verified;
                    if (uiListener != null) {
                        SwingUtilities.invokeLater(() ->
                                uiListener.onTransferComplete(transferId, v));
                    }
                } else {
                    // Transfer was cancelled — delete partial file
                    partFile.delete();
                }

            } catch (IOException e) {
                if (!info.cancelled) {
                    partFile.delete();
                    notifyError(transferId, "Receive failed: " + e.getMessage());
                }
            } finally {
                if (fileSocket != null) {
                    try { fileSocket.close(); } catch (IOException ignored) {}
                }
            }
        }, "FileReceiver-" + transferId);

        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    // ==================== Cancel ====================

    /**
     * Cancels an active file transfer.
     */
    public void cancelTransfer(String transferId) {
        TransferInfo info = activeTransfers.get(transferId);
        if (info == null) return;

        info.cancelled = true;
        info.status = TransferStatus.CANCELLED;

        // Notify server
        try {
            ProtocolMessage cancel = new ProtocolMessage(MessageType.FILE_CANCEL);
            cancel.setSender(session.getUsername());
            cancel.getData().addProperty("transferId", transferId);
            connection.sendMessage(cancel);
        } catch (IOException ignored) {}

        activeTransfers.remove(transferId);
    }

    // ==================== Server Event Handlers ====================

    /** Handles FILE_REJECT from server. */
    public void handleFileRejected(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        activeTransfers.remove(transferId);
        if (uiListener != null) {
            SwingUtilities.invokeLater(() -> uiListener.onTransferRejected(transferId));
        }
    }

    /** Handles FILE_PROGRESS from server (for sender). */
    public void handleProgress(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        int progress = msg.getData().get("progress").getAsInt();
        long bytesTransferred = msg.getData().get("bytesTransferred").getAsLong();
        long totalBytes = msg.getData().get("totalBytes").getAsLong();
        String fileName = msg.getData().has("fileName")
                ? msg.getData().get("fileName").getAsString() : "";

        TransferInfo info = activeTransfers.get(transferId);
        if (info != null && info.isSender && uiListener != null) {
            SwingUtilities.invokeLater(() ->
                    uiListener.onTransferProgress(transferId, progress,
                            bytesTransferred, totalBytes, fileName));
        }
    }

    /** Handles FILE_COMPLETE from server (for sender). */
    public void handleTransferComplete(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        boolean verified = msg.getData().has("verified")
                && msg.getData().get("verified").getAsBoolean();

        TransferInfo info = activeTransfers.get(transferId);
        if (info != null && info.isSender) {
            info.status = TransferStatus.COMPLETED;
            activeTransfers.remove(transferId);
            if (uiListener != null) {
                SwingUtilities.invokeLater(() ->
                        uiListener.onTransferComplete(transferId, verified));
            }
        }
    }

    /** Handles FILE_CANCEL from server. */
    public void handleTransferCancelled(ProtocolMessage msg) {
        String transferId = msg.getData().get("transferId").getAsString();
        String reason = msg.getData().has("reason")
                ? msg.getData().get("reason").getAsString() : "Transfer cancelled";

        TransferInfo info = activeTransfers.get(transferId);
        if (info != null) {
            info.cancelled = true;
            info.status = TransferStatus.CANCELLED;
            activeTransfers.remove(transferId);

            if (uiListener != null) {
                SwingUtilities.invokeLater(() ->
                        uiListener.onTransferCancelled(transferId, reason));
            }
        }
    }

    /** Handles FILE_ERROR from server. */
    public void handleTransferError(ProtocolMessage msg) {
        String transferId = msg.getData().has("transferId")
                ? msg.getData().get("transferId").getAsString() : "unknown";
        String reason = msg.getData().has("reason")
                ? msg.getData().get("reason").getAsString() : "Unknown error";

        activeTransfers.remove(transferId);
        notifyError(transferId, reason);
    }

    private void notifyError(String transferId, String error) {
        if (uiListener != null) {
            SwingUtilities.invokeLater(() -> uiListener.onTransferError(transferId, error));
        }
    }

    public TransferInfo getTransfer(String transferId) {
        return activeTransfers.get(transferId);
    }
}
