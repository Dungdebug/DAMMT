package com.chatapp.model;

/**
 * Represents a connected user in the chat system.
 *
 * <p>Used by both Server (to track connected clients) and Client (to display
 * online users list). The username is the primary identifier — no persistent
 * database is needed for this in-memory session-based system.</p>
 */
public class User {

    private String username;
    private String status;
    private long loginTime;

    public User() {
        // Default constructor for Gson deserialization
    }

    public User(String username) {
        this.username = username;
        this.status = "online";
        this.loginTime = System.currentTimeMillis();
    }

    // ==================== Getters & Setters ====================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', status='" + status + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return username != null && username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}
