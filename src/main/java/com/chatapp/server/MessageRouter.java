package com.chatapp.server;

import com.chatapp.model.ProtocolMessage;

import java.util.logging.Logger;

/**
 * Routes protocol messages from one client to another via the {@link ClientManager}.
 *
 * <p>The Server acts as a central message relay in the Client-Server architecture.
 * When Client A sends a message to Client B, it goes:</p>
 * <pre>
 *   Client A → Server (MessageRouter) → Client B
 * </pre>
 *
 * <p>The router does NOT store messages — if the target is offline, the message
 * is dropped and an error is returned to the sender.</p>
 */
public class MessageRouter {

    private static final Logger LOGGER = ServerLogger.getLogger();

    private final ClientManager clientManager;

    public MessageRouter(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Routes a message to the specified receiver.
     *
     * @param receiver the target username
     * @param message  the message to deliver
     * @return true if the message was delivered successfully
     */
    public boolean routeToUser(String receiver, ProtocolMessage message) {
        if (receiver == null || receiver.isBlank()) {
            LOGGER.warning("Cannot route message: receiver is null/empty");
            return false;
        }

        boolean sent = clientManager.sendToUser(receiver, message);
        if (!sent) {
            LOGGER.fine("Message not delivered to " + receiver + " (offline or error)");
        }
        return sent;
    }

    /**
     * Routes a message based on its receiver field.
     *
     * @param message the message (must have receiver set)
     * @return true if delivered
     */
    public boolean routeMessage(ProtocolMessage message) {
        return routeToUser(message.getReceiver(), message);
    }
}
