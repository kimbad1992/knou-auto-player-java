package org.knouauto;

import org.knouauto.logger.ColorLogger;

import javax.swing.*;
import java.awt.*;

public class GUI extends JFrame {

    private JTextField userIdField;
    private JPasswordField passwordField;
    private JCheckBox headlessCheckBox;
    private JCheckBox muteAudioCheckBox;
    private JTextPane logTextPane;
    private JButton startButton;
    private JButton stopButton;

    private final ColorLogger log;
    private final AutoPlayer autoPlayer;

    public GUI() {
        setTitle("KNOU Auto Player");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initComponents();
        addActionListeners();

        log = new ColorLogger(logTextPane);
        autoPlayer = new AutoPlayer(log);
    }

    private void initComponents() {
        JPanel inputPanel = new JPanel(new GridLayout(5, 2));

        JLabel userIdLabel = new JLabel("아이디:");
        userIdField = new JTextField();
        JLabel passwordLabel = new JLabel("비밀번호:");
        passwordField = new JPasswordField();

        headlessCheckBox = new JCheckBox("백그라운드 실행(Headless)");
        muteAudioCheckBox = new JCheckBox("음소거");

        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);

        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logTextPane);

        inputPanel.add(userIdLabel);
        inputPanel.add(userIdField);
        inputPanel.add(passwordLabel);
        inputPanel.add(passwordField);
        inputPanel.add(headlessCheckBox);
        inputPanel.add(muteAudioCheckBox);
        inputPanel.add(startButton);
        inputPanel.add(stopButton);

        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void addActionListeners() {
        startButton.addActionListener(e -> {
            String userId = userIdField.getText();
            String userPassword = new String(passwordField.getPassword());
            boolean enableHeadless = headlessCheckBox.isSelected();
            boolean muteAudio = muteAudioCheckBox.isSelected();

            autoPlayer.start(userId, userPassword, enableHeadless, muteAudio);

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });

        stopButton.addActionListener(e -> {
            autoPlayer.stop();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI();
            gui.setVisible(true);
        });
    }
}
