package com.chatapp.gui;

import com.chatapp.gui.components.ChatBubble;
import com.chatapp.gui.components.ModernScrollPane;
import com.chatapp.gui.components.RoundedButton;
import com.chatapp.gui.components.StatusIndicator;
import com.chatapp.model.ChatMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Chat panel displaying messages and the message input bar for a 1-1 conversation.
 */
public class ChatPanel extends JPanel {

    private static final Color BG_CHAT = new Color(0x31, 0x33, 0x38);
    private static final Color BG_HEADER = new Color(0x2B, 0x2D, 0x31);
    private static final Color BG_INPUT = new Color(0x38, 0x3A, 0x40);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color DIVIDER = new Color(0x3E, 0x40, 0x47);

    private final String chatPartner;
    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;

    private Consumer<String> onSendMessage;
    private Runnable onSendFile;

    public ChatPanel(String chatPartner) {
        this.chatPartner = chatPartner;
        setLayout(new BorderLayout());
        setBackground(BG_CHAT);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftHeader.setOpaque(false);

        StatusIndicator status = new StatusIndicator();
        status.setOnline(true);
        leftHeader.add(status);

        JLabel partnerLabel = new JLabel(chatPartner);
        partnerLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        partnerLabel.setForeground(TEXT_COLOR);
        leftHeader.add(partnerLabel);

        header.add(leftHeader, BorderLayout.WEST);

        // Send File button in header
        RoundedButton fileBtn = new RoundedButton("📎 Send File");
        fileBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        fileBtn.addActionListener(e -> {
            if (onSendFile != null) onSendFile.run();
        });
        header.add(fileBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Message list container
        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(BG_CHAT);
        messagesContainer.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        scrollPane = new ModernScrollPane(messagesContainer);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Input bar
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(BG_HEADER);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        inputField = new JTextField();
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(TEXT_COLOR);
        inputField.setCaretColor(TEXT_COLOR);
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DIVIDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendCurrentText();
                }
            }
        });
        inputBar.add(inputField, BorderLayout.CENTER);

        RoundedButton sendBtn = new RoundedButton("Send");
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendBtn.addActionListener(e -> sendCurrentText());
        inputBar.add(sendBtn, BorderLayout.EAST);

        add(inputBar, BorderLayout.SOUTH);
    }

    public void setOnSendMessage(Consumer<String> listener) {
        this.onSendMessage = listener;
    }

    public void setOnSendFile(Runnable listener) {
        this.onSendFile = listener;
    }

    public void addChatMessage(ChatMessage msg, boolean isOutgoing) {
        ChatBubble bubble = new ChatBubble(msg, isOutgoing);
        messagesContainer.add(bubble);
        messagesContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    public void addSystemNotice(String text) {
        JPanel notice = new JPanel(new FlowLayout(FlowLayout.CENTER));
        notice.setOpaque(false);
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        label.setForeground(new Color(0x94, 0x9B, 0xA4));
        notice.add(label);

        messagesContainer.add(notice);
        messagesContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void sendCurrentText() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            inputField.setText("");
            if (onSendMessage != null) {
                onSendMessage.accept(text);
            }
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    public String getChatPartner() {
        return chatPartner;
    }
}
