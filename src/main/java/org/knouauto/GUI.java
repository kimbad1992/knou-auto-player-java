package org.knouauto;

import com.formdev.flatlaf.FlatLightLaf;
import org.knouauto.logger.ColorLogger;
import org.knouauto.settings.AutoLoginStore;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

public class GUI extends JFrame implements ColorLogger.StatusListener {

    private static final String APP_NAME = "KNOU Auto Player";
    private static final String STATUS_IDLE = "대기 중";

    // 마지막 창 크기·위치
    private static final String KEY_WINDOW_X = "windowX";
    private static final String KEY_WINDOW_Y = "windowY";
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";

    // 8px 그리드
    private static final int MARGIN = 16;
    private static final int SECTION_GAP = 20;
    private static final int LOG_PREFERRED_HEIGHT = 240;
    private static final int LOG_MINIMUM_HEIGHT = 120;
    private static final int EXIT_WAIT_MILLIS = 15_000;

    private JTextField userIdField;
    private JPasswordField passwordField;
    private JCheckBox headlessCheckBox;
    private JCheckBox muteAudioCheckBox;
    private JCheckBox overLimitCheckBox;
    private JCheckBox autoLoginCheckBox;
    private JCheckBox skipLecturePopupCheckBox;
    private JTextPane logTextPane;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton startButton;

    private final ColorLogger log;
    private final AutoPlayer autoPlayer;
    private final AutoLoginStore autoLoginStore = new AutoLoginStore();
    private final Preferences windowPrefs = Preferences.userNodeForPackage(GUI.class);

    // 저장된 자동 로그인 정보로 시작된 경우 true (사용자가 직접 누르면 false)
    private boolean startedByAutoLogin = false;
    private boolean running = false;
    // 창 닫기에서 "중지하고 종료"를 고른 경우: 정리가 끝나면 종료
    private boolean exitAfterStop = false;

    public GUI() {
        setTitle(APP_NAME);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        loadIcon();

        setContentPane(buildContent());

        log = new ColorLogger(logTextPane);
        log.setStatusListener(this);
        autoPlayer = new AutoPlayer(log);
        autoPlayer.setStopCallback(this::onStopped);

        addListeners();
        getRootPane().setDefaultButton(startButton);

        pack();
        Dimension packed = getSize();
        setMinimumSize(new Dimension(packed.width, packed.height - (LOG_PREFERRED_HEIGHT - LOG_MINIMUM_HEIGHT)));
        restoreWindowBounds(packed);

        updateStartButtonState();
        // 화면만 확인할 때: java -Dknou.skipAutoLogin=true -jar ...
        if (!Boolean.getBoolean("knou.skipAutoLogin")) {
            loadAutoLogin();
        }
    }

    private void loadIcon() {
        try {
            java.net.URL iconURL = GUI.class.getResource("/icon.png");
            if (iconURL != null) {
                setIconImage(new ImageIcon(iconURL).getImage());
            }
        } catch (Exception e) {
            System.err.println("아이콘 로딩 실패: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 레이아웃

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, SECTION_GAP));
        content.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
        content.add(buildTopPanel(), BorderLayout.NORTH);
        content.add(buildLogPanel(), BorderLayout.CENTER);
        content.add(buildBottomPanel(), BorderLayout.SOUTH);
        return content;
    }

    // 아이디/비밀번호 폼 + 옵션 두 묶음(두 열)
    private JPanel buildTopPanel() {
        userIdField = new JTextField(20);
        passwordField = new JPasswordField(20);

        headlessCheckBox = new JCheckBox("브라우저 창 숨기기");
        headlessCheckBox.setToolTipText("Chrome 창을 띄우지 않고 백그라운드에서 실행합니다.");
        muteAudioCheckBox = new JCheckBox("음소거");
        overLimitCheckBox = new JCheckBox("한도 도달 시 다음 과목으로");
        overLimitCheckBox.setToolTipText("일일 수강 한도에 도달하면 다음 과목으로 넘어갑니다.");
        autoLoginCheckBox = new JCheckBox("자동 로그인");
        autoLoginCheckBox.setToolTipText("로그인에 성공하면 아이디/비밀번호를 저장하고, 다음 실행 시 자동으로 시작합니다.");
        skipLecturePopupCheckBox = new JCheckBox("이전 선택으로 바로 시작");
        skipLecturePopupCheckBox.setToolTipText("이전에 선택한 과목이 저장되어 있으면 과목 선택 창 없이 바로 시작합니다.");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 8, 10);
        form.add(new JLabel("아이디"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(userIdField, c);

        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 10);
        form.add(new JLabel("비밀번호"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(passwordField, c);

        JPanel options = new JPanel(new GridLayout(1, 2, 24, 0));
        options.add(optionGroup("실행 방식", headlessCheckBox, muteAudioCheckBox));
        options.add(optionGroup("동작", overLimitCheckBox, autoLoginCheckBox, skipLecturePopupCheckBox));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        form.setAlignmentX(LEFT_ALIGNMENT);
        options.setAlignmentX(LEFT_ALIGNMENT);
        top.add(form);
        top.add(Box.createVerticalStrut(SECTION_GAP));
        top.add(options);
        return top;
    }

    // 세미볼드 제목 아래 체크박스를 세로로
    private JPanel optionGroup(String title, JCheckBox... boxes) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));

        JLabel heading = sectionLabel(title);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        group.add(heading);
        group.add(Box.createVerticalStrut(6));
        for (int i = 0; i < boxes.length; i++) {
            if (i > 0) {
                group.add(Box.createVerticalStrut(2));
            }
            boxes[i].setAlignmentX(LEFT_ALIGNMENT);
            group.add(boxes[i]);
        }
        group.add(Box.createVerticalGlue());
        return group;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("FlatLaf.styleClass", "semibold");
        return label;
    }

    private JLabel secondaryLabel(String text) {
        JLabel label = new JLabel(text);
        Color secondary = UIManager.getColor("Label.disabledForeground");
        if (secondary != null) {
            label.setForeground(secondary);
        }
        return label;
    }

    // 로그 제목 줄(복사·저장·지우기) + 로그 영역
    private JPanel buildLogPanel() {
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setMargin(new Insets(8, 10, 8, 10));
        // 읽기 전용이어도 입력란처럼 흰 배경을 유지
        Color textBackground = UIManager.getColor("TextPane.background");
        if (textBackground != null) {
            logTextPane.setBackground(new Color(textBackground.getRGB()));
        }
        JScrollPane scrollPane = new JScrollPane(logTextPane);
        scrollPane.setPreferredSize(new Dimension(0, LOG_PREFERRED_HEIGHT));
        scrollPane.setMinimumSize(new Dimension(0, LOG_MINIMUM_HEIGHT));

        JButton copyButton = toolButton("복사", "로그를 클립보드에 복사");
        JButton saveButton = toolButton("저장", "로그를 파일로 저장");
        JButton clearButton = toolButton("지우기", "로그 비우기");
        copyButton.addActionListener(e -> copyLog());
        saveButton.addActionListener(e -> saveLog());
        clearButton.addActionListener(e -> log.clear());

        Box header = Box.createHorizontalBox();
        header.add(sectionLabel("로그"));
        header.add(Box.createHorizontalGlue());
        header.add(copyButton);
        header.add(Box.createHorizontalStrut(4));
        header.add(saveButton);
        header.add(Box.createHorizontalStrut(4));
        header.add(clearButton);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JButton toolButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        Color secondary = UIManager.getColor("Label.disabledForeground");
        if (secondary != null) {
            button.setForeground(secondary);
        }
        return button;
    }

    // 상태 텍스트 + 가는 진행 바, 오른쪽에 시작/중지 버튼
    private JPanel buildBottomPanel() {
        statusLabel = secondaryLabel(STATUS_IDLE);
        progressBar = new JProgressBar(0, 100);
        startButton = new JButton("시작");
        startButton.putClientProperty("JComponent.minimumWidth", 96);

        JPanel bottom = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        bottom.add(statusLabel, c);

        c.gridy = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 0, 0);
        bottom.add(progressBar, c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridheight = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 24, 0, 0);
        bottom.add(startButton, c);
        return bottom;
    }

    // ---------------------------------------------------------------- 동작

    private void addListeners() {
        startButton.addActionListener(e -> onStartStopClicked());

        // Enter: 아이디 → 비밀번호로 이동, 비밀번호 → 시작
        userIdField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> {
            if (startButton.isEnabled()) {
                startButton.doClick();
            }
        });

        DocumentListener inputWatcher = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateStartButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateStartButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateStartButtonState();
            }
        };
        userIdField.getDocument().addDocumentListener(inputWatcher);
        passwordField.getDocument().addDocumentListener(inputWatcher);

        // 사용자가 직접 체크를 해제하면 저장된 자동 로그인 정보를 바로 삭제
        autoLoginCheckBox.addActionListener(e -> {
            if (autoLoginCheckBox.isSelected()) {
                log.info("자동 로그인: 로그인에 성공하면 아이디와 비밀번호를 저장합니다.");
                return;
            }
            try {
                autoLoginStore.clear();
                log.info("자동 로그인 정보를 삭제했습니다.");
            } catch (Exception ex) {
                log.error("자동 로그인 정보 삭제 실패: " + ex.getMessage());
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                userIdField.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });
    }

    private void onStartStopClicked() {
        if (running) {
            requestStop();
            return;
        }

        String userId = userIdField.getText().trim();
        String userPassword = new String(passwordField.getPassword());
        boolean enableHeadless = headlessCheckBox.isSelected();
        boolean muteAudio = muteAudioCheckBox.isSelected();
        boolean overLimit = overLimitCheckBox.isSelected();
        boolean skipLecturePopup = skipLecturePopupCheckBox.isSelected();
        boolean autoLoginStart = startedByAutoLogin;
        startedByAutoLogin = false;

        // 자동 로그인 정보는 로그인에 성공했을 때만 저장
        AutoLoginStore.Settings settings = new AutoLoginStore.Settings(
                userId, enableHeadless, muteAudio, overLimit, skipLecturePopup);
        autoPlayer.setLoginSuccessCallback(() -> saveAutoLogin(settings, userPassword));
        // 저장된 정보로 시작했는데 로그인이 실패하면 자동 로그인을 해제 (직접 입력한 경우는 유지)
        autoPlayer.setLoginFailureCallback(() -> {
            if (autoLoginStart) {
                disableAutoLoginAfterFailure();
            }
        });
        autoPlayer.setLectureSelectionCallback(this::saveSelectedLectures);

        setRunning(true);
        showBusy("시작하는 중");
        autoPlayer.start(userId, userPassword, enableHeadless, muteAudio, overLimit,
                skipLecturePopup, autoLoginStore.loadSelectedLectures());
    }

    private void requestStop() {
        startButton.setEnabled(false);
        showBusy("중지하는 중");
        autoPlayer.stop();
    }

    // AutoPlayer 작업이 끝나면(정상 종료·중지·오류) 호출
    private void onStopped() {
        setRunning(false);
        statusLabel.setText(STATUS_IDLE);
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        updateStartButtonState();
        if (exitAfterStop) {
            exitNow();
        }
    }

    private void setRunning(boolean running) {
        this.running = running;
        startButton.setText(running ? "중지" : "시작");
        // 실행 중에는 Enter로 중지되지 않도록 기본 버튼 해제
        getRootPane().setDefaultButton(running ? null : startButton);
        // 실행 중에는 시작 시점의 아이디/비밀번호가 쓰이므로 입력란만 잠근다
        userIdField.setEnabled(!running);
        passwordField.setEnabled(!running);
    }

    // 아이디/비밀번호가 비어 있으면 시작 불가. 실행 중에는 중지 버튼으로 항상 활성.
    private void updateStartButtonState() {
        if (running) {
            return;
        }
        boolean filled = !userIdField.getText().trim().isEmpty() && passwordField.getPassword().length > 0;
        startButton.setEnabled(filled);
    }

    private void showBusy(String text) {
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        statusLabel.setText(text);
        progressBar.setIndeterminate(true);
    }

    @Override
    public void onStatus(String text) {
        if (!running) {
            return;
        }
        showBusy(text);
    }

    @Override
    public void onProgress(String title, String elapsedTime, String totalTime, double percent) {
        if (!running) {
            return;
        }
        int value = (int) Math.max(0, Math.min(100, Math.round(percent)));
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        statusLabel.setText(title.replace(" :: ", " · ") + " · " + value + "% · " + elapsedTime + " / " + totalTime);
        progressBar.setIndeterminate(false);
        progressBar.setValue(value);
    }

    // ---------------------------------------------------------------- 종료

    private void onWindowClosing() {
        if (!running) {
            exitNow();
            return;
        }
        Object[] options = {"중지하고 종료", "취소"};
        int choice = JOptionPane.showOptionDialog(this, "실행 중입니다. 중지하고 종료할까요?", "종료",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        if (choice != 0) {
            return;
        }
        exitAfterStop = true;
        requestStop();
        // 드라이버 정리가 끝나지 않더라도 일정 시간 뒤에는 종료
        Timer fallback = new Timer(EXIT_WAIT_MILLIS, e -> exitNow());
        fallback.setRepeats(false);
        fallback.start();
    }

    private void exitNow() {
        saveWindowBounds();
        dispose();
        System.exit(0);
    }

    // ---------------------------------------------------------------- 창 크기·위치 기억

    private void restoreWindowBounds(Dimension packed) {
        int width = windowPrefs.getInt(KEY_WINDOW_WIDTH, -1);
        int height = windowPrefs.getInt(KEY_WINDOW_HEIGHT, -1);
        int x = windowPrefs.getInt(KEY_WINDOW_X, Integer.MIN_VALUE);
        int y = windowPrefs.getInt(KEY_WINDOW_Y, Integer.MIN_VALUE);

        Dimension minimum = getMinimumSize();
        if (width < minimum.width || height < minimum.height) {
            setSize(Math.max(packed.width, 560), packed.height);
            setLocationRelativeTo(null);
            return;
        }
        setSize(width, height);
        Rectangle bounds = new Rectangle(x, y, width, height);
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && isOnScreen(bounds)) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }
    }

    // 모니터 구성이 바뀌어 화면 밖으로 나간 위치는 쓰지 않는다
    private boolean isOnScreen(Rectangle bounds) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle screen = device.getDefaultConfiguration().getBounds();
            Rectangle visible = screen.intersection(bounds);
            if (visible.width >= 120 && visible.height >= 60) {
                return true;
            }
        }
        return false;
    }

    private void saveWindowBounds() {
        if ((getExtendedState() & MAXIMIZED_BOTH) != 0) {
            return;
        }
        Rectangle bounds = getBounds();
        windowPrefs.putInt(KEY_WINDOW_X, bounds.x);
        windowPrefs.putInt(KEY_WINDOW_Y, bounds.y);
        windowPrefs.putInt(KEY_WINDOW_WIDTH, bounds.width);
        windowPrefs.putInt(KEY_WINDOW_HEIGHT, bounds.height);
        try {
            windowPrefs.flush();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- 로그 복사·저장

    private void copyLog() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(log.getText()), null);
        log.info("로그를 복사했습니다.");
    }

    private void saveLog() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("로그 저장");
        chooser.setSelectedFile(new java.io.File("knou-auto-player-" + stamp + ".log"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        try {
            Files.writeString(target, log.getText(), StandardCharsets.UTF_8);
            log.info("로그를 저장했습니다: " + target);
        } catch (IOException e) {
            log.error("로그 저장 실패: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 자동 로그인

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
        skipLecturePopupCheckBox.setSelected(settings.skipLecturePopup());
        autoLoginCheckBox.setSelected(true);

        try {
            passwordField.setText(autoLoginStore.loadPassword(settings.userId()));
        } catch (Exception e) {
            log.error("저장된 비밀번호를 불러오지 못했습니다: " + e.getMessage());
            return;
        }

        log.info("저장된 정보로 자동 시작합니다.");
        startedByAutoLogin = true;
        SwingUtilities.invokeLater(() -> {
            if (startButton.isEnabled()) {
                startButton.doClick();
            }
        });
    }

    // 저장된 로그인 정보가 틀린 경우: 다음 실행에서 같은 실패를 반복하지 않도록 자동 로그인 해제
    private void disableAutoLoginAfterFailure() {
        autoLoginCheckBox.setSelected(false);
        try {
            autoLoginStore.clear();
            log.warn("저장된 로그인 정보가 맞지 않아 자동 로그인을 해제했습니다.");
        } catch (Exception e) {
            log.error("자동 로그인 정보 삭제 실패: " + e.getMessage());
        }
    }

    private void saveSelectedLectures(List<String> titles) {
        try {
            autoLoginStore.saveSelectedLectures(titles);
        } catch (Exception e) {
            log.error("과목 선택을 저장하지 못했습니다: " + e.getMessage());
        }
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

    // ---------------------------------------------------------------- 룩앤필

    /** FlatLaf 밝은 테마를 적용하고, 실패하면 시스템 룩앤필로 폴백. 어떤 창이든 만들기 전에 호출. */
    public static void setupLookAndFeel() {
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", APP_NAME);
            System.setProperty("apple.awt.application.appearance", "system");
        }
        try {
            if (FlatLightLaf.setup()) {
                applyFonts();
                applyUiTweaks();
                return;
            }
        } catch (Throwable e) {
            System.err.println("FlatLaf 적용 실패: " + e.getMessage());
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("시스템 룩앤필 적용 실패: " + e.getMessage());
        }
    }

    // 번들한 Pretendard를 기본 글꼴로. Windows/macOS에서 같은 글꼴로 보이게 한다. 실패하면 FlatLaf 기본 글꼴 유지.
    private static void applyFonts() {
        Font regular = loadFont("/fonts/Pretendard-Regular.otf");
        Font semibold = loadFont("/fonts/Pretendard-SemiBold.otf");
        if (regular == null) {
            return;
        }
        float size = 13f;
        UIManager.put("defaultFont", regular.deriveFont(size));
        if (semibold != null) {
            UIManager.put("semibold.font", semibold.deriveFont(size));
            UIManager.put("h4.font", semibold.deriveFont(size));
        }
    }

    private static Font loadFont(String resource) {
        try (java.io.InputStream in = GUI.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception e) {
            System.err.println("글꼴 로딩 실패 (" + resource + "): " + e.getMessage());
            return null;
        }
    }

    // 입력란 모서리를 버튼과 맞추고, 기본 버튼은 강조색으로 채운다 (강조색은 시스템 또는 FlatLaf 기본 파랑 하나)
    private static void applyUiTweaks() {
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Component.arrowType", "chevron");

        Color accent = UIManager.getColor("Component.accentColor");
        if (accent == null) {
            return;
        }
        UIManager.put("Button.default.background", accent);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.hoverBackground", shade(accent, 0.08f));
        UIManager.put("Button.default.pressedBackground", shade(accent, 0.16f));
        UIManager.put("Button.default.focusedBackground", accent);
        UIManager.put("Button.default.borderColor", accent);
        UIManager.put("Button.default.hoverBorderColor", shade(accent, 0.08f));
        UIManager.put("Button.default.focusedBorderColor", accent);
        UIManager.put("Button.default.boldText", false);
    }

    private static Color shade(Color color, float amount) {
        return new Color(
                Math.round(color.getRed() * (1 - amount)),
                Math.round(color.getGreen() * (1 - amount)),
                Math.round(color.getBlue() * (1 - amount)));
    }

    public static void main(String[] args) {
        setupLookAndFeel();
        SwingUtilities.invokeLater(() -> new GUI().setVisible(true));
    }
}
