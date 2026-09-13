package com.chatapp.gui;

import com.chatapp.client.ServerConnection;
import com.chatapp.client.UserSession;
import com.chatapp.gui.components.RoundedButton;
import com.chatapp.model.ProtocolMessage;
import com.chatapp.protocol.MessageType;
import com.chatapp.utils.ValidationUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Login screen — the first window shown to the user.
 *
 * <p>Handles server connection and user authentication:</p>
 * <ol>
 *   <li>User enters server IP, port, and username</li>
 *   <li>Client connects to server via TCP</li>
 *   <li>Sends LOGIN message</li>
 *   <li>On LOGIN_SUCCESS → open MainChatFrame</li>
 *   <li>On LOGIN_FAILED → show error, stay on login screen</li>
 * </ol>
 */
public class LoginFrame extends JFrame {

    private static final Color BG_COLOR = new Color(0x1E, 0x1F, 0x22);
    private static final Color CARD_COLOR = new Color(0x2B, 0x2D, 0x31);
    private static final Color ACCENT = new Color(0x58, 0x65, 0xF2);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color LABEL_COLOR = new Color(0xA0, 0xA0, 0xA0);

    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private RoundedButton connectButton;
    private JLabel statusLabel;
    private ServerConnection connection;

    public LoginFrame() {
        setTitle("Java TCP Chat — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 520);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(BG_COLOR);

        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Card panel (centered form)
        JPanel card = new JPanel();
        card.setBackground(CARD_COLOR);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3E, 0x40, 0x47), 1),
                BorderFactory.createEmptyBorder(32, 36, 32, 36)));

        // App title with icon
        JLabel titleIcon = new JLabel("💬");
        titleIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        titleIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(titleIcon);
        card.add(Box.createVerticalStrut(8));

        JLabel title = new JLabel("Java TCP Chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT_COLOR);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(title);

        JLabel subtitle = new JLabel("Connect to start chatting");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(LABEL_COLOR);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(subtitle);

        card.add(Box.createVerticalStrut(24));

        // Server address fields (host and port side by side)
        card.add(createLabel("Server Address"));
        card.add(Box.createVerticalStrut(6));

        JPanel serverRow = new JPanel(new GridLayout(1, 2, 8, 0));
        serverRow.setOpaque(false);
        serverRow.setMaximumSize(new Dimension(380, 38));

        hostField = createTextField("127.0.0.1");
        portField = createTextField("5000");
        serverRow.add(hostField);
        serverRow.add(portField);
        card.add(serverRow);

        card.add(Box.createVerticalStrut(16));

        // Username field
        card.add(createLabel("Username"));
        card.add(Box.createVerticalStrut(6));
        usernameField = createTextField("Enter username...");
        usernameField.setMaximumSize(new Dimension(380, 38));
        card.add(usernameField);

        card.add(Box.createVerticalStrut(20));

        // Connect button
        connectButton = new RoundedButton("Connect");
        connectButton.setMaximumSize(new Dimension(380, 42));
        connectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        connectButton.addActionListener(e -> handleConnect());
        card.add(connectButton);

        card.add(Box.createVerticalStrut(12));

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(LABEL_COLOR);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(statusLabel);

        // Enter key to connect
        usernameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleConnect();
                }
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        add(card, gbc);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(LABEL_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextField createTextField(String placeholder) {
        JTextField field = new JTextField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.setPreferredSize(new Dimension(0, 38));
        return field;
    }

    /**
     * Handles the Connect button click.
     * Connects to server in a background thread to avoid blocking EDT.
     */
    private void handleConnect() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();

        // Validate inputs
        if (!ValidationUtils.isValidIP(host)) {
            showError("Invalid server address");
            return;
        }
        if (!ValidationUtils.isValidPort(portStr)) {
            showError("Invalid port (1-65535)");
            return;
        }
        String usernameError = ValidationUtils.getUsernameError(username);
        if (usernameError != null) {
            showError(usernameError);
            return;
        }

        int port = Integer.parseInt(portStr);

        // Disable UI during connection
        setInputEnabled(false);
        statusLabel.setForeground(LABEL_COLOR);
        statusLabel.setText("Connecting to " + host + ":" + port + "...");

        // Connect in background thread (NOT on EDT)
        new Thread(() -> {
            try {
                // Step 1: TCP connect
                connection = new ServerConnection();
                connection.setListener(new LoginMessageListener(username));
                connection.connect(host, port);

                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Connected! Logging in..."));

                // Step 2: Send LOGIN
                ProtocolMessage loginMsg = ProtocolMessage.createLogin(username);
                connection.sendMessage(loginMsg);

                // Response handled by LoginMessageListener

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    showError("Connection failed: " + e.getMessage());
                    setInputEnabled(true);
                });
            }
        }, "LoginThread").start();
    }

    /**
     * Listener for login response from server.
     */
    private class LoginMessageListener implements ServerConnection.MessageListener {

        private final String username;

        LoginMessageListener(String username) {
            this.username = username;
        }

        @Override
        public void onMessageReceived(ProtocolMessage message) {
            SwingUtilities.invokeLater(() -> {
                if (message.getType() == MessageType.LOGIN_SUCCESS) {
                    // Parse online users list
                    List<String> onlineUsers = new ArrayList<>();
                    if (message.getData().has("onlineUsers")) {
                        JsonArray arr = message.getData().getAsJsonArray("onlineUsers");
                        for (JsonElement el : arr) {
                            onlineUsers.add(el.getAsString());
                        }
                    }

                    // Create session
                    UserSession session = new UserSession(username);
                    session.setOnlineUsers(onlineUsers);

                    // Open main chat window
                    MainChatFrame chatFrame = new MainChatFrame(connection, session);
                    chatFrame.setVisible(true);

                    // Close login window
                    LoginFrame.this.dispose();

                } else if (message.getType() == MessageType.LOGIN_FAILED) {
                    String reason = message.getData().has("reason")
                            ? message.getData().get("reason").getAsString()
                            : "Login failed";
                    showError(reason);
                    setInputEnabled(true);
                    connection.disconnect();
                }
            });
        }

        @Override
        public void onDisconnected(String reason) {
            SwingUtilities.invokeLater(() -> {
                showError("Disconnected: " + reason);
                setInputEnabled(true);
            });
        }
    }

    private void setInputEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        connectButton.setEnabled(enabled);
    }

    private void showError(String message) {
        statusLabel.setForeground(new Color(0xED, 0x42, 0x45));
        statusLabel.setText("⚠ " + message);
    }
}
