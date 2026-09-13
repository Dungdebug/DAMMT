package com.chatapp.model;

/**
 * Represents the lifecycle states of a file transfer session.
 *
 * <p>State transition diagram:</p>
 * <pre>
 *   PENDING ──→ ACCEPTED ──→ TRANSFERRING ──→ COMPLETED
 *      │            │              │
 *      ├──→ REJECTED│              ├──→ FAILED
 *      │            │              │
 *      └──→ CANCELLED ←───────────┘
 * </pre>
 */
public enum TransferStatus {
    /** Request sent, waiting for receiver's response */
    PENDING,
    /** Receiver accepted, preparing to start transfer */
    ACCEPTED,
    /** Receiver rejected the file transfer */
    REJECTED,
    /** File data is actively being transmitted */
    TRANSFERRING,
    /** Transfer finished successfully and SHA-256 verified */
    COMPLETED,
    /** Transfer failed due to error or SHA-256 mismatch */
    FAILED,
    /** Transfer cancelled by sender or receiver */
    CANCELLED
}
