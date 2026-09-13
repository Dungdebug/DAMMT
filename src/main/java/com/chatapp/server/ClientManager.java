package com.chatapp.server;

import com.chatapp.model.ProtocolMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages all connected and authenticated clients.
 *
 * <p>Uses a {@link ConcurrentHashMap} for thread-safe access from multiple
 * {@link ClientHandler} threads. Each client is identified by their unique
 * username (enforced during login).</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Register/unregister clients</li>
 *   <li>Lookup clients by username</li>
 *   <li>Broadcast messages to all or specific clients</li>
 *   <li>Provide online user list</li>
 * </ul>
 */
public class ClientManager {

    private static final Logger LOGGER = ServerLogger.getLogger();

    /**
     * Thread-safe map: username → ClientHandler.
     * ConcurrentHashMap allows multiple threads to read/write without
     * external synchronization, preventing ConcurrentModificationException.
     */
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    /**
     * Registers a client after successful login.
     */
    public void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        LOGGER.fine("Client registered: " + username + " (total: " + clients.size() + ")");
    }

    /**
     * Unregisters a client (on disconnect or timeout).
     */
    public void removeClient(String username) {
        clients.remove(username);
        LOGGER.fine("Client unregistered: " + username + " (total: " + clients.size() + ")");
    }

    /**
     * Gets a client handler by username.
     *
     * @return the handler, or null if not found
     */
    public ClientHandler getClient(String username) {
        return clients.get(username);
    }

    /**
     * Checks if a user is currently online.
     */
    public boolean isOnline(String username) {
        return clients.containsKey(username);
    }

    /**
     * Returns an unmodifiable set of all online usernames.
     */
    public Set<String> getOnlineUsernames() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    /**
     * Returns the number of connected clients.
     */
    public int getOnlineCount() {
        return clients.size();
    }

    /**
     * Sends a message to a specific user.
     *
     * @return true if the message was sent successfully
     */
    public boolean sendToUser(String username, ProtocolMessage msg) {
        ClientHandler handler = clients.get(username);
        if (handler != null) {
            try {
                handler.sendMessage(msg);
                return true;
            } catch (IOException e) {
                LOGGER.warning("Failed to send message to " + username + ": " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Broadcasts a message to ALL connected clients.
     */
    public void broadcast(ProtocolMessage msg) {
        for (ClientHandler handler : clients.values()) {
            try {
                handler.sendMessage(msg);
            } catch (IOException e) {
                LOGGER.warning("Broadcast failed for " + handler.getUsername() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Broadcasts a message to all clients EXCEPT the specified user.
     */
    public void broadcastExcept(ProtocolMessage msg, String excludeUsername) {
        for (var entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeUsername)) {
                try {
                    entry.getValue().sendMessage(msg);
                } catch (IOException e) {
                    LOGGER.warning("Broadcast failed for " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Disconnects all clients (used during server shutdown).
     */
    public void disconnectAll() {
        for (ClientHandler handler : clients.values()) {
            handler.shutdown();
        }
        clients.clear();
    }
}
