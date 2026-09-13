package com.chatapp.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores the current client's session state.
 *
 * <p>Holds the logged-in username and the list of online users. The online
 * users list is updated in real-time as USER_ONLINE/USER_OFFLINE messages
 * arrive from the server.</p>
 *
 * <p>Uses {@link CopyOnWriteArrayList} for thread-safe read/write access
 * from both the network receive thread and the Swing EDT.</p>
 */
public class UserSession {

    private final String username;
    private final CopyOnWriteArrayList<String> onlineUsers = new CopyOnWriteArrayList<>();
    private volatile boolean connected = true;

    public UserSession(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getOnlineUsers() {
        return onlineUsers;
    }

    public void setOnlineUsers(List<String> users) {
        onlineUsers.clear();
        onlineUsers.addAll(users);
    }

    public void addOnlineUser(String user) {
        if (!onlineUsers.contains(user) && !user.equals(username)) {
            onlineUsers.add(user);
        }
    }

    public void removeOnlineUser(String user) {
        onlineUsers.remove(user);
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
