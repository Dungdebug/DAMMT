package com.chatapp.gui.components;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A chat message bubble component with styled appearance.
 *
 * <p>Sent messages appear right-aligned with blue background.
 * Received messages appear left-aligned with gray background.</p>
 */
public class ChatBubble extends JPanel {

    private static final Color SENT_COLOR = new Color(0x58, 0x65, 0xF2);
    private static final Color RECEIVED_COLOR = new Color(0x38, 0x3A, 0x40);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TIME_COLOR = new Color(0xA0, 0xA0, 0xA0);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final boolean isSent;

    /**
     * Creates a chat bubble.
     *
     * @param sender    sender name
     * @param content   message text
     * @param timestamp Unix timestamp in ms
     * @param isSent    true if this message was sent by the current user
     */
    /**
     * Creates a chat bubble from a ChatMessage.
     *
     * @param message the chat message
     * @param isSent  true if sent by the current user
     */
    public ChatBubble(com.chatapp.model.ChatMessage message, boolean isSent) {
        this(message.getSender(), message.getContent(), message.getTimestamp(), isSent);
    }

    public ChatBubble(String sender, String content, long timestamp, boolean isSent) {
        this.isSent = isSent;
        setOpaque(false);
        setLayout(new BorderLayout());

        // Main bubble panel
        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSent ? SENT_COLOR : RECEIVED_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        bubble.setOpaque(false);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // Sender label (only for received messages)
        if (!isSent) {
            JLabel senderLabel = new JLabel(sender);
            senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            senderLabel.setForeground(new Color(0x7C, 0x8A, 0xF2));
            senderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(senderLabel);
            bubble.add(Box.createVerticalStrut(2));
        }

        // Message content
        JTextArea contentArea = new JTextArea(content);
        contentArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        contentArea.setForeground(TEXT_COLOR);
        contentArea.setOpaque(false);
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentArea.setBorder(null);
        // Limit width
        contentArea.setMaximumSize(new Dimension(350, Integer.MAX_VALUE));
        bubble.add(contentArea);

        // Time label
        String time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(TIME_FMT);
        JLabel timeLabel = new JLabel(time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(TIME_COLOR);
        timeLabel.setAlignmentX(isSent ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(timeLabel);

        // Limit bubble maximum width
        bubble.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));

        // Alignment wrapper
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));

        if (isSent) {
            wrapper.add(Box.createHorizontalGlue());
            wrapper.add(bubble);
            wrapper.add(Box.createHorizontalStrut(8));
        } else {
            wrapper.add(Box.createHorizontalStrut(8));
            wrapper.add(bubble);
            wrapper.add(Box.createHorizontalGlue());
        }

        add(wrapper, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }
}
