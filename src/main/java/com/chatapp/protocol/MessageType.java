package com.chatapp.protocol;

/**
 * Defines all message types in the application-level protocol.
 *
 * <p>Each message exchanged between Client and Server over the TCP connection
 * has a {@code type} field that determines how the message payload should be
 * interpreted. This enum is serialized as a string in JSON by Gson.</p>
 *
 * <p>Categories:</p>
 * <ul>
 *   <li>Authentication: LOGIN, LOGIN_SUCCESS, LOGIN_FAILED</li>
 *   <li>User Management: ONLINE_USERS, USER_ONLINE, USER_OFFLINE</li>
 *   <li>Chat: CHAT_MESSAGE, CHAT_DELIVERED, CHAT_ERROR</li>
 *   <li>File Transfer: FILE_REQUEST through FILE_ERROR</li>
 *   <li>Connection: PING, PONG, DISCONNECT, ERROR</li>
 * </ul>
 */
public enum MessageType {

    // ==================== Authentication ====================
    /** Client → Server: request to login with username */
    LOGIN,
    /** Server → Client: login successful, includes online users list */
    LOGIN_SUCCESS,
    /** Server → Client: login failed with reason */
    LOGIN_FAILED,

    // ==================== User Management ====================
    /** Server → Client: complete list of online users (sent after login) */
    ONLINE_USERS,
    /** Server → Clients: broadcast that a user came online */
    USER_ONLINE,
    /** Server → Clients: broadcast that a user went offline */
    USER_OFFLINE,

    // ==================== Chat Messages ====================
    /** Client → Server → Client: a 1-1 chat message */
    CHAT_MESSAGE,
    /** Server → Client: confirmation that message was delivered to receiver */
    CHAT_DELIVERED,
    /** Server → Client: message could not be delivered (receiver offline, etc.) */
    CHAT_ERROR,

    // ==================== File Transfer ====================
    /** Client → Server → Client: request to send a file to another user */
    FILE_REQUEST,
    /** Client → Server → Client: receiver accepts the file transfer */
    FILE_ACCEPT,
    /** Client → Server → Client: receiver rejects the file transfer */
    FILE_REJECT,
    /** Server → Clients: file transfer is starting (connect file socket) */
    FILE_START,
    /** Server → Clients: transfer progress update (percentage, bytes) */
    FILE_PROGRESS,
    /** Client → Server → Client: file transfer completed successfully */
    FILE_COMPLETE,
    /** Client → Server → Client: cancel an ongoing file transfer */
    FILE_CANCEL,
    /** Server → Client: file transfer error */
    FILE_ERROR,

    // ==================== Connection Management ====================
    /** Server → Client: heartbeat ping (expects PONG response) */
    PING,
    /** Client → Server: heartbeat pong (response to PING) */
    PONG,
    /** Client → Server: graceful disconnect notification */
    DISCONNECT,
    /** Server → Client: general error message */
    ERROR
}
