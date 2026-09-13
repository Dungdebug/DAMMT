package com.chatapp.server;

import com.chatapp.utils.ValidationUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages user login sessions on the server.
 *
 * <p>Ensures username uniqueness (no two clients can be logged in with the
 * same username simultaneously) and validates usernames against the allowed
 * pattern.</p>
 *
 * <p>This is an in-memory session manager — no persistent database is needed.
 * Sessions are stored in a {@link ConcurrentHashMap} for thread-safe access.</p>
 */
public class SessionManager {

    private static final Logger LOGGER = ServerLogger.getLogger();

    /** Set of currently active usernames (thread-safe) */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    /**
     * Attempts to create a new session for the given username.
     *
     * @param username the requested username
     * @return null if successful, or an error message string if login is rejected
     */
    public String createSession(String username) {
        // Validate username format
        String validationError = ValidationUtils.getUsernameError(username);
        if (validationError != null) {
            return validationError;
        }

        // Check uniqueness — putIfAbsent is atomic in ConcurrentHashMap
        if (!activeSessions.add(username)) {
            return "Username '" + username + "' is already online";
        }

        LOGGER.info("Session created: " + username);
        return null; // Success
    }

    /**
     * Removes a session when a user disconnects.
     */
    public void removeSession(String username) {
        if (username != null && activeSessions.remove(username)) {
            LOGGER.info("Session removed: " + username);
        }
    }

    /**
     * Checks if a username is currently in an active session.
     */
    public boolean isOnline(String username) {
        return activeSessions.contains(username);
    }

    /**
     * Returns the number of active sessions.
     */
    public int getActiveCount() {
        return activeSessions.size();
    }
}
