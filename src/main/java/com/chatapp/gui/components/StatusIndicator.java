package com.chatapp.gui.components;

import javax.swing.*;
import java.awt.*;

/**
 * A small colored circle indicator showing online/offline status.
 */
public class StatusIndicator extends JPanel {

    private Color statusColor;

    public StatusIndicator() {
        this(new Color(0x23, 0xA5, 0x5A)); // Green by default
        setOpaque(false);
    }

    public StatusIndicator(Color color) {
        this.statusColor = color;
        setOpaque(false);
        setPreferredSize(new Dimension(12, 12));
        setMaximumSize(new Dimension(12, 12));
    }

    public void setOnline(boolean online) {
        this.statusColor = online
                ? new Color(0x23, 0xA5, 0x5A)   // Green
                : new Color(0x80, 0x84, 0x8E);   // Gray
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(statusColor);
        int size = Math.min(getWidth(), getHeight()) - 2;
        g2.fillOval(1, 1, size, size);
        g2.dispose();
    }
}
