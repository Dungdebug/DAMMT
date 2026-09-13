package com.chatapp.model;

/**
 * Wraps a file transfer request with its metadata and current status.
 *
 * <p>Used on both Client (to track outgoing/incoming transfers) and Server
 * (to manage active transfer sessions). The status field tracks the
 * transfer lifecycle from PENDING through COMPLETED/FAILED/CANCELLED.</p>
 */
public class FileTransferRequest {

    /** The file metadata (name, size, hash, sender, receiver) */
    private FileMetadata metadata;

    /** Current status of this transfer */
    private TransferStatus status;

    /** Reason for failure/rejection/cancellation (if applicable) */
    private String reason;

    public FileTransferRequest() {
        // Default constructor for Gson deserialization
    }

    public FileTransferRequest(FileMetadata metadata, TransferStatus status) {
        this.metadata = metadata;
        this.status = status;
    }

    // ==================== Getters & Setters ====================

    public FileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(FileMetadata metadata) {
        this.metadata = metadata;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTransferId() {
        return metadata != null ? metadata.getTransferId() : null;
    }

    @Override
    public String toString() {
        return "FileTransferRequest{" +
                "transferId=" + getTransferId() +
                ", status=" + status +
                '}';
    }
}
