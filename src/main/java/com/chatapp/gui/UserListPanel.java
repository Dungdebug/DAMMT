package com.chatapp.gui;

import com.chatapp.client.UserSession;
import com.chatapp.gui.components.ModernScrollPane;
import com.chatapp.gui.components.StatusIndicator;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sidebar panel displaying the list of active online users.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Online counter badge</li>
 *   <li>Custom cell renderer with status indicator and unread dots</li>
 *   <li>User selection listener callback</li>
 * </ul>
 */
public class UserListPanel extends JPanel {

    private static final Color BG_SIDEBAR = new Color(0x2B, 0x2D, 0x31);
    private static final Color TEXT_COLOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color ACCENT = new Color(0x58, 0x65, 0xF2);
    private static final Color USER_SELECTED = new Color(0x40, 0x44, 0x4B);
    private static final Color DIVIDER = new Color(0x3E, 0x40, 0x47);

    private final DefaultListModel<String> listModel;
    private final JList<String> userList;
    private final JLabel countLabel;
    private final Map<String, Boolean> unreadMap = new HashMap<>();

    private Consumer<String> userSelectedListener;

    public UserListPanel(UserSession session) {
        setLayout(new BorderLayout());
        setBackground(BG_SIDEBAR);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DIVIDER));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Online Users");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(TEXT_COLOR);
        header.add(title, BorderLayout.WEST);

        countLabel = new JLabel("0");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        countLabel.setForeground(ACCENT);
        header.add(countLabel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // List
        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setForeground(TEXT_COLOR);
        userList.setSelectionBackground(USER_SELECTED);
        userList.setSelectionForeground(TEXT_COLOR);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userList.setFixedCellHeight(48);
        userList.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        userList.setCellRenderer(new UserCellRenderer());

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userSelectedListener != null) {
                String selected = userList.getSelectedValue();
                if (selected != null) {
                    unreadMap.remove(selected);
                    userList.repaint();
                    userSelectedListener.accept(selected);
                }
            }
        });

        add(new ModernScrollPane(userList), BorderLayout.CENTER);

        if (session != null) {
            setUsers(session.getOnlineUsers());
        }
    }

    public void setUserSelectedListener(Consumer<String> listener) {
        this.userSelectedListener = listener;
    }

    public void setUsers(Collection<String> users) {
        listModel.clear();
        for (String user : users) {
            listModel.addElement(user);
        }
        countLabel.setText(String.valueOf(listModel.getSize()));
    }

    public void addUser(String user) {
        if (!listModel.contains(user)) {
            listModel.addElement(user);
            countLabel.setText(String.valueOf(listModel.getSize()));
        }
    }

    public void removeUser(String user) {
        listModel.removeElement(user);
        unreadMap.remove(user);
        countLabel.setText(String.valueOf(listModel.getSize()));
    }

    public void markUnread(String user) {
        if (!user.equals(userList.getSelectedValue())) {
            unreadMap.put(user, true);
            userList.repaint();
        }
    }

    public String getSelectedUser() {
        return userList.getSelectedValue();
    }

    public void selectUser(String user) {
        userList.setSelectedValue(user, true);
    }

    private class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            JPanel cell = new JPanel(new BorderLayout(10, 0));
            cell.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            cell.setBackground(isSelected ? USER_SELECTED : BG_SIDEBAR);

            String username = (String) value;

            StatusIndicator si = new StatusIndicator();
            si.setOnline(true);
            cell.add(si, BorderLayout.WEST);

            JLabel nameLabel = new JLabel(username);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            nameLabel.setForeground(TEXT_COLOR);
            cell.add(nameLabel, BorderLayout.CENTER);

            if (unreadMap.containsKey(username)) {
                JPanel dot = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
