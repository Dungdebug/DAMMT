package com.chatapp.model;

/**
 * Metadata describing a file to be transferred between users.
 *
 * <p>Sent as part of FILE_REQUEST to inform the receiver about the file
 * before they decide to accept or reject. The SHA-256 hash is computed
 * by the sender before transfer and verified by the receiver after
 * transfer to ensure data integrity.</p>
 *
 * <p>Security note: The fileName is sanitized on the receiver side to
 * prevent path traversal attacks (e.g., "../../malicious.exe").</p>
 */
public class FileMetadata {

    /** Unique identifier for this transfer session */
    private String transferId;

    /** Original file name (will be sanitized on receiver) */
    private String fileName;

    /** File size in bytes */
    private long fileSize;

    /** SHA-256 hash of the file (hex string) — for integrity verification */
    private String sha256Hash;

    /** Username of the sender */
    private String sender;

    /** Username of the receiver */
    private String receiver;

    /** Timestamp when the transfer was initiated */
    private long timestamp;

    public FileMetadata() {
        // Default constructor for Gson deserialization
    }

    public FileMetadata(String transferId, String fileName, long fileSize,
                        String sha256Hash, String sender, String receiver) {
        this.transferId = transferId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sha256Hash = sha256Hash;
        this.sender = sender;
        this.receiver = receiver;
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== Getters & Setters ====================

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "transferId='" + transferId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                '}';
    }
}
