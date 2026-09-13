package com.chatapp.client;

import com.chatapp.model.ChatMessage;
import com.chatapp.model.ProtocolMessage;
import com.chatapp.protocol.MessageType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controls chat functionality: sending/receiving messages and managing history.
 *
 * <p>Acts as the Controller in the MVC pattern:</p>
 * <pre>
 *   ChatPanel (View) ←→ ChatController ←→ ServerConnection (Network)
 * </pre>
 *
 * <p>Chat history is stored per-user in memory using a ConcurrentHashMap
 * with CopyOnWriteArrayList values for thread safety.</p>
 */
public class ChatController {

    private final ServerConnection connection;
    private final UserSession session;

    /** Chat history per user: username → list of messages */
    private final Map<String, List<ChatMessage>> chatHistory = new ConcurrentHashMap<>();

    /** Callback for UI updates */
    private ChatListener uiListener;

    /**
     * Callback interface for chat events.
     */
    public interface ChatListener {
        void onMessageReceived(ChatMessage message);
        void onMessageSent(ChatMessage message);
        void onChatError(String error);
    }

    public ChatController(ServerConnection connection, UserSession session) {
        this.connection = connection;
        this.session = session;
    }

    public void setListener(ChatListener listener) {
        this.uiListener = listener;
    }

    /**
     * Sends a chat message to the specified receiver.
     *
     * @param receiver the target username
     * @param content  the message text
     */
    public void sendMessage(String receiver, String content) {
        if (content == null || content.isBlank()) return;
        if (receiver == null || receiver.isBlank()) return;

        // Create local chat message
        ChatMessage chatMsg = new ChatMessage(session.getUsername(), receiver, content);

        // Create protocol message for the wire
        ProtocolMessage msg = ProtocolMessage.createChat(
                session.getUsername(), receiver, content);

        try {
            connection.sendMessage(msg);

            // Add to local history
            addToHistory(receiver, chatMsg);

            // Notify UI
            if (uiListener != null) {
                uiListener.onMessageSent(chatMsg);
            }

        } catch (IOException e) {
            if (uiListener != null) {
                uiListener.onChatError("Failed to send message: " + e.getMessage());
            }
        }
    }

    /**
     * Handles an incoming chat message from the server.
     */
    public void handleIncomingMessage(ProtocolMessage msg) {
        String content = msg.getData().has("content")
                ? msg.getData().get("content").getAsString() : "";
        String sender = msg.getSender();

        ChatMessage chatMsg = new ChatMessage(sender, session.getUsername(), content);
        chatMsg.setTimestamp(msg.getTimestamp());

        // Add to history under the sender's name
        addToHistory(sender, chatMsg);

        // Notify UI
        if (uiListener != null) {
            uiListener.onMessageReceived(chatMsg);
        }
    }

    /**
     * Handles a chat error (e.g., receiver offline).
     */
    public void handleChatError(ProtocolMessage msg) {
        String reason = msg.getData().has("reason")
                ? msg.getData().get("reason").getAsString()
                : "Unknown error";
        if (uiListener != null) {
            uiListener.onChatError(reason);
        }
    }

    /**
     * Returns the chat history with a specific user.
     */
    public List<ChatMessage> getHistory(String username) {
        return chatHistory.getOrDefault(username, Collections.emptyList());
    }

    /**
     * Adds a message to the chat history.
     */
    private void addToHistory(String otherUser, ChatMessage message) {
        chatHistory.computeIfAbsent(otherUser, k -> new CopyOnWriteArrayList<>())
                   .add(message);
    }
}
