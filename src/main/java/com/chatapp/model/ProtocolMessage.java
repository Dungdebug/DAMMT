package com.chatapp.model;

import com.chatapp.protocol.MessageType;
import com.google.gson.JsonObject;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The core protocol message exchanged between Client and Server over TCP.
 *
 * <p>Every message in the system is a ProtocolMessage. It contains:</p>
 * <ul>
 *   <li>{@code type} — The message type (see {@link MessageType})</li>
 *   <li>{@code sender} — Username of the sender (null for server-originated messages)</li>
 *   <li>{@code receiver} — Username of the intended receiver (null for broadcasts)</li>
 *   <li>{@code messageId} — Unique identifier for tracking</li>
 *   <li>{@code timestamp} — Unix timestamp in milliseconds</li>
 *   <li>{@code data} — Flexible JSON payload carrying type-specific fields</li>
 * </ul>
 *
 * <p>The {@code data} field uses Gson's {@link JsonObject} for maximum flexibility —
 * different message types carry different payloads without needing separate classes
 * for each wire format.</p>
 *
 * <p>Wire format (with length-prefix framing):</p>
 * <pre>
 * [4-byte length][UTF-8 JSON of this object]
 * </pre>
 */
public class ProtocolMessage {

    private MessageType type;
    private String sender;
    private String receiver;
    private String messageId;
    private long timestamp;
    private JsonObject data;

    /**
     * Default constructor — initializes with empty data and current timestamp.
     */
    public ProtocolMessage() {
        this.data = new JsonObject();
        this.timestamp = System.currentTimeMillis();
        this.messageId = generateId();
    }

    /**
     * Creates a message with the specified type.
     *
     * @param type the message type
     */
    public ProtocolMessage(MessageType type) {
        this();
        this.type = type;
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a LOGIN message.
     */
    public static ProtocolMessage createLogin(String username) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.LOGIN);
        msg.getData().addProperty("username", username);
        return msg;
    }

    /**
     * Creates a CHAT_MESSAGE.
     */
    public static ProtocolMessage createChat(String sender, String receiver, String content) {
        ProtocolMessage msg = new ProtocolMessage(MessageType.CHAT_MESSAGE);
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.getData().addProperty("content", content);
        return msg;
    }

    /**
     * Creates a simple response message with a reason field.
     */
    public static ProtocolMessage createWithReason(MessageType type, String reason) {
        ProtocolMessage msg = new ProtocolMessage(type);
        msg.getData().addProperty("reason", reason);
        return msg;
    }

    // ==================== Getters & Setters ====================

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the flexible JSON data payload.
     * Lazily initializes to an empty JsonObject if null (e.g., after Gson deserialization).
     */
    public JsonObject getData() {
        if (data == null) {
            data = new JsonObject();
        }
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
    }

    // ==================== Utility ====================

    /**
     * Generates a short unique message ID using timestamp + random.
     */
    private static String generateId() {
        return Long.toHexString(System.currentTimeMillis())
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0xFFFF));
    }

    @Override
    public String toString() {
        return "ProtocolMessage{type=" + type +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}
