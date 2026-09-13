package com.chatapp.client;

import com.chatapp.gui.LoginFrame;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Main entry point for the Chat Client application.
 *
 * <p>Initializes the FlatLaf dark theme and displays the Login screen.
 * All GUI operations are dispatched to the Swing EDT (Event Dispatch Thread)
 * using {@link SwingUtilities#invokeLater} to ensure thread safety.</p>
 */
public class ClientApplication {

    public static void main(String[] args) {
        // Set FlatLaf Dark Look and Feel for modern UI appearance
        try {
            FlatDarkLaf.setup();
            // Customize some FlatLaf defaults
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ScrollBar.width", 8);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
        } catch (Exception e) {
            System.err.println("Failed to set FlatLaf theme: " + e.getMessage());
            // Fall back to system look and feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        // Launch the Login frame on the EDT
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}
