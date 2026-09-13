package com.chatapp.utils;

import com.chatapp.protocol.ProtocolConstants;

import java.util.regex.Pattern;

/**
 * Input validation utilities for usernames, ports, IP addresses, etc.
 *
 * <p>All data received from the network must be validated before processing.
 * Never trust input from a client — it could be malformed, malicious, or
 * corrupted during transmission.</p>
 */
public final class ValidationUtils {

    private static final Pattern USERNAME_REGEX =
            Pattern.compile(ProtocolConstants.USERNAME_PATTERN);

    private static final Pattern IP_REGEX =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private ValidationUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Validates a username against the allowed pattern.
     *
     * <p>Rules: 2-20 characters, alphanumeric + underscore only.</p>
     *
     * @param username the username to validate
     * @return true if valid
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return USERNAME_REGEX.matcher(username).matches();
    }

    /**
     * Validates that a port number is in the valid TCP range (1-65535).
     *
     * @param port the port number
     * @return true if valid
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * Validates a string as a port number.
     *
     * @param portStr the port string
     * @return true if it's a valid numeric port
     */
    public static boolean isValidPort(String portStr) {
        if (portStr == null || portStr.isBlank()) return false;
        try {
            return isValidPort(Integer.parseInt(portStr.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates an IPv4 address or "localhost".
     *
     * @param ip the IP address string
     * @return true if valid
     */
    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String trimmed = ip.trim();
        if ("localhost".equalsIgnoreCase(trimmed)) return true;
        return IP_REGEX.matcher(trimmed).matches();
    }

    /**
     * Returns a human-readable username validation error message, or null if valid.
     */
    public static String getUsernameError(String username) {
        if (username == null || username.isBlank()) {
            return "Username cannot be empty";
        }
        if (username.length() < ProtocolConstants.MIN_USERNAME_LENGTH) {
            return "Username must be at least " + ProtocolConstants.MIN_USERNAME_LENGTH + " characters";
        }
        if (username.length() > ProtocolConstants.MAX_USERNAME_LENGTH) {
            return "Username must be at most " + ProtocolConstants.MAX_USERNAME_LENGTH + " characters";
        }
        if (!USERNAME_REGEX.matcher(username).matches()) {
            return "Username can only contain letters, numbers, and underscores";
        }
        return null; // Valid
    }
}
