package org.knouauto;

import org.knouauto.logger.ColorLogger;
import org.knouauto.settings.AutoLoginStore;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class GUI extends JFrame {

    private JTextField userIdField;
    private JPasswordField passwordField;
    private JCheckBox headlessCheckBox;
    private JCheckBox muteAudioCheckBox;
    private JTextPane logTextPane;
    private JButton startButton;
    private JButton stopButton;
    private JCheckBox overLimitCheckBox;
    private JCheckBox autoLoginCheckBox;

    private final ColorLogger log;
    private final AutoPlayer autoPlayer;
    private final AutoLoginStore autoLoginStore = new AutoLoginStore();

    public GUI() {
        setTitle("KNOU Auto Player");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- 아이콘 설정 ---
        try {
            java.net.URL iconURL = GUI.class.getResource("/icon.png");
            if (iconURL != null) {
                setIconImage(new ImageIcon(iconURL).getImage());
            }
        } catch (Exception e) {
            System.err.println("아이콘 로딩 실패: " + e.getMessage());
            e.printStackTrace();
        }
        // --- 아이콘 설정 종료 ---

        initComponents();
        addActionListeners();

        log = new ColorLogger(logTextPane);
        autoPlayer = new AutoPlayer(log);

        // 작업 완료 시 버튼 상태로 초기 상태로 변경하는 콜백 함수
        autoPlayer.setStopCallback(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        loadAutoLogin();
    }

    private void initComponents() {
        JPanel inputPanel = new JPanel(new GridLayout(4, 2));

        JLabel userIdLabel = new JLabel("아이디:");
        userIdField = new JTextField();
        JLabel passwordLabel = new JLabel("비밀번호:");
        passwordField = new JPasswordField();

        headlessCheckBox = new JCheckBox("백그라운드 실행(Headless)");
        muteAudioCheckBox = new JCheckBox("음소거");
        overLimitCheckBox = new JCheckBox("일일 한도 도달 시 다음 과목 수강");
        autoLoginCheckBox = new JCheckBox("자동 로그인");

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
        inputPanel.add(overLimitCheckBox);
        inputPanel.add(autoLoginCheckBox);

        // 버튼들을 별도의 패널에 추가하여 하단에 배치
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }


    private void addActionListeners() {
        startButton.addActionListener(e -> {
            String userId = userIdField.getText();
            String userPassword = new String(passwordField.getPassword());
            boolean enableHeadless = headlessCheckBox.isSelected();
            boolean muteAudio = muteAudioCheckBox.isSelected();
            boolean overLimit = overLimitCheckBox.isSelected();

            // 자동 로그인 정보는 로그인에 성공했을 때만 저장
            AutoLoginStore.Settings settings = new AutoLoginStore.Settings(userId, enableHeadless, muteAudio, overLimit);
            autoPlayer.setLoginSuccessCallback(() -> saveAutoLogin(settings, userPassword));

            autoPlayer.start(userId, userPassword, enableHeadless, muteAudio, overLimit);

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });

        stopButton.addActionListener(e -> {
            autoPlayer.stop();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        // 사용자가 직접 체크를 해제하면 저장된 자동 로그인 정보를 바로 삭제
        autoLoginCheckBox.addActionListener(e -> {
            if (autoLoginCheckBox.isSelected()) {
                log.info("로그인에 성공하면 아이디/비밀번호를 저장하고, 다음 실행 시 자동으로 시작합니다.");
                return;
            }
            try {
                autoLoginStore.clear();
                log.info("저장된 자동 로그인 정보를 삭제했습니다.");
            } catch (Exception ex) {
                log.error("자동 로그인 정보 삭제 실패: " + ex.getMessage());
            }
        });
    }

    // 저장된 자동 로그인 정보가 있으면 입력란을 채우고 바로 시작
    private void loadAutoLogin() {
        Optional<AutoLoginStore.Settings> saved = autoLoginStore.load();
        if (saved.isEmpty()) {
            return;
        }

        AutoLoginStore.Settings settings = saved.get();
        userIdField.setText(settings.userId());
        headlessCheckBox.setSelected(settings.headless());
        muteAudioCheckBox.setSelected(settings.muteAudio());
        overLimitCheckBox.setSelected(settings.overLimit());
        autoLoginCheckBox.setSelected(true);

        try {
            passwordField.setText(autoLoginStore.loadPassword(settings.userId()));
        } catch (Exception e) {
            log.error("저장된 비밀번호를 불러오지 못해 자동 로그인을 건너뜁니다: " + e.getMessage());
            return;
        }

        log.info("자동 로그인 정보로 시작합니다. 중지하려면 Stop을 누르세요.");
        SwingUtilities.invokeLater(startButton::doClick);
    }

    private void saveAutoLogin(AutoLoginStore.Settings settings, String password) {
        if (!autoLoginCheckBox.isSelected()) {
            return;
        }
        try {
            autoLoginStore.save(settings, password);
            log.info("자동 로그인 정보를 저장했습니다.");
        } catch (Exception e) {
            log.error("자동 로그인 정보 저장 실패: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI();
            gui.setVisible(true);
        });
    }
}
