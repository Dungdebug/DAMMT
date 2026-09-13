package com.chatapp.protocol;

import com.chatapp.model.ProtocolMessage;
import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles serialization and deserialization of {@link ProtocolMessage} objects
 * over TCP using <b>length-prefix framing</b>.
 *
 * <h3>Why length-prefix framing?</h3>
 * <p>TCP is a <b>byte stream</b> protocol — it does NOT preserve message boundaries.
 * Data sent in one {@code write()} call may arrive split across multiple
 * {@code read()} calls, or multiple writes may be coalesced into one read.
 * Length-prefix framing solves this by prepending each message with its exact
 * byte length, allowing the receiver to read the correct number of bytes.</p>
 *
 * <h3>Wire format:</h3>
 * <pre>
 * ┌──────────────────────┬────────────────────────────────┐
 * │ 4 bytes (big-endian) │ N bytes (UTF-8 encoded JSON)   │
 * │ payload length = N   │ ProtocolMessage as JSON         │
 * └──────────────────────┴────────────────────────────────┘
 * </pre>
 *
 * <h3>Thread safety:</h3>
 * <p>Write operations are synchronized on the output stream to prevent
 * interleaved writes when multiple threads send messages concurrently.</p>
 */
public final class MessageSerializer {

    /** Gson instance for JSON conversion (thread-safe, reusable) */
    private static final Gson GSON = new Gson();

    private MessageSerializer() {
        // Utility class — prevent instantiation
    }

    /**
     * Writes a ProtocolMessage to the output stream using length-prefix framing.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Serialize message to JSON string</li>
     *   <li>Convert to UTF-8 bytes</li>
     *   <li>Write 4-byte integer length prefix (big-endian)</li>
     *   <li>Write JSON bytes</li>
     *   <li>Flush the stream</li>
     * </ol>
     *
     * @param out     the DataOutputStream to write to
     * @param message the protocol message to send
     * @throws IOException if writing fails or message exceeds size limit
     */
    public static void writeMessage(DataOutputStream out, ProtocolMessage message)
            throws IOException {

        String json = GSON.toJson(message);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // Guard against oversized messages
        if (jsonBytes.length > ProtocolConstants.MAX_MESSAGE_SIZE) {
            throw new IOException("Message too large: " + jsonBytes.length +
                    " bytes (max: " + ProtocolConstants.MAX_MESSAGE_SIZE + ")");
        }

        // Synchronized to prevent interleaved writes from multiple threads
        // sharing the same output stream (e.g., heartbeat thread + message thread)
        synchronized (out) {
            out.writeInt(jsonBytes.length);   // 4-byte length prefix
            out.write(jsonBytes);              // JSON payload
            out.flush();                       // Ensure bytes are sent immediately
        }
    }

    /**
     * Reads a ProtocolMessage from the input stream using length-prefix framing.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Read 4-byte integer = payload length</li>
     *   <li>Validate length is within bounds</li>
     *   <li>Read exactly {@code length} bytes using {@code readFully()}
     *       (blocks until all bytes arrive — handles TCP fragmentation)</li>
     *   <li>Decode UTF-8 bytes to JSON string</li>
     *   <li>Parse JSON into ProtocolMessage</li>
     * </ol>
     *
     * @param in the DataInputStream to read from
     * @return the parsed ProtocolMessage
     * @throws IOException if reading fails, stream ends, or message is malformed
     */
    public static ProtocolMessage readMessage(DataInputStream in) throws IOException {

        // Step 1: Read length prefix
        int length = in.readInt();

        // Step 2: Validate length to prevent memory exhaustion attacks
        if (length <= 0 || length > ProtocolConstants.MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message length: " + length +
                    " (valid range: 1-" + ProtocolConstants.MAX_MESSAGE_SIZE + ")");
        }

        // Step 3: Read exactly 'length' bytes
        // readFully() blocks until ALL bytes are read — this correctly handles
        // TCP fragmentation where data arrives in partial chunks
        byte[] jsonBytes = new byte[length];
        in.readFully(jsonBytes);

        // Step 4: Parse JSON
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        try {
            ProtocolMessage msg = GSON.fromJson(json, ProtocolMessage.class);
            if (msg == null || msg.getType() == null) {
                throw new IOException("Invalid message: missing type field");
            }
            return msg;
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new IOException("Malformed JSON message: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a ProtocolMessage to its JSON string representation.
     *
     * @param message the message to serialize
     * @return JSON string
     */
    public static String toJson(ProtocolMessage message) {
        return GSON.toJson(message);
    }

    /**
     * Parses a JSON string into a ProtocolMessage.
     *
     * @param json the JSON string
     * @return parsed ProtocolMessage
     */
    public static ProtocolMessage fromJson(String json) {
        return GSON.fromJson(json, ProtocolMessage.class);
    }
}
