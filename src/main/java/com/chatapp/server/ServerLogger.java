package com.chatapp.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Server-side logging utility with formatted, timestamped output.
 *
 * <p>Provides clear, readable log messages for debugging and demo purposes.
 * All server events (connections, logins, messages, file transfers, errors)
 * are logged with timestamps to help trace the flow during presentations.</p>
 *
 * <p>Log format example:</p>
 * <pre>
 * 2026-09-12 14:02:15 [INFO]  Client connected: 192.168.1.10
 * 2026-09-12 14:02:17 [INFO]  User logged in: Duong
 * 2026-09-12 14:03:08 [INFO]  Duong -> An: Hello
 * </pre>
 */
public final class ServerLogger {

    private static final Logger LOGGER = Logger.getLogger("ChatServer");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        // Remove default handlers
        LOGGER.setUseParentHandlers(false);

        // Custom console handler with formatted output
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                StringBuilder sb = new StringBuilder();
                sb.append(TIME_FMT.format(LocalDateTime.now()));
                sb.append(" [").append(record.getLevel().getName()).append("]");

                // Pad level name for alignment
                int padding = 7 - record.getLevel().getName().length();
                sb.append(" ".repeat(Math.max(0, padding)));

                sb.append(record.getMessage());

                // Include exception stack trace if present
                if (record.getThrown() != null) {
                    sb.append("\n");
                    StringWriter sw = new StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(sw));
                    sb.append(sw);
                }

                sb.append("\n");
                return sb.toString();
            }
        });

        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.ALL);
    }

    private ServerLogger() {
        // Utility class — prevent instantiation
    }

    /**
     * Returns the configured server logger.
     */
    public static Logger getLogger() {
        return LOGGER;
    }
}
