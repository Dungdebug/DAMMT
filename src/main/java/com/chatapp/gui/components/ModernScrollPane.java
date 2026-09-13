package com.chatapp.gui.components;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * A JScrollPane with thin, modern-looking scrollbars.
 */
public class ModernScrollPane extends JScrollPane {

    public ModernScrollPane(Component view) {
        super(view);
        setBorder(null);
        setBackground(new Color(0x2B, 0x2D, 0x31));
        getViewport().setBackground(new Color(0x2B, 0x2D, 0x31));

        // Style the vertical scrollbar
        getVerticalScrollBar().setUI(new ThinScrollBarUI());
        getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        getVerticalScrollBar().setOpaque(false);

        // Style the horizontal scrollbar
        getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
        getHorizontalScrollBar().setOpaque(false);

        // Smooth scrolling
        getVerticalScrollBar().setUnitIncrement(16);
        getHorizontalScrollBar().setUnitIncrement(16);
    }

    /**
     * Thin, minimal scrollbar UI.
     */
    private static class ThinScrollBarUI extends BasicScrollBarUI {

        private static final Color THUMB_COLOR = new Color(0x55, 0x57, 0x5E);
        private static final Color THUMB_HOVER = new Color(0x70, 0x72, 0x79);

        @Override
        protected void configureScrollBarColors() {
            thumbColor = THUMB_COLOR;
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? THUMB_HOVER : THUMB_COLOR);
            g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // Transparent track
        }
    }
}
