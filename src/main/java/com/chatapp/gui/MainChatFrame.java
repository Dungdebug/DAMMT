package com.chatapp.gui;

import com.chatapp.client.*;
import com.chatapp.gui.components.*;
import com.chatapp.model.*;
import com.chatapp.protocol.MessageType;
import com.chatapp.utils.FileUtils;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Main chat window — displayed after successful login.
 *
 * <h3>Layout:</h3>
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │  Header (username, status, logout)                   │
 * ├──────────────┬───────────────────────────────────────┤
 * │  Online      │  Chat Area                            │
 * │  Users       │  (message bubbles)                    │
 * │  List        │                                       │
 * │              │                                       │
 * │              │                                       │
 * │              ├───────────────────────────────────────┤
 * │              │  Input Area (text + send + file btn)  │
 * └──────────────┴───────────────────────────────────────┘
 * </pre>
 */
public class MainChatFrame extends JFrame {

    // Color palette
    private static final Color BG_DARK = new Color(0x1E, 0x1F, 0x22);
    private static final Color BG_SIDEBAR = new Color(0x2B, 0x2D, 0x31);
    private static final Color BG_CHAT = new Color(0x31, 0x33, 0x38);
    private static final Color BG_INPUT = new Color(0x38, 0x3A, 0x40);
    private static final Color ACCENT = new Color(0x58, 0x65, 0xF2);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color LABEL_COLOR = new Color(0xA0, 0xA0, 0xA0);
    private static final Color DIVIDER = new Color(0x3E, 0x40, 0x47);
    private static final Color USER_HOVER = new Color(0x3A, 0x3C, 0x43);
    private static final Color USER_SELECTED = new Color(0x40, 0x44, 0x4B);

    // Controllers
    private final ServerConnection connection;
    private final UserSession session;
    private final ChatController chatController;
    private final FileTransferController fileTransferController;

    // GUI components
    private DefaultListModel<String> onlineUsersModel;
    private JList<String> onlineUsersList;
    private JPanel chatMessagesPanel;
    private JTextField messageInput;
    private RoundedButton sendButton;
    private JLabel chatTitleLabel;
    private JLabel onlineCountLabel;
    private JPanel rightPanel;
    private JPanel emptyChatPanel;

    /** Currently selected chat partner */
    private String selectedUser = null;

    /** Chat panels per user (for switching without losing history) */
    private final Map<String, JPanel> chatPanels = new HashMap<>();

    /** Notification dots for unread messages */
    private final Map<String, Boolean> unreadMessages = new HashMap<>();

    /** File transfer progress dialog */
    private FileTransferDialog fileTransferDialog;

    public MainChatFrame(ServerConnection connection, UserSession session) {
        this.connection = connection;
        this.session = session;
        this.chatController = new ChatController(connection, session);
        this.fileTransferController = new FileTransferController(connection, session);

        setTitle("Java TCP Chat — " + session.getUsername());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 680);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        initComponents();
        setupListeners();
        setupMessageHandling();

        // Handle window close → graceful disconnect
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleDisconnect();
            }
        });
    }

    private void initComponents() {
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        // ==================== Header ====================
        JPanel header = createHeader();
        add(header, BorderLayout.NORTH);

        // ==================== Main Split ====================
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerSize(1);
        splitPane.setDividerLocation(240);
        splitPane.setBorder(null);

        // Left: Online users sidebar
        JPanel sidebar = createSidebar();
        splitPane.setLeftComponent(sidebar);

        // Right: Chat area
        rightPanel = createChatArea();
        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    // ==================== Header ====================

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        // Left: App name
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftHeader.setOpaque(false);

        JLabel appIcon = new JLabel("💬");
        appIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        leftHeader.add(appIcon);

        JLabel appName = new JLabel("TCP Chat");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appName.setForeground(TEXT_COLOR);
        leftHeader.add(appName);

        header.add(leftHeader, BorderLayout.WEST);

        // Center: Status
        JPanel centerHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerHeader.setOpaque(false);

        StatusIndicator indicator = new StatusIndicator();
        indicator.setOnline(true);
        centerHeader.add(indicator);

        JLabel userLabel = new JLabel(session.getUsername());
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userLabel.setForeground(TEXT_COLOR);
        centerHeader.add(userLabel);

        header.add(centerHeader, BorderLayout.CENTER);

        // Right: Disconnect button
        RoundedButton logoutBtn = new RoundedButton("Disconnect");
        logoutBtn.setColors(
                new Color(0xED, 0x42, 0x45),
                new Color(0xD6, 0x3B, 0x3E),
                new Color(0xBF, 0x34, 0x37));
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logoutBtn.addActionListener(e -> handleDisconnect());
        header.add(logoutBtn, BorderLayout.EAST);

        return header;
    }

    // ==================== Sidebar ====================

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DIVIDER));

        // Sidebar header
        JPanel sideHeader = new JPanel(new BorderLayout());
        sideHeader.setBackground(BG_SIDEBAR);
        sideHeader.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel onlineLabel = new JLabel("Online Users");
        onlineLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        onlineLabel.setForeground(TEXT_COLOR);
        sideHeader.add(onlineLabel, BorderLayout.WEST);

        onlineCountLabel = new JLabel(String.valueOf(session.getOnlineUsers().size()));
        onlineCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        onlineCountLabel.setForeground(ACCENT);
        sideHeader.add(onlineCountLabel, BorderLayout.EAST);

        sidebar.add(sideHeader, BorderLayout.NORTH);

        // Users list
        onlineUsersModel = new DefaultListModel<>();
        for (String user : session.getOnlineUsers()) {
            onlineUsersModel.addElement(user);
        }

        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.setBackground(BG_SIDEBAR);
        onlineUsersList.setForeground(TEXT_COLOR);
        onlineUsersList.setSelectionBackground(USER_SELECTED);
        onlineUsersList.setSelectionForeground(TEXT_COLOR);
        onlineUsersList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        onlineUsersList.setFixedCellHeight(48);
        onlineUsersList.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        // Custom cell renderer with status indicator
        onlineUsersList.setCellRenderer(new UserListRenderer());

        // Click to select chat partner
        onlineUsersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = onlineUsersList.getSelectedValue();
                if (selected != null) {
                    selectUser(selected);
                }
            }
        });

        sidebar.add(new ModernScrollPane(onlineUsersList), BorderLayout.CENTER);

        return sidebar;
    }

    // ==================== Chat Area ====================

    private JPanel createChatArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_CHAT);

        // Empty state (no user selected)
        emptyChatPanel = createEmptyChatPanel();
        panel.add(emptyChatPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createEmptyChatPanel() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(BG_CHAT);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel("✉");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 64));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(icon);

        content.add(Box.createVerticalStrut(16));

        JLabel msg = new JLabel("Select a user to start chatting");
        msg.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        msg.setForeground(LABEL_COLOR);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(msg);

        empty.add(content);
        return empty;
    }

    private JPanel createActiveChatPanel(String username) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_CHAT);

        // Chat header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_SIDEBAR);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JPanel chatUserInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chatUserInfo.setOpaque(false);

        StatusIndicator si = new StatusIndicator();
        si.setOnline(true);
        chatUserInfo.add(si);

        chatTitleLabel = new JLabel(username);
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatTitleLabel.setForeground(TEXT_COLOR);
        chatUserInfo.add(chatTitleLabel);

        chatHeader.add(chatUserInfo, BorderLayout.WEST);
        panel.add(chatHeader, BorderLayout.NORTH);

        // Messages area
        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(BG_CHAT);
        messagesPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new ModernScrollPane(messagesPanel);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Input area
        JPanel inputArea = createInputArea();
        panel.add(inputArea, BorderLayout.SOUTH);

        // Store the messages panel reference keyed by username
        chatPanels.put(username, messagesPanel);

        // Load existing messages
        for (ChatMessage msg : chatController.getHistory(username)) {
            addBubble(messagesPanel, msg, scrollPane);
        }

        return panel;
    }

    private JPanel createInputArea() {
        JPanel inputArea = new JPanel(new BorderLayout(8, 0));
        inputArea.setBackground(BG_INPUT);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        // File attach button
        JButton fileBtn = new JButton("📎");
        fileBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        fileBtn.setForeground(LABEL_COLOR);
        fileBtn.setBackground(BG_INPUT);
        fileBtn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        fileBtn.setFocusPainted(false);
        fileBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        fileBtn.setToolTipText("Send file");
        fileBtn.addActionListener(e -> handleSendFile());
        inputArea.add(fileBtn, BorderLayout.WEST);

        // Text input
        messageInput = new JTextField();
        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageInput.putClientProperty("JTextField.placeholderText", "Type a message...");
        messageInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleSendMessage();
                }
            }
        });
        inputArea.add(messageInput, BorderLayout.CENTER);

        // Send button
        sendButton = new RoundedButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.addActionListener(e -> handleSendMessage());
        inputArea.add(sendButton, BorderLayout.EAST);

        return inputArea;
    }

    // ==================== User Selection ====================

    private void selectUser(String username) {
        if (username.equals(selectedUser)) return;
        selectedUser = username;

        // Clear unread
        unreadMessages.remove(username);
        onlineUsersList.repaint();

        // Replace right panel content
        rightPanel.removeAll();

        JPanel chatPanel = createActiveChatPanel(username);
        rightPanel.add(chatPanel, BorderLayout.CENTER);

        rightPanel.revalidate();
        rightPanel.repaint();

        // Focus on input
        messageInput.requestFocusInWindow();
    }

    // ==================== Message Handling ====================

    private void handleSendMessage() {
        if (selectedUser == null || messageInput.getText().trim().isEmpty()) return;

        String content = messageInput.getText().trim();
        messageInput.setText("");

        chatController.sendMessage(selectedUser, content);
    }

    private void handleSendFile() {
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a user first", "No user selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser(
                FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setDialogTitle("Select file to send");
        chooser.setMultiSelectionEnabled(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Send \"" + file.getName() + "\" (" +
                            FileUtils.formatSize(file.length()) +
                            ") to " + selectedUser + "?",
                    "Confirm File Transfer",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    fileTransferController.sendFileRequest(selectedUser, file);
                    addSystemMessage(selectedUser,
                            "📤 File transfer request sent: " + file.getName() +
                                    " (" + FileUtils.formatSize(file.length()) + ")");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to send file: " + e.getMessage(),
                            "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void addSystemMessage(String user, String text) {
        JPanel panel = chatPanels.get(user);
        if (panel != null) {
            JLabel label = new JLabel(text);
            label.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            label.setForeground(LABEL_COLOR);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            panel.add(label);
            panel.add(Box.createVerticalStrut(4));
            panel.revalidate();
            scrollToBottom(panel);
        }
    }

    private void addBubble(JPanel panel, ChatMessage msg, JScrollPane scrollPane) {
        boolean isSent = msg.isSentBy(session.getUsername());
        ChatBubble bubble = new ChatBubble(
                msg.getSender(), msg.getContent(), msg.getTimestamp(), isSent);
        panel.add(bubble);
        panel.add(Box.createVerticalStrut(4));
        panel.revalidate();
        scrollToBottom(panel);
    }

    private void scrollToBottom(JPanel panel) {
        SwingUtilities.invokeLater(() -> {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, panel);
            if (sp != null) {
                JScrollBar sb = sp.getVerticalScrollBar();
                sb.setValue(sb.getMaximum());
            }
        });
    }

    // ==================== Controller Listeners ====================

    private void setupListeners() {
        chatController.setListener(new ChatController.ChatListener() {
            @Override
            public void onMessageReceived(ChatMessage message) {
                SwingUtilities.invokeLater(() -> {
                    String sender = message.getSender();

                    // If this sender's chat panel exists, add the bubble
                    JPanel panel = chatPanels.get(sender);
                    if (panel != null) {
                        ChatBubble bubble = new ChatBubble(
                                sender, message.getContent(),
                                message.getTimestamp(), false);
                        panel.add(bubble);
                        panel.add(Box.createVerticalStrut(4));
                        panel.revalidate();
                        scrollToBottom(panel);
                    }

                    // If not the currently selected user, mark unread
                    if (!sender.equals(selectedUser)) {
                        unreadMessages.put(sender, true);
                        onlineUsersList.repaint();

                        // Flash title bar
                        setTitle("💬 New message from " + sender);
                    }
                });
            }

            @Override
            public void onMessageSent(ChatMessage message) {
                SwingUtilities.invokeLater(() -> {
                    String receiver = message.getReceiver();
                    JPanel panel = chatPanels.get(receiver);
                    if (panel != null) {
                        ChatBubble bubble = new ChatBubble(
                                session.getUsername(), message.getContent(),
                                message.getTimestamp(), true);
                        panel.add(bubble);
                        panel.add(Box.createVerticalStrut(4));
                        panel.revalidate();
                        scrollToBottom(panel);
                    }
                });
            }

            @Override
            public void onChatError(String error) {
                SwingUtilities.invokeLater(() -> {
                    if (selectedUser != null) {
                        addSystemMessage(selectedUser, "⚠ " + error);
                    }
                });
            }
        });

        fileTransferController.setListener(new FileTransferController.FileTransferListener() {
            @Override
            public void onFileRequestReceived(FileMetadata metadata) {
                SwingUtilities.invokeLater(() -> {
                    int choice = JOptionPane.showConfirmDialog(MainChatFrame.this,
                            metadata.getSender() + " wants to send you:\n\n" +
                                    "📄 " + metadata.getFileName() + "\n" +
                                    "📦 " + FileUtils.formatSize(metadata.getFileSize()) + "\n\n" +
                                    "Accept this file?",
                            "Incoming File Transfer",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);

                    if (choice == JOptionPane.YES_OPTION) {
                        // Choose save directory
                        JFileChooser chooser = new JFileChooser(
                                FileSystemView.getFileSystemView().getHomeDirectory());
                        chooser.setDialogTitle("Save file to...");
                        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

                        int result = chooser.showSaveDialog(MainChatFrame.this);
                        File saveDir = (result == JFileChooser.APPROVE_OPTION)
                                ? chooser.getSelectedFile()
                                : new File(System.getProperty("user.home"), "Downloads");

                        fileTransferController.acceptTransfer(
                                metadata.getTransferId(), saveDir);

                        addSystemMessage(metadata.getSender(),
                                "📥 Accepted file: " + metadata.getFileName());
                    } else {
                        fileTransferController.rejectTransfer(metadata.getTransferId());
                        addSystemMessage(metadata.getSender(),
                                "❌ Rejected file: " + metadata.getFileName());
                    }
                });
            }

            @Override
            public void onTransferStarted(String transferId, boolean isSender) {
                SwingUtilities.invokeLater(() -> showTransferDialog(transferId, isSender));
            }

            @Override
            public void onTransferProgress(String transferId, int percent,
                                           long bytesTransferred, long totalBytes, String fileName) {
                SwingUtilities.invokeLater(() -> {
                    if (fileTransferDialog != null) {
                        fileTransferDialog.updateProgress(percent, bytesTransferred, totalBytes, fileName);
                    }
                });
            }

            @Override
            public void onTransferComplete(String transferId, boolean verified) {
                SwingUtilities.invokeLater(() -> {
                    closeTransferDialog();
                    String verifyText = verified ? " ✅ SHA-256 verified" : "";
                    JOptionPane.showMessageDialog(MainChatFrame.this,
                            "File transfer completed!" + verifyText,
                            "Transfer Complete", JOptionPane.INFORMATION_MESSAGE);

                    if (selectedUser != null) {
                        addSystemMessage(selectedUser,
                                "✅ File transfer completed" + verifyText);
                    }
                });
            }

            @Override
            public void onTransferCancelled(String transferId, String reason) {
                SwingUtilities.invokeLater(() -> {
                    closeTransferDialog();
                    JOptionPane.showMessageDialog(MainChatFrame.this,
                            "File transfer cancelled: " + reason,
                            "Transfer Cancelled", JOptionPane.WARNING_MESSAGE);
                });
            }

            @Override
            public void onTransferError(String transferId, String error) {
                SwingUtilities.invokeLater(() -> {
                    closeTransferDialog();
                    JOptionPane.showMessageDialog(MainChatFrame.this,
                            "File transfer error: " + error,
                            "Transfer Error", JOptionPane.ERROR_MESSAGE);
                });
            }

            @Override
            public void onTransferRejected(String transferId) {
                SwingUtilities.invokeLater(() -> {
                    if (selectedUser != null) {
                        addSystemMessage(selectedUser,
                                "❌ File transfer was rejected");
                    }
                });
            }
        });
    }

    private void showTransferDialog(String transferId, boolean isSender) {
        if (fileTransferDialog != null) {
            fileTransferDialog.dispose();
        }

        fileTransferDialog = new FileTransferDialog(this, "File Transfer", isSender);
        fileTransferDialog.setOnCancel(() -> {
            fileTransferController.cancelTransfer(transferId);
            closeTransferDialog();
        });
        fileTransferDialog.setVisible(true);
    }

    private void closeTransferDialog() {
        if (fileTransferDialog != null) {
            fileTransferDialog.dispose();
            fileTransferDialog = null;
        }
    }

    // ==================== Protocol Message Handling ====================

    private void setupMessageHandling() {
        // Re-wire the connection listener to dispatch to controllers
        connection.setListener(new ServerConnection.MessageListener() {
            @Override
            public void onMessageReceived(ProtocolMessage msg) {
                switch (msg.getType()) {
                    case CHAT_MESSAGE -> chatController.handleIncomingMessage(msg);
                    case CHAT_DELIVERED -> {} // Could update UI with ✓✓
                    case CHAT_ERROR -> chatController.handleChatError(msg);
                    case USER_ONLINE -> handleUserOnline(msg);
                    case USER_OFFLINE -> handleUserOffline(msg);
                    case FILE_REQUEST -> fileTransferController.handleIncomingFileRequest(msg);
                    case FILE_ACCEPT -> fileTransferController.handleFileAccepted(msg);
                    case FILE_REJECT -> fileTransferController.handleFileRejected(msg);
                    case FILE_PROGRESS -> fileTransferController.handleProgress(msg);
                    case FILE_COMPLETE -> fileTransferController.handleTransferComplete(msg);
                    case FILE_CANCEL -> fileTransferController.handleTransferCancelled(msg);
                    case FILE_ERROR -> fileTransferController.handleTransferError(msg);
                    case ERROR -> handleServerError(msg);
                    default -> {}
                }
            }

            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainChatFrame.this,
                            "Disconnected from server: " + reason,
                            "Connection Lost", JOptionPane.ERROR_MESSAGE);
                    goToLogin();
                });
            }
        });
    }

    private void handleUserOnline(ProtocolMessage msg) {
        String username = msg.getData().get("username").getAsString();
        session.addOnlineUser(username);
        SwingUtilities.invokeLater(() -> {
            if (!onlineUsersModel.contains(username)) {
                onlineUsersModel.addElement(username);
            }
            onlineCountLabel.setText(String.valueOf(onlineUsersModel.size()));
        });
    }

    private void handleUserOffline(ProtocolMessage msg) {
        String username = msg.getData().get("username").getAsString();
        session.removeOnlineUser(username);
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.removeElement(username);
            onlineCountLabel.setText(String.valueOf(onlineUsersModel.size()));

            if (username.equals(selectedUser)) {
                addSystemMessage(selectedUser, "⚠ " + username + " went offline");
            }
        });
    }

    private void handleServerError(ProtocolMessage msg) {
        String reason = msg.getData().has("reason")
                ? msg.getData().get("reason").getAsString() : "Server error";
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, reason,
                        "Server Error", JOptionPane.ERROR_MESSAGE));
    }

    private void handleDisconnect() {
        connection.disconnect();
        goToLogin();
    }

    private void goToLogin() {
        dispose();
        SwingUtilities.invokeLater(() -> {
            LoginFrame login = new LoginFrame();
            login.setVisible(true);
        });
    }

    // ==================== Custom Cell Renderer ====================

    /**
     * Custom renderer for the online users list with status dot and unread indicator.
     */
    private class UserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            JPanel cell = new JPanel(new BorderLayout(10, 0));
            cell.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            if (isSelected) {
                cell.setBackground(USER_SELECTED);
            } else {
                cell.setBackground(BG_SIDEBAR);
            }

            String username = (String) value;

            // Status indicator
            StatusIndicator si = new StatusIndicator();
            si.setOnline(true);
            cell.add(si, BorderLayout.WEST);

            // Username
            JLabel nameLabel = new JLabel(username);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            nameLabel.setForeground(TEXT_COLOR);
            cell.add(nameLabel, BorderLayout.CENTER);

            // Unread dot
            if (unreadMessages.containsKey(username)) {
                JPanel dot = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(ACCENT);
                        g2.fillOval(0, 0, 10, 10);
                        g2.dispose();
                    }
                };
                dot.setOpaque(false);
                dot.setPreferredSize(new Dimension(10, 10));
                cell.add(dot, BorderLayout.EAST);
            }

            return cell;
        }
    }
}
