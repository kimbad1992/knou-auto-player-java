package org.knouauto.logger;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ColorLogger {

    private JTextPane logTextPane;
    private StyledDocument doc;

    public ColorLogger(JTextPane logTextPane) {
        this.logTextPane = logTextPane;
        this.doc = logTextPane.getStyledDocument();
    }

    public void info(String message) {
        logMessage(message, "INFO");
    }

    public void warn(String message) {
        logMessage(message, "WARN");
    }

    public void error(String message) {
        logMessage(message, "ERROR");
    }

    public void success(String message) {
        logMessage(message, "SUCCESS");
    }

    public void system(String message) {
        logMessage(message, "SYSTEM");
    }

    public void logProgress(String title, double progress) {
        SwingUtilities.invokeLater(() -> {
            String message = String.format("%s 수강 중... %.2f%% 완료...", title, progress);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

            try {
                // 기존 텍스트에서 마지막 줄을 찾아서 지우기
                int lastLineStart = doc.getText(0, doc.getLength()).lastIndexOf('\n', doc.getLength() - 2);
                if (lastLineStart >= 0) {
                    doc.remove(lastLineStart + 1, doc.getLength() - lastLineStart - 1);
                }

                // 새로운 진행 상황 추가
                appendColoredText(timestamp + " - ", Color.BLACK); // 시간
                appendColoredText("[INFO] ", Color.BLUE); // 로그 레벨
                appendColoredText(message + "\n", Color.DARK_GRAY); // 메시지 내용
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }


    private void logMessage(String message, String level) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

            try {
                appendColoredText(timestamp + " - ", Color.BLACK); // 시간
                appendColoredText("[" + level + "] ", getColorForLevel(level)); // 로그 레벨
                appendColoredText(message + "\n", Color.DARK_GRAY); // 메시지 내용
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void appendColoredText(String text, Color color) throws BadLocationException {
        SimpleAttributeSet attributeSet = new SimpleAttributeSet();
        StyleConstants.setForeground(attributeSet, color);
        doc.insertString(doc.getLength(), text, attributeSet);
    }

    private Color getColorForLevel(String level) {
        return switch (level) {
            case "ERROR" -> Color.RED;
            case "INFO" -> Color.BLUE;
            case "SUCCESS" -> Color.GREEN;
            case "WARN" -> Color.ORANGE;
            case "SYSTEM" -> Color.GRAY;
            default -> Color.BLACK;
        };
    }
}
