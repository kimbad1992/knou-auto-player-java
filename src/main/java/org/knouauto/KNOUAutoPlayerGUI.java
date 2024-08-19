package org.knouauto;

import org.knouauto.enums.LectureSelector;
import org.knouauto.enums.PlayerSelector;
import org.knouauto.model.Lecture;
import org.knouauto.model.Video;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class KNOUAutoPlayerGUI extends JFrame {

    public static String LOGIN_URL = "https://ucampus.knou.ac.kr/ekp/user/login/retrieveULOLogin.do";
    public static String STUDY_URL = "https://ucampus.knou.ac.kr/ekp/user/study/retrieveUMYStudy.sdo";
    public static int VIDEO_ELAPSE_PERCENT = 60;

    private JTextField userIdField;
    private JPasswordField passwordField;
    private JCheckBox headlessCheckBox;
    private JCheckBox muteAudioCheckBox;
    private JTextArea logTextArea; // 로그를 기록할 JTextArea
    private JButton startButton;
    private JButton stopButton;
    private WebDriver driver;
    private WebDriverWait wait;
    private AutoPlayerWorker autoPlayerWorker;

    // 기타 멤버 변수 선언
    private JButton stopPlayingButton; // 비디오 재생 중지 버튼
    private boolean stopPlaying = false; // 재생 중지 플래그

    public KNOUAutoPlayerGUI() {
        setTitle("KNOU Auto Player");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(5, 2));

        JLabel userIdLabel = new JLabel("아이디:");
        userIdField = new JTextField();
        JLabel passwordLabel = new JLabel("비밀번호:");
        passwordField = new JPasswordField();

        headlessCheckBox = new JCheckBox("브라우저 없이");
        muteAudioCheckBox = new JCheckBox("음소거");

        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false); // 초기에는 비활성화 상태

        logTextArea = new JTextArea();
        logTextArea.setEditable(false); // 로그는 사용자가 수정할 수 없도록 설정
        JScrollPane scrollPane = new JScrollPane(logTextArea); // 스크롤 가능하도록 설정

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userId = userIdField.getText();
                String userPassword = new String(passwordField.getPassword());
                boolean enableHeadless = headlessCheckBox.isSelected();
                boolean muteAudio = muteAudioCheckBox.isSelected();
                autoPlayerWorker = new AutoPlayerWorker(userId, userPassword, enableHeadless, muteAudio);
                autoPlayerWorker.execute();
                startButton.setEnabled(false); // Start 버튼 비활성화
                stopButton.setEnabled(true);  // Stop 버튼 활성화
            }
        });

        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (autoPlayerWorker != null) {
                    autoPlayerWorker.cancel(true); // 백그라운드 작업 취소
                    stopButton.setEnabled(false); // Stop 버튼 비활성화
                    startButton.setEnabled(true); // Start 버튼 활성화
                    logMessage("작업이 중지되었습니다.", "INFO");
                }
            }
        });

        inputPanel.add(userIdLabel);
        inputPanel.add(userIdField);
        inputPanel.add(passwordLabel);
        inputPanel.add(passwordField);
        inputPanel.add(headlessCheckBox);
        inputPanel.add(muteAudioCheckBox);
        inputPanel.add(startButton);
        inputPanel.add(stopButton);

        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER); // 스크롤 가능한 텍스트 박스를 중앙에 추가

        // 비디오 재생 중지 버튼 추가
        stopPlayingButton = new JButton("Stop Video");
        stopPlayingButton.setEnabled(false); // 초기에는 비활성화 상태
        stopPlayingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopPlaying = !stopPlaying; // 재생 중지 플래그 설정
            }
        });

        // inputPanel.add(stopPlayingButton); // 패널에 버튼 추가
    }

    private class AutoPlayerWorker extends SwingWorker<Void, String> {
        private String userId;
        private String userPassword;
        private boolean enableHeadless;
        private boolean muteAudio;

        public AutoPlayerWorker(String userId, String userPassword, boolean enableHeadless, boolean muteAudio) {
            this.userId = userId;
            this.userPassword = userPassword;
            this.enableHeadless = enableHeadless;
            this.muteAudio = muteAudio;
        }

        @Override
        protected Void doInBackground() {
            try {
                ChromeOptions options = new ChromeOptions();
                if (enableHeadless) {
                    options.addArguments("--headless");
                    options.addArguments("window-size=800x600");
                    options.addArguments("disable-gpu");
                }
                if (muteAudio) {
                    options.addArguments("--mute-audio");
                }

                driver = new ChromeDriver(options);
                wait = new WebDriverWait(driver, Duration.ofSeconds(5L));

                publish("로그인 중...");

                // 로그인
                driver.get(LOGIN_URL);
                driver.findElement(By.name("username")).sendKeys(userId);
                WebElement passwordElement = driver.findElement(By.name("password"));
                passwordElement.sendKeys(userPassword);
                passwordElement.sendKeys(Keys.RETURN);

                wait.until(ExpectedConditions.urlToBe(STUDY_URL));

                publish("로그인 성공, 강의 로딩 중...");

                loadAndPlayLectures();

            } catch (UnhandledAlertException e) {
                publish("알림 발생: " + e.getAlertText());
                e.printStackTrace();
            } catch (Exception e) {
                publish("에러 발생: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
                publish("작업이 완료되었습니다.");
            }
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {
                logMessage(message, "INFO");
            }
        }

        @Override
        protected void done() {
            startButton.setEnabled(true); // Start 버튼 활성화
            stopButton.setEnabled(false); // Stop 버튼 비활성화
        }
    }

    private List<Lecture> loadLectures() {
        List<Lecture> lectureList = new ArrayList<>();
        try {
            logMessage("강의 로딩 중...", "INFO");

            // 강의 목록을 가져오기
            // List<WebElement> lectures = driver.findElements(By.cssSelector(LectureSelector.ROOT.get()));
            List<WebElement> lectures = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(LectureSelector.ROOT.get())));

            for (WebElement lectureElement : lectures) {
                Lecture lecture = new Lecture();
                lecture.setId(lectureElement.getAttribute("id"));
                lecture.setTitle(lectureElement.findElement(By.cssSelector(LectureSelector.TITLE.get())).getText());
                lecture.setLectureElement(lectureElement);  // WebElement 저장
                logMessage(lecture.toString(), "INFO");

                // 강의를 펼치는 코드 추가 (JavaScript 사용)
                WebElement moreButton = lectureElement.findElement(By.cssSelector(LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1])));
                if (moreButton.isDisplayed()) {
                    expandLectureUsingJS(moreButton, lecture.getTitle());
                }

                // 비디오 목록 가져오기
                List<WebElement> videos = lectureElement.findElements(By.cssSelector(LectureSelector.VIDEO_ROOT.get()));

                for (WebElement videoElement : videos) {
                    Video video = new Video();
                    video.setId(videoElement.getAttribute("id"));
                    video.setTitle(videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_TITLE.get())).getText());

                    // 대기 중인 비디오인지 확인
                    WebElement waitingElement = null;
                    try {
                        waitingElement = videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_WAITING.get()));
                    } catch (NoSuchElementException e) {
                        // 대기 상태가 아닐 경우 예외를 무시
                    }
                    video.setWaiting(waitingElement != null && waitingElement.isDisplayed());

                    // 시청한 비디오인지 확인
                    WebElement watchedElement = null;
                    try {
                        watchedElement = videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_WATCHED.get()));
                    } catch (NoSuchElementException e) {
                        // 시청 상태가 아닐 경우 예외를 무시
                    }
                    video.setWatched(watchedElement != null && watchedElement.getAttribute("class").contains("on"));

                    lecture.getVideos().add(video);
                    logMessage(video.toString(), "INFO");
                }
                lectureList.add(lecture);
            }

        } catch (Exception e) {
            logMessage("강의 로딩 중 에러 발생.", "ERROR");
        }

        return lectureList;
    }

    private void loadAndPlayLectures() {
        List<Lecture> lectures = loadLectures();
        int totalVideos = 0;
        int watchedVideoCount = 0;
        int waitingVideoCount = 0;
        int notWatchedVideoCount = 0;

        for (Lecture lecture : lectures) {
            for (Video video : lecture.getVideos()) {
                totalVideos++;

                if (video.isWaiting()) {
                    waitingVideoCount++;
                    continue;
                }

                if (video.isWatched()) {
                    watchedVideoCount++;
                    continue;
                }

                notWatchedVideoCount++;
            }
        }

        // 전체 로깅 메시지를 출력
        logMessage(lectures.size() + " lectures, " + totalVideos + " videos loaded", "SUCCESS");
        logMessage("   ├ ✔ watched: " + watchedVideoCount, "INFO");
        logMessage("   ├ ◻ waiting: " + waitingVideoCount, "INFO");
        logMessage("   └ ✖ not watched: " + notWatchedVideoCount, "INFO");

        // 비디오 재생을 로깅 후에 수행
        for (Lecture lecture : lectures) {
            for (Video video : lecture.getVideos()) {
                if (video.isWaiting() || video.isWatched()) {
                    logMessage("Skipped : "+video.toString(), "INFO");
                    continue;
                }

                String title = lecture.getTitle() + " :: " + video.getTitle();
                logMessage("preparing " + title, "INFO");

                // 비디오 재생
                try {
                    playVideo(title, video, lecture);
                } catch (Exception e) {
                    logMessage("비디오 재생 실패: " + video.getTitle() + " (" + e.getMessage() + ")", "ERROR");
                }
            }
        }
    }

    private void expandLectureUsingJS(WebElement lecture, String lectureTitle) {
        try {
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("arguments[0].click();", lecture);
            Thread.sleep(1000); // 강의가 펼쳐질 시간을 잠시 대기
        } catch (InterruptedException e) {
            logMessage("강의 펼침 도중 오류 발생: " + lectureTitle, "ERROR");
            e.printStackTrace();
        }
    }

    public void playVideo(String title, Video video, Lecture lecture) {
        try {
            logMessage("playing " + title, "INFO");

            // 메인 창으로 포커스 전환
            switchToMainWindow();

            // 강의 보기 버튼 찾기 시도
            WebElement showButton = null;
            try {
                showButton = driver.findElement(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get()));

                // 강의 보기 버튼이 보이지 않을 경우 강의를 펼치는 작업 수행
                if (!showButton.isDisplayed()) {
                    // Lecture의 WebElement를 직접 사용
                    WebElement lectureElement = lecture.getLectureElement();

                    // 강의를 펼치는 작업
                    WebElement moreButton = lectureElement.findElement(By.cssSelector(LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1])));
                    if (moreButton.isDisplayed()) {
                        expandLectureUsingJS(moreButton, title);
                    }

                    // 강의 보기 버튼 다시 찾기
                    showButton = driver.findElement(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get()));
                }

                // 요소가 클릭 가능해질 때까지 대기
                showButton = wait.until(ExpectedConditions.elementToBeClickable(showButton));

                JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
                jsExecutor.executeScript("arguments[0].click();", showButton);

                logMessage("비디오 표시 버튼 클릭 완료: " + title, "INFO");
            } catch (NoSuchElementException e) {
                logMessage("비디오 표시 버튼을 찾을 수 없습니다. 다음 비디오로 이동합니다.", "ERROR");
                return; // 비디오 표시 버튼을 찾을 수 없으면 다음 비디오로 이동
            }

            // 팝업 창으로 포커스 전환
            switchToPopupWindow();

            // 프레임이 로드될 때까지 대기
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(PlayerSelector.ROOT.get()));

            // 비디오 재생 버튼 클릭
            WebElement playButton = null;
            try {
                playButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.PLAY.get())));
                playButton.click();
                logMessage("비디오 재생 버튼 클릭 완료: " + title, "INFO");
            } catch (TimeoutException e) {
                logMessage("비디오 재생 버튼을 찾을 수 없습니다. 다음 비디오로 이동합니다.", "ERROR");
                return; // 비디오 재생 버튼을 찾을 수 없으면 다음 비디오로 이동
            }

            try {
//                WebElement continueButton = driver.findElement(By.cssSelector(PlayerSelector.WATCH_CONTINUE.get()));
                WebElement continueButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.WATCH_CONTINUE.get())));
                continueButton.click();
                logMessage("이어서 보기 클릭 완료: " + title, "INFO");
            } catch (NoSuchElementException e) {
                // 이어서 보기 버튼이 없을 경우 예외를 무시하고 계속 진행
                // logMessage("이어서 보기 버튼이 없어 첫 수강으로 간주하고 계속 진행합니다.", "INFO");
            }

            // 총 재생 시간을 가져옴
            WebElement totalTimeElement = null;
            String totalTime = "";

            try {
                totalTimeElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.TOTAL_DURATION.get())));
                wait.until(ExpectedConditions.textMatches(By.cssSelector(PlayerSelector.TOTAL_DURATION.get()), Pattern.compile("\\d{2}:\\d{2}")));
                totalTime = totalTimeElement.getText();
            } catch (TimeoutException e) {
                logMessage("비디오 총 재생 시간을 가져올 수 없습니다. 비디오 재생을 계속합니다.", "ERROR");
            }

            int totalSeconds = stringToSecond(totalTime);
            logMessage("총 재생 시간: " + totalTime, "INFO");

            // 재생 완료될 때까지 대기 (예: 60% 재생 시 완료 처리)
            boolean keepPlaying = true;
            int elapsedSeconds = 0;

            stopPlayingButton.setEnabled(true); // 중지 버튼 활성화

            while (keepPlaying) {
                // TODO : 강의 종료 테스트용
                if (stopPlaying) { // 중지 버튼이 눌렸는지 확인
                    logMessage("사용자에 의해 비디오 재생이 중지되었습니다.", "INFO");
                    keepPlaying = false; // 반복 종료
                    break; // 반복 탈출
                }

                try {
                    // JavascriptExecutor 사용하여 elapsedTime 값을 강제로 가져오기
                    JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
                    String elapsedTime = (String) jsExecutor.executeScript("return arguments[0].textContent;",
                            driver.findElement(By.cssSelector(PlayerSelector.ELAPSED.get())));

                    elapsedSeconds = stringToSecond(elapsedTime);
                    double elapsedPercent = (double) elapsedSeconds / totalSeconds * 100;

                    // 진행 상황 로깅
                    logProgress(title, elapsedPercent);

                    if (elapsedPercent >= VIDEO_ELAPSE_PERCENT) {
                        keepPlaying = false;
                    }

                    Thread.sleep(500); // 잠시 대기 후 반복
                } catch (NoSuchElementException e) {
                    logMessage("elapsedTime 요소를 찾을 수 없습니다.", "ERROR");
                    keepPlaying = false; // 요소를 찾지 못하면 반복을 종료하도록 설정
                } catch (Exception e) {
                    logMessage("Unexpected error: " + e.getMessage(), "ERROR");
                    keepPlaying = false; // 예상치 못한 오류 발생 시 반복 종료
                }
            }

            // 비디오 종료 처리
            endVideo();
            logMessage("비디오 시청 완료: " + title, "INFO");

            // 중지 버튼 및 상태 비활성화
            stopPlayingButton.setEnabled(false);
            stopPlaying = false;
        } catch (Exception e) {
            logMessage("비디오 재생 에러: " + e.getMessage(), "ERROR");
        }
    }

    // 메인 창으로 포커스 전환
    private void switchToMainWindow() {
        driver.switchTo().defaultContent();
        driver.switchTo().window(driver.getWindowHandles().iterator().next());
    }

    // 팝업 창으로 포커스 전환
    private void switchToPopupWindow() {
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowHandle.equals(driver.getWindowHandles().iterator().next())) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }
        driver.switchTo().defaultContent();
    }

    private void endVideo() {
        try {
            // 팝업 창으로 포커스 전환
            switchToPopupWindow();

            // JavaScriptExecutor를 사용하여 fnStudyEnd() 함수를 실행
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("fnStudyEnd();");

            // Alert가 나타날 때까지 대기
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());

            // Alert가 나타나면 수락
            alert.accept();
        } catch (TimeoutException e) {
            logMessage("학습 종료 알림을 찾을 수 없습니다: " + e.getMessage(), "ERROR");
        } catch (Exception e) {
            logMessage("플레이어 종료 중 에러 발생 : " + e.getMessage(), "ERROR");
        }
    }

    private int stringToSecond(String time) {
        if (time == null || time.isEmpty()) {
            return 0;  // 빈 문자열이 입력된 경우 0초로 처리
        }
        String[] parts = time.split(":");
        int minutes = Integer.parseInt(parts[0]);
        int seconds = Integer.parseInt(parts[1]);
        return minutes * 60 + seconds;
    }


    private void logMessage(String message, String level) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            logTextArea.append(timestamp + " - [" + level + "] " + message + "\n");
        });
    }

    private void logProgress(String title, double progress) {
        SwingUtilities.invokeLater(() -> {
            String message = String.format("%s 수강 중... %.2f%% 완료...", title, progress);
            String level = "INFO";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

            // 로그의 마지막 줄을 찾아서 업데이트하는 예시
            int lastLineStart = logTextArea.getText().lastIndexOf('\n', logTextArea.getText().length() - 2);
            if (lastLineStart < 0) {
                lastLineStart = 0;
            } else {
                lastLineStart += 1; // 줄바꿈 이후 위치로 이동
            }

            logTextArea.replaceRange("[" + level + "] " + message, lastLineStart, logTextArea.getText().length());
        });
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KNOUAutoPlayerGUI gui = new KNOUAutoPlayerGUI();
            gui.setVisible(true);
        });
    }
}
