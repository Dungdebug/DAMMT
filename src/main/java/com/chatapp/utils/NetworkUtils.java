package com.chatapp.utils;

import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * Network-related utility methods.
 */
public final class NetworkUtils {

    private NetworkUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Checks if a TCP port is available (not in use) on the local machine.
     *
     * @param port the port number to check
     * @return true if the port is available
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the local machine's IP address.
     *
     * @return the local IP address string, or "127.0.0.1" if detection fails
     */
    public static String getLocalIPAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
