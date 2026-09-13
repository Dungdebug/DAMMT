package com.chatapp.gui;

import com.chatapp.gui.components.RoundedButton;
import com.chatapp.utils.FileUtils;

import javax.swing.*;
import java.awt.*;

/**
 * Modern modal/modeless dialog displaying real-time file transfer progress, speed, and cancel button.
 */
public class FileTransferDialog extends JDialog {

    private static final Color BG_PANEL = new Color(0x2B, 0x2D, 0x31);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color LABEL_COLOR = new Color(0x94, 0x9B, 0xA4);

    private final JLabel titleLabel;
    private final JLabel fileDetailLabel;
    private final JProgressBar progressBar;
    private final JLabel speedLabel;
    private final RoundedButton cancelButton;

    private Runnable onCancelListener;

    public FileTransferDialog(Frame parent, String title, boolean isSender) {
        super(parent, title, false);
        setSize(420, 210);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        panel.setBackground(BG_PANEL);

        // Top labels
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        topPanel.setOpaque(false);

        titleLabel = new JLabel(isSender ? "📤 Sending File..." : "📥 Receiving File...");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(TEXT_COLOR);
        topPanel.add(titleLabel);

        fileDetailLabel = new JLabel("Preparing transfer...");
        fileDetailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fileDetailLabel.setForeground(LABEL_COLOR);
        topPanel.add(fileDetailLabel);

        panel.add(topPanel, BorderLayout.NORTH);

        // Center: Progress bar + Speed/Bytes
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(380, 24));
        centerPanel.add(progressBar, BorderLayout.CENTER);

        speedLabel = new JLabel("0% (0 KB / 0 KB)");
        speedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        speedLabel.setForeground(LABEL_COLOR);
        centerPanel.add(speedLabel, BorderLayout.SOUTH);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom: Cancel button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomPanel.setOpaque(false);

        cancelButton = new RoundedButton("Cancel");
        cancelButton.setColors(
                new Color(0xED, 0x42, 0x45),
                new Color(0xD6, 0x3B, 0x3E),
                new Color(0xBF, 0x34, 0x37));
        cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cancelButton.addActionListener(e -> {
            if (onCancelListener != null) {
                onCancelListener.run();
            }
            dispose();
        });
        bottomPanel.add(cancelButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }

    public void setOnCancel(Runnable listener) {
        this.onCancelListener = listener;
    }

    public void updateProgress(int percent, long transferred, long total, String fileName) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            fileDetailLabel.setText(fileName + " (" + FileUtils.formatSize(total) + ")");
            speedLabel.setText(percent + "% (" + FileUtils.formatSize(transferred) + " / " + FileUtils.formatSize(total) + ")");
        });
    }

    public void complete(boolean verified) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            fileDetailLabel.setText(verified ? "Transfer completed & SHA-256 verified!" : "Transfer completed!");
            cancelButton.setText("Close");
        });
    }
}
