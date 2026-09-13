package com.chatapp.model;

/**
 * Represents a single chat message between two users.
 *
 * <p>Contains sender, receiver, content, and timestamp. Used for both
 * in-transit protocol messages and local chat history storage.</p>
 *
 * <p>JSON example on the wire:</p>
 * <pre>
 * {
 *   "sender": "Duong",
 *   "receiver": "An",
 *   "content": "Hello!",
 *   "timestamp": 1694534400000
 * }
 * </pre>
 */
public class ChatMessage {

    private String sender;
    private String receiver;
    private String content;
    private long timestamp;

    public ChatMessage() {
        // Default constructor for Gson deserialization
    }

    public ChatMessage(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== Getters & Setters ====================

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Checks if this message was sent by the given username.
     */
    public boolean isSentBy(String username) {
        return sender != null && sender.equals(username);
    }

    @Override
    public String toString() {
        return "ChatMessage{" + sender + " -> " + receiver + ": " + content + "}";
    }
}
