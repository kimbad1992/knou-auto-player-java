package org.knouauto.logger;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 로그 창에 이벤트(시작/완료/오류)를 남기고, 진행 상황은 상태 줄로 보낸다.
 * 모든 메서드는 어느 스레드에서나 호출할 수 있다. (UI 갱신은 EDT로 넘김)
 */
public class ColorLogger {

    /** 진행 상황을 받아 상태 줄에 표시하는 쪽(GUI)이 구현 */
    public interface StatusListener {
        /** 진행률을 알 수 없는 상태 (예: 로그인 중) */
        void onStatus(String text);

        /** 영상 시청 진행률 */
        void onProgress(String title, String elapsedTime, String totalTime, double percent);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 차분한 톤의 오류/성공 색. 나머지 레벨은 기본 글자색.
    private static final Color ERROR_COLOR = new Color(0xB4, 0x23, 0x18);
    private static final Color SUCCESS_COLOR = new Color(0x1F, 0x7A, 0x3E);

    private final JTextPane logTextPane;
    private final StyledDocument doc;
    private StatusListener statusListener;

    public ColorLogger(JTextPane logTextPane) {
        this.logTextPane = logTextPane;
        this.doc = logTextPane.getStyledDocument();

        // 로그도 본문과 같은 글꼴을 쓴다. (macOS의 Menlo는 Java에서 영문 글자가 빠지는 문제가 있음)
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        logTextPane.setFont(base.deriveFont(Font.PLAIN, 12.5f));

        // 캐럿이 스크롤을 끌고 다니지 않도록 하고, 자동 스크롤은 직접 처리
        if (logTextPane.getCaret() instanceof DefaultCaret caret) {
            caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        }
    }

    public void setStatusListener(StatusListener statusListener) {
        this.statusListener = statusListener;
    }

    public void info(String message) {
        append(message, null);
    }

    public void warn(String message) {
        append(message, null);
    }

    public void error(String message) {
        append(brief(message), ERROR_COLOR);
    }

    // Selenium 예외 메시지는 여러 줄에 세션 정보까지 붙으므로 첫 줄만, 길면 잘라서 보여준다. 전체는 stderr로.
    private static String brief(String message) {
        if (message == null) {
            return "";
        }
        String firstLine = message.strip().split("\\R", 2)[0].strip();
        if (firstLine.length() > 160) {
            firstLine = firstLine.substring(0, 157) + "...";
        }
        if (!firstLine.equals(message.strip())) {
            System.err.println(message);
        }
        return firstLine;
    }

    public void success(String message) {
        append(message, SUCCESS_COLOR);
    }

    public void system(String message) {
        append(message, null);
    }

    /** 상태 줄 텍스트만 바꾼다. (로그에는 남지 않음) */
    public void status(String text) {
        StatusListener listener = statusListener;
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onStatus(text));
        }
    }

    /** 영상 시청 진행률을 상태 줄로 보낸다. (로그에는 남지 않음) */
    public void progress(String title, String elapsedTime, String totalTime, double percent) {
        StatusListener listener = statusListener;
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onProgress(title, elapsedTime, totalTime, percent));
        }
    }

    /** 로그 전체 텍스트 (복사·저장용, EDT에서 호출) */
    public String getText() {
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    /** 로그 비우기 (EDT에서 호출) */
    public void clear() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void append(String message, Color messageColor) {
        String time = LocalTime.now().format(TIME_FORMAT);
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = verticalScrollBar();
            boolean wasAtBottom = bar == null || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
            try {
                insert(time + "  ", timeColor());
                insert(message + "\n", messageColor);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            // 사용자가 위로 올려 둔 경우는 그대로 두고, 끝을 보고 있었으면 따라 내려간다
            if (wasAtBottom && bar != null) {
                SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
            }
        });
    }

    private void insert(String text, Color color) throws BadLocationException {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        if (color != null) {
            StyleConstants.setForeground(attributes, color);
        }
        doc.insertString(doc.getLength(), text, attributes);
    }

    private Color timeColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }

    private JScrollBar verticalScrollBar() {
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, logTextPane);
        return scrollPane == null ? null : scrollPane.getVerticalScrollBar();
    }
}
