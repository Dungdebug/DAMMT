package com.chatapp.gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * A modern-looking button with rounded corners and hover effects.
 */
public class RoundedButton extends JButton {

    private Color normalColor;
    private Color hoverColor;
    private Color pressColor;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public RoundedButton(String text) {
        super(text);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setFont(new Font("Segoe UI", Font.BOLD, 14));
        setForeground(Color.WHITE);

        // Default colors (blue accent)
        normalColor = new Color(0x58, 0x65, 0xF2);
        hoverColor = new Color(0x4E, 0x5B, 0xE0);
        pressColor = new Color(0x44, 0x51, 0xCC);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    public void setColors(Color normal, Color hover, Color press) {
        this.normalColor = normal;
        this.hoverColor = hover;
        this.pressColor = press;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg;
        if (!isEnabled()) {
            bg = normalColor.darker();
        } else if (isPressed) {
            bg = pressColor;
        } else if (isHovered) {
            bg = hoverColor;
        } else {
            bg = normalColor;
        }

        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));

        // Draw text centered
        FontMetrics fm = g2.getFontMetrics(getFont());
        g2.setFont(getFont());
        g2.setColor(isEnabled() ? getForeground() : new Color(180, 180, 180));
        int x = (getWidth() - fm.stringWidth(getText())) / 2;
        int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(getText(), x, y);

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(getText()) + 40;
        int h = fm.getHeight() + 16;
        return new Dimension(Math.max(w, 100), Math.max(h, 36));
    }
}
