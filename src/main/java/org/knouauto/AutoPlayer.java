package org.knouauto;

import org.knouauto.enums.LectureSelector;
import org.knouauto.enums.PlayerSelector;
import org.knouauto.logger.ColorLogger;
import org.knouauto.model.Exam;
import org.knouauto.model.Lecture;
import org.knouauto.model.Video;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

public class AutoPlayer {

    public static final String LOGIN_URL = "https://ucampus.knou.ac.kr/ekp/user/login/retrieveULOLogin.do";
    public static final String STUDY_URL = "https://ucampus.knou.ac.kr/ekp/user/study/retrieveUMYStudy.sdo";

    public static String[] HEADLESS_OPTIONS = {"--headless", "window-size=800x600", "disable-gpu"};
    public static String[] MUTE_OPTIONS = {"--mute-audio"};

    public static final int VIDEO_ELAPSE_PERCENT = 60;
    public static final long DRIVER_WAIT_SEC = 5L;
    // 과목을 펼친 뒤 강의 보기 버튼이 보일 때까지 (목록을 늦게 불러오는 과목이 있음)
    public static final long VIEW_BUTTON_WAIT_SEC = 15L;
    // 플레이어가 영상 길이를 읽을 때까지. 넘기면 그 영상은 실패로 처리
    public static final long DURATION_WAIT_SEC = 30L;

    private WebDriver driver;
    private WebDriverWait wait;
    private JavascriptExecutor js;
    private SwingWorker<Void, String> worker;
    private String mainWindowHandle;
    private Runnable stopCallback;
    private Runnable loginSuccessCallback;
    private Runnable loginFailureCallback;
    private Consumer<List<String>> lectureSelectionCallback;

    private final ColorLogger log;

    private boolean isPlayingVideo = false;
    private boolean doOverLimit;
    private boolean skipLecturePopup;
    private List<String> savedLectures = List.of();
    // 과목 id -> 마지막으로 펼치기를 누른 시각. 펼치는 중에 다시 누르면 접히므로 기록해 둔다
    private final Map<String, Long> expandClickedAt = new HashMap<>();

    // 이번 실행 요약용
    private int watchedThisRun = 0;
    private int failedThisRun = 0;
    private boolean limitHit = false;
    private String stopReason;   // null이면 정상 종료
    private long runStartedAt;

    // 아이디/비밀번호가 틀렸거나 로그인 후 강의실로 이동하지 못한 경우
    public static class LoginFailedException extends Exception {
        public LoginFailedException(String message) {
            super(message);
        }
    }

    public AutoPlayer(ColorLogger log) {
        this.log = log;
    }

    public void setStopCallback (Runnable stopCallback) {
        this.stopCallback = stopCallback;
    }

    public void setLoginSuccessCallback(Runnable loginSuccessCallback) {
        this.loginSuccessCallback = loginSuccessCallback;
    }

    // 로그인 실패(아이디/비밀번호 불일치 등) 시 호출
    public void setLoginFailureCallback(Runnable loginFailureCallback) {
        this.loginFailureCallback = loginFailureCallback;
    }

    // 과목 선택 팝업에서 OK로 확정한 과목 제목 목록을 전달
    public void setLectureSelectionCallback(Consumer<List<String>> lectureSelectionCallback) {
        this.lectureSelectionCallback = lectureSelectionCallback;
    }

    /**
     * @param skipLecturePopup 저장된 과목 선택이 있으면 팝업 없이 바로 시작
     * @param savedLectures    이전에 선택한 과목 제목 목록 (팝업 기본 체크 상태)
     */
    public void start(String userId, String userPassword, boolean enableHeadless, boolean muteAudio, boolean overLimit,
                      boolean skipLecturePopup, List<String> savedLectures) {
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    watchedThisRun = 0;
                    failedThisRun = 0;
                    limitHit = false;
                    stopReason = null;
                    expandClickedAt.clear();
                    runStartedAt = System.currentTimeMillis();
                    doOverLimit = overLimit;
                    AutoPlayer.this.skipLecturePopup = skipLecturePopup;
                    AutoPlayer.this.savedLectures = savedLectures == null ? List.of() : List.copyOf(savedLectures);

                    // WebDriver 초기화 및 옵션 설정
                    log.status("브라우저 준비 중");
                    initializeDriver(enableHeadless, muteAudio);

                    // 로그인 및 강의 실행
                    startLearning(userId, userPassword);
                } catch (InterruptedException e) {
                    stopReason = "중지했습니다";
                } catch (LoginFailedException e) {
                    log.error(e.getMessage());
                    stopReason = "로그인하지 못해 종료했습니다";
                    if (loginFailureCallback != null) {
                        SwingUtilities.invokeLater(loginFailureCallback);
                    }
                } catch (UnhandledAlertException e) {
                    String alertText = e.getAlertText();
                    log.error("사이트 알림: " + (alertText == null ? e.getMessage() : alertText));
                    stopReason = "오류로 종료했습니다";
                } catch (Exception e) {
                    if (isCancelled()) {
                        stopReason = "중지했습니다";
                    } else {
                        log.error("오류: " + e.getMessage());
                        stopReason = "오류로 종료했습니다";
                    }
                } finally {
                    cleanup();
                    logRunSummary(isCancelled());

                    // 버튼 상태를 초기화하는 작업을 finally 블록에서 수행
                    if (stopCallback != null) {
                        SwingUtilities.invokeLater(stopCallback);
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                chunks.forEach(log::system);
            }

            @Override
            protected void done() {
                cleanup();
            }
        };
        worker.execute();
    }

    // WebDriver 초기화
    private void initializeDriver(boolean enableHeadless, boolean muteAudio) {
        ChromeOptions options = new ChromeOptions();
        if (enableHeadless) options.addArguments(HEADLESS_OPTIONS);
        if (muteAudio) options.addArguments(MUTE_OPTIONS);
        driver = new ChromeDriver(options);
        js = (JavascriptExecutor) driver;
        wait = new WebDriverWait(driver, Duration.ofSeconds(DRIVER_WAIT_SEC));
        // Main Window 지정
        mainWindowHandle = driver.getWindowHandle();
    }

    // 로그인 및 강의 실행
    private void startLearning(String userId, String userPassword) throws Exception {
        log.status("로그인 중");
        login(userId, userPassword);
        log.info("로그인 완료");
        if (loginSuccessCallback != null) {
            SwingUtilities.invokeLater(loginSuccessCallback);
        }
        loadAndPlayLectures();
    }

    // SwingWorker의 작업 완료 / 예외로 인한 작업 종료시 호출
    private void cleanup() {
        if (isPlayingVideo) {
            try {
                endVideo(); // 비디오 재생 중이면 종료 처리
            } catch (Exception e) {
                log.warn("종료하면서 학습 종료 처리를 하지 못했습니다: " + e.getMessage());
            }
        }

        if (driver != null) {
            driver.quit();
            driver = null;
        }
        isPlayingVideo = false;
    }

    public void stop() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true); // 작업 취소
        }
    }

    private void login(String userId, String userPassword) throws Exception {
        driver.get(LOGIN_URL);

        // 로그인 페이지 개편(2026-10): name=username 은 hidden 필드가 되었고,
        // 신규 '아이디' 탭은 2차 인증 팝업이 뜰 수 있으므로 (구)로그인 탭으로 로그인한다.
        WebElement legacyRadio = driver.findElement(By.cssSelector("input[name='user_type'][value='LEGACY']"));
        if (!legacyRadio.isSelected()) {
            js.executeScript("arguments[0].click();", legacyRadio);
        }

        WebElement idElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username_legacy")));
        idElement.sendKeys(userId);
        WebElement passwordElement = driver.findElement(By.id("password_legacy"));
        passwordElement.sendKeys(userPassword);
        passwordElement.sendKeys(Keys.RETURN);

        // 로그인 실패는 알림 문구와 무관하게 LoginFailedException으로 통일
        try {
            wait.until(ExpectedConditions.urlToBe(STUDY_URL));
        } catch (UnhandledAlertException e) {
            String alertText = e.getAlertText();
            handleAlertAccept(e);
            throw new LoginFailedException("로그인 실패: " + (alertText == null ? "알림이 발생했습니다." : alertText));
        } catch (TimeoutException e) {
            String currentUrl;
            try {
                currentUrl = driver.getCurrentUrl();
            } catch (UnhandledAlertException alertException) {
                String alertText = alertException.getAlertText();
                handleAlertAccept(alertException);
                throw new LoginFailedException("로그인 실패: " + (alertText == null ? "알림이 발생했습니다." : alertText));
            }
            throw new LoginFailedException("로그인 실패: 로그인 후 강의실 페이지로 이동하지 않았습니다. (현재 URL: " + currentUrl + ")");
        }
    }

    // 강의 로딩 메서드
    private List<Lecture> loadLectures() {
        List<Lecture> lectureList = new ArrayList<>();
        int totalVideos = 0;
        int watchedVideoCount = 0;
        int waitingVideoCount = 0;
        int notWatchedVideoCount = 0;

        try {
            log.status("강의 목록 불러오는 중");

            // 강의 목록을 가져오기
            List<WebElement> lectures = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(LectureSelector.ROOT.get())));

            // 수강할 과목 결정: 저장된 선택으로 바로 시작하거나 팝업에서 선택받기
            List<String> selectedLectures = resolveLectureSelection(lectures);

            for (WebElement lectureElement : lectures) {
                Lecture lecture = new Lecture();
                lecture.setId(lectureElement.getAttribute("id"));
                lecture.setTitle(lectureElement.findElement(By.cssSelector(LectureSelector.TITLE.get())).getText());
                lecture.setLectureElement(lectureElement);  // WebElement 저장

                if (!selectedLectures.contains(lecture.getTitle().trim())) {
                    continue;
                }


                // 강의를 펼치는 코드 추가 (JavaScript 사용)
                expandLecture(lecture);

                // 비디오 목록 가져오기
                List<WebElement> videos = lectureElement.findElements(By.cssSelector(LectureSelector.VIDEO_ROOT.get()));

                for (WebElement videoElement : videos) {
                    totalVideos++;

                    Video video = new Video();
                    video.setId(videoElement.getAttribute("id"));
                    // 펼치는 중이라 아직 안 보이면 getText()가 빈 문자열이므로 textContent로도 읽는다
                    String videoTitle = textOf(videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_TITLE.get())));
                    video.setTitle(videoTitle.isEmpty() ? "영상 " + (lecture.getVideos().size() + 1) : videoTitle);

                    // 대기 중인 비디오인지 확인
                    boolean isWaiting = false;
                    try {
                        WebElement waitingElement = videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_WAITING.get()));
                        isWaiting = waitingElement != null && waitingElement.isDisplayed();
                    } catch (NoSuchElementException e) {
                        // 대기 상태가 아닐 경우 예외를 무시
                    }
                    video.setWaiting(isWaiting);

                    // 시청한 비디오인지 확인
                    boolean isWatched = false;
                    try {
                        WebElement watchedElement = videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_WATCHED.get()));
                        isWatched = watchedElement != null && watchedElement.getAttribute("class").contains("on");
                    } catch (NoSuchElementException e) {
                        // 시청 상태가 아닐 경우 예외를 무시
                    }
                    video.setWatched(isWatched);

                    // 연습문제 확인
                    boolean isExercise = false;
                    try {
                        WebElement exerciseElement = videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_EXERCISE.get()));
                        isExercise = exerciseElement != null && exerciseElement.getAttribute("class").contains("on");
                    } catch (NoSuchElementException e) {
                        // 연습문제 상태가 아닐 경우 예외를 무시
                    }
                    video.setExercise(isExercise);

                    // 비디오 상태에 따라 카운트 증가
                    if (isWaiting) {
                        waitingVideoCount++;
                    } else if (isWatched) {
                        watchedVideoCount++;
                    } else {
                        notWatchedVideoCount++;
                    }

                    lecture.getVideos().add(video);
                }
                lectureList.add(lecture);

                // 과목별 한 줄 요약
                long remaining = lecture.getVideos().stream().filter(v -> !v.isWaiting() && !v.isWatched()).count();
                long waiting = lecture.getVideos().stream().filter(Video::isWaiting).count();
                String line = lecture.getTitle() + " · 영상 " + lecture.getVideos().size() + "개 중 " + remaining + "개 남음";
                if (waiting > 0) {
                    line += " (대기 " + waiting + "개)";
                }
                log.info(line);
            }

            if (lectureList.size() > 1) {
                log.info("볼 영상 총 " + notWatchedVideoCount + "개");
            }
        } catch (Exception e) {
            log.error("강의 목록을 불러오지 못했습니다: " + e.getMessage());
            stopReason = "오류로 종료했습니다";
        }

        return lectureList;
    }

    // 팝업 생략 옵션이 켜져 있고 저장된 선택이 현재 과목 목록과 하나라도 맞으면 팝업 없이 진행
    private List<String> resolveLectureSelection(List<WebElement> lectures) throws Exception {
        List<String> lectureTitles = new ArrayList<>();
        for (WebElement lectureElement : lectures) {
            lectureTitles.add(lectureElement.findElement(By.cssSelector(LectureSelector.TITLE.get())).getText().trim());
        }

        if (skipLecturePopup && !savedLectures.isEmpty()) {
            List<String> matched = new ArrayList<>();
            for (String saved : savedLectures) {
                if (lectureTitles.contains(saved)) {
                    matched.add(saved);
                }
            }
            if (!matched.isEmpty()) {
                log.info("과목 선택 (저장된 선택): " + String.join(", ", matched));
                return matched;
            }
            log.warn("저장된 과목이 목록에 없어 다시 선택합니다.");
        }

        List<String> selected = showLectureSelectionPopup(lectureTitles, savedLectures);
        if (selected != null) {
            if (lectureSelectionCallback != null) {
                List<String> confirmed = List.copyOf(selected);
                SwingUtilities.invokeLater(() -> lectureSelectionCallback.accept(confirmed));
            }
            if (selected.isEmpty()) {
                log.warn("선택한 과목이 없습니다.");
                stopReason = "선택한 과목이 없어 종료했습니다";
            } else {
                log.info("과목 선택: " + String.join(", ", selected));
            }
            return selected;
        }
        log.info("과목 선택을 취소했습니다.");
        stopReason = "과목 선택을 취소해 종료했습니다";
        return List.of();
    }


    private void loadAndPlayLectures() {
        List<Lecture> lectures = loadLectures();

        lectureLoop:
        for (Lecture lecture : lectures) {
            videoLoop:
            for (Video video : lecture.getVideos()) {
                if (worker.isCancelled()) {
                    return; // 작업이 취소되었으면 즉시 종료
                }

                if (video.isWaiting() || video.isWatched()) {
                    continue;
                }

                String title = lecture.getTitle() + " :: " + video.getTitle();
                String label = lecture.getTitle() + " · " + video.getTitle();
                long videoStartedAt = System.currentTimeMillis();
                List<WebElement> playerFrames;

                // 강의 보기 → 재생 창 → 볼 플레이어 찾기
                try {
                    if (worker.isCancelled() || Thread.currentThread().isInterrupted()) {
                        return; // 작업이 취소되었으면 즉시 종료
                    }

                    log.status("재생 준비 중 · " + label);
                    log.info("▶ " + label);

                    // 메인 창으로 포커스 전환
                    switchToMainWindow();

                    clickViewButton(lecture, video);

                    // 팝업 창으로 포커스 전환
                    switchToPopupWindow();

                    playerFrames = findPlayerFrames();
                    // 한도 초과 같은 알림은 이 단계에서 드러나므로 첫 플레이어에 한 번 들어가 본다
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(playerFrames.get(0)));
                } catch (UnhandledAlertException e) {
                    String alertMsg = Objects.toString(e.getAlertText(), "");
                    handleAlertAccept(e);

                    if (alertMsg.contains("초과")) {
                        // 일일 수강 한도 초과
                        limitHit = true;
                        if (doOverLimit) {
                            log.warn("일일 수강 한도에 도달해 다음 과목으로 넘어갑니다.");
                            break videoLoop;
                        } else {
                            log.warn("일일 수강 한도에 도달했습니다.");
                            break lectureLoop;
                        }
                    } else if (alertMsg.contains("진도율")) {
                        // 진도율 체크되지 않음
                        log.warn("진도율이 체크되지 않고 있다는 알림이 떴습니다.");
                    }

                    // 알림을 닫았으니 플레이어를 다시 찾아 이어서 진행
                    try {
                        switchToPopupWindow();
                        playerFrames = findPlayerFrames();
                    } catch (Exception ex) {
                        log.error("× " + label + " · 열지 못했습니다: " + ex.getMessage());
                        failedThisRun++;
                        continue;
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    log.error("× " + label + " · 열지 못했습니다: " + e.getMessage());
                    failedThisRun++;
                    continue;
                }

                try {
                    if (playAllPlayers(video, title, label, playerFrames) == 0) {
                        failedThisRun++;
                        closeFailedPopup();
                        continue;
                    }
                    boolean ended = endVideo();
                    watchedThisRun++;
                    String done = "✓ " + label + " · " + formatDuration(System.currentTimeMillis() - videoStartedAt);
                    if (!ended) {
                        done += " (학습 종료 처리는 실패)";
                    }
                    log.success(done);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /**
     * 재생 창에서 볼 플레이어 프레임 목록 (화면 순서).
     * 한 화면에 플레이어가 여러 개면 '오리엔테이션' 상자에 든 것만 빼고 모두 차례로 본다.
     * 오리엔테이션뿐이면 마지막 것을 본다.
     */
    private List<WebElement> findPlayerFrames() throws InterruptedException {
        By framesSelector = By.cssSelector(PlayerSelector.FRAMES.get());
        List<WebElement> frames;
        try {
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(framesSelector));
            // 플레이어를 하나씩 붙이는 화면이 있어 잠깐 기다렸다가 다시 센다
            Thread.sleep(Duration.ofSeconds(1L));
            frames = driver.findElements(framesSelector);
        } catch (TimeoutException e) {
            log.warn("영상 화면을 찾지 못해 기본 화면으로 다시 시도합니다.");
            return List.of(wait.until(ExpectedConditions.presenceOfElementLocated(By.id(PlayerSelector.ROOT.get()))));
        }
        if (frames.size() <= 1) {
            return frames.isEmpty()
                    ? List.of(driver.findElement(By.id(PlayerSelector.ROOT.get())))
                    : frames;
        }

        List<WebElement> targets = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        for (WebElement frame : frames) {
            String boxTitle = contentBoxTitle(frame);
            boolean orientation = boxTitle.contains("오리엔테이션");
            if (!orientation) {
                targets.add(frame);
            }
            detail.append(" [").append(frame.getAttribute("id")).append(": ")
                    .append(boxTitle.isEmpty() ? "제목 없음" : boxTitle)
                    .append(orientation ? ", 건너뜀" : "").append("]");
        }
        if (targets.isEmpty()) {
            targets.add(frames.get(frames.size() - 1));
        }

        int skipped = frames.size() - targets.size();
        log.info("    플레이어 " + frames.size() + "개"
                + (skipped > 0 ? " 중 " + targets.size() + "개 재생 (오리엔테이션 제외)" : " · 차례로 재생"));
        System.err.println("플레이어 프레임:" + detail);
        return targets;
    }

    // 플레이어 프레임을 감싼 상자의 제목. 상자가 없으면 빈 문자열
    private String contentBoxTitle(WebElement frame) {
        try {
            WebElement contentBox = frame.findElement(By.xpath(PlayerSelector.CONTENT_BOX.get()));
            return textOf(contentBox.findElement(By.cssSelector(PlayerSelector.CONTENT_BOX_TITLE.get())));
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    /**
     * 플레이어를 차례로 재생하며 기준 진행률까지 지켜본다.
     * 플레이어가 여러 개일 때 재생 버튼이 끝내 안 보이는 것(숨은 프레임 등)은 건너뛴다.
     *
     * @return 기준 진행률까지 본 플레이어 수. 0이면 이 영상은 실패
     */
    private int playAllPlayers(Video video, String title, String label, List<WebElement> playerFrames)
            throws InterruptedException {
        int count = playerFrames.size();
        int watched = 0;
        // 연습 문제를 통과하지 못한 경우에만, 첫 플레이어 재생을 시작한 뒤 한 번 푼다
        boolean exerciseDone = video.isExercise();

        for (int i = 0; i < count; i++) {
            WebElement frame = playerFrames.get(i);
            String player = count > 1 ? "플레이어 " + (i + 1) + "/" + count : null;

            if (!startPlayer(frame)) {
                if (player != null) {
                    log.warn("    " + player + " · 재생 버튼이 없어 건너뜁니다.");
                    continue;
                }
                log.error("× " + label + " · 재생 버튼을 찾지 못했습니다.");
                return 0;
            }

            if (!exerciseDone) {
                exerciseDone = true;
                solveExercises(label);
                // 연습 문제 처리 후 지금 플레이어로 돌아온다 (항상 첫 번째 프레임으로 가면 다른 플레이어를 보게 됨)
                try {
                    switchToPopupWindow();
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frame));
                } catch (TimeoutException e) {
                    log.error("× " + label + " · 연습 문제 뒤 영상 화면으로 돌아가지 못했습니다.");
                    return 0;
                }
            }

            String playerTitle = player == null ? title : title + " (" + player + ")";
            if (!watchingVideo(playerTitle, frame)) {
                log.error("× " + label + (player == null ? "" : " · " + player) + " · 영상 길이를 읽지 못했습니다.");
                return 0;
            }
            watched++;

            if (i < count - 1) {
                pausePlayer();
            }
        }

        if (watched == 0) {
            log.error("× " + label + " · 재생할 수 있는 플레이어가 없습니다.");
        }
        return watched;
    }

    // 플레이어 프레임으로 들어가 재생을 시작한다 (이어서 보기가 뜨면 이어서). 재생 버튼이 안 보이면 false
    private boolean startPlayer(WebElement frame) throws InterruptedException {
        try {
            switchToPopupWindow();
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frame));
        } catch (TimeoutException e) {
            return false;
        }

        Thread.sleep(Duration.ofSeconds(1L));

        // 비디오 재생 버튼 클릭
        try {
            WebElement playButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.PLAY.get())));
            playButton.click();
        } catch (Exception e) {
            return false;
        }

        try {
            WebElement continueButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.WATCH_CONTINUE.get())));
            continueButton.click();
        } catch (NoSuchElementException | TimeoutException e) {
            // 이어서 보기 버튼이 없으면 처음부터 재생
        }
        return true;
    }

    // 재생 창의 연습 문제 풀이. 연습 문제가 없으면 그냥 넘어간다. 끝나면 재생 창 최상위 문서에 있다
    private void solveExercises(String label) {
        log.status("연습 문제 확인 중 · " + label);
        // 연습문제 요소를 찾기 위한 컨텍스트 전환
        driver.switchTo().defaultContent();

        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form[id^='frm_']")));
            List<WebElement> examForms = driver.findElements(By.cssSelector("form[id^='frm_']"));

            for (WebElement examForm : examForms) {
                Exam exam = new Exam(examForm, js);
                solveExam(exam);  // 각 문제를 해결
            }

            log.info("    연습문제 제출 · " + examForms.size() + "문항");
        } catch (TimeoutException te) {
            // 연습 문제가 없는 영상
        } catch (Exception e) {
            log.error("    연습문제를 처리하지 못했습니다: " + e.getMessage());
        }
    }

    // 다음 플레이어로 넘어가기 전에 지금 플레이어를 멈춘다 (소리가 겹치지 않도록). 실패해도 그대로 진행
    private void pausePlayer() {
        try {
            js.executeScript(
                    "if (window.jwplayer) { var p = jwplayer();" +
                    " if (p.getState && p.getState() === 'playing') { p.pause(); } }");
        } catch (Exception ignored) {
        }
    }

    // 실패한 영상의 재생 창 정리: 학습 종료를 시도하고, 안 되면 창을 닫는다 (다음 영상이 남은 창을 잡지 않도록)
    private void closeFailedPopup() {
        isPlayingVideo = false;
        try {
            switchToPopupWindow();
            if (driver.getWindowHandle().equals(mainWindowHandle)) {
                return;
            }
            js.executeScript("if (typeof fnStudyEnd === 'function') { fnStudyEnd(); }");
            wait.until(ExpectedConditions.alertIsPresent()).accept();
        } catch (Exception e) {
            try {
                if (!driver.getWindowHandle().equals(mainWindowHandle)) {
                    driver.close();
                }
            } catch (Exception ignored) {
            }
        } finally {
            try {
                switchToMainWindow();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 현재 플레이어를 기준 진행률(VIDEO_ELAPSE_PERCENT)까지 지켜본다.
     *
     * @return 기준까지 봤으면 true, 영상 길이를 끝내 읽지 못했으면 false
     */
    private boolean watchingVideo(String title, WebElement currentFrame) throws InterruptedException {
        isPlayingVideo = true;
        long startedAt = System.currentTimeMillis();

        while (true) {
            // 영상 길이는 재생이 시작된 뒤에야 채워질 수 있어 매번 다시 읽는다.
            // 0초인 채로 계산하면 진행률이 무한대가 되어 바로 '완료'로 잘못 처리된다.
            String totalTime = playerText(PlayerSelector.TOTAL_DURATION);
            String elapsedTime = playerText(PlayerSelector.ELAPSED);
            int totalSeconds = stringToSecond(totalTime);

            if (totalSeconds > 0) {
                double elapsedPercent = (double) stringToSecond(elapsedTime) / totalSeconds * 100;
                log.progress(title, elapsedTime, totalTime, elapsedPercent);
                if (elapsedPercent >= VIDEO_ELAPSE_PERCENT) {
                    // 학습 종료(endVideo)까지는 재생 중으로 둔다
                    return true;
                }
            } else if (System.currentTimeMillis() - startedAt > Duration.ofSeconds(DURATION_WAIT_SEC).toMillis()) {
                isPlayingVideo = false;
                return false;
            }

            // 돌발 퀴즈 확인 및 처리
            if (handleSurpriseQuiz(currentFrame)) {
                // 퀴즈 처리 후 영상이 일시정지 상태일 수 있으므로 다시 재생
                try {
                    WebElement playButton = driver.findElement(By.cssSelector(PlayerSelector.PLAY.get()));
                    if (playButton.isDisplayed()) {
                        playButton.click();
                    }
                } catch (Exception e) {
                    // 재생 버튼이 없으면 그대로 진행
                }
            }

            Thread.sleep(500);
        }
    }

    // 현재 플레이어 프레임 안 요소의 글자 (재생 시간 등). 아직 없으면 빈 문자열
    private String playerText(PlayerSelector selector) {
        try {
            Object text = js.executeScript("return arguments[0].textContent;",
                    driver.findElement(By.cssSelector(selector.get())));
            return Objects.toString(text, "").trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    // 화면에 안 보이는 요소는 getText()가 빈 문자열이라 textContent로 한 번 더 읽는다
    private static String textOf(WebElement element) {
        String text = element.getText().trim();
        if (text.isEmpty()) {
            text = Objects.toString(element.getDomProperty("textContent"), "").replaceAll("\\s+", " ").trim();
        }
        return text;
    }

    // 사이트에 학습 종료를 알린다. 성공하면 true.
    private boolean endVideo() {
        try {
            // Cleanup시 endVideo 중복호출 되지 않도록
            isPlayingVideo = false;

            switchToPopupWindow();

            js.executeScript("fnStudyEnd();");

            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
            return true;
        } catch (TimeoutException e) {
            log.warn("학습 종료 확인 창이 뜨지 않았습니다.");
        } catch (Exception e) {
            log.warn("학습 종료 처리를 하지 못했습니다: " + e.getMessage());
        }
        return false;
    }

    // 실행이 끝날 때 한 줄 요약
    private void logRunSummary(boolean cancelled) {
        String reason;
        if (cancelled) {
            reason = "중지했습니다";
        } else if (stopReason != null) {
            reason = stopReason;
        } else if (limitHit) {
            reason = "일일 수강 한도에 도달해 종료했습니다";
        } else if (watchedThisRun == 0 && failedThisRun == 0) {
            reason = "볼 영상이 없어 종료했습니다";
        } else {
            reason = "선택한 과목의 영상을 모두 시청했습니다";
        }

        StringBuilder summary = new StringBuilder(reason);
        if (watchedThisRun > 0 || failedThisRun > 0) {
            summary.append(" · 시청 ").append(watchedThisRun).append("개");
            if (failedThisRun > 0) {
                summary.append(", 실패 ").append(failedThisRun).append("개");
            }
            summary.append(" · ").append(formatDuration(System.currentTimeMillis() - runStartedAt));
        }

        boolean finishedAll = !cancelled && stopReason == null && !limitHit && watchedThisRun > 0 && failedThisRun == 0;
        if (finishedAll) {
            log.success(summary.toString());
        } else {
            log.info(summary.toString());
        }
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "시간 " + minutes + "분";
        }
        if (minutes > 0) {
            return minutes + "분";
        }
        return seconds + "초";
    }

    private int stringToSecond(String time) {
        if (time == null || time.isEmpty()) {
            return 0;
        }

        String[] parts = time.split(":");
        int[] units = {3600, 60, 1};
        int seconds = 0;

        // 뒤에서부터 시간을 초 단위로 변환하여 더함
        try {
            for (int i = 0; i < parts.length; i++) {
                seconds += Integer.parseInt(parts[i].trim()) * units[units.length - parts.length + i];
            }
        } catch (RuntimeException e) {
            // 아직 시간이 안 채워진 표시("--:--" 등)는 0초로 본다
            return 0;
        }

        return seconds;
    }

    private void switchToMainWindow() {
        driver.switchTo().window(mainWindowHandle);
        driver.switchTo().defaultContent();
    }

    private void switchToPopupWindow() {
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowHandle.equals(mainWindowHandle)) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }
    }

    private void solveExam(Exam exam) {
        try {
            int attemptCount = 0;
            boolean isAnswerCorrect = false;

            // 최대 두 번까지 시도
            while (attemptCount < 2 && !isAnswerCorrect) {
                if (exam.isDescriptive()) {
                    isAnswerCorrect = descriptiveAnswer(exam);
                } else {
                    isAnswerCorrect = choiceAnswer(exam);
                }

                // 정답/오답인 Case의 처리
//                if (!isAnswerCorrect) {
//                    log.info("오답입니다. 다시 시도합니다.");
//                } else {
//                    log.info("정답입니다. 다음 문제로 이동합니다.");
//                }

                attemptCount++;
            }

//            if (!isAnswerCorrect) {
//                log.warn("최대 시도 횟수를 초과했습니다. 다음 문제로 넘어갑니다.");
//            }
        } catch (Exception e) {
            log.error("    연습문제 풀이 중 오류: " + e.getMessage());
        }
    }


    private boolean descriptiveAnswer(Exam exam) {
        try {
            List<WebElement> answerFields = exam.getExamForm().findElements(By.cssSelector(".answerTxt"));
            for (WebElement field : answerFields) {
                field.sendKeys("잘모루겠습니다교수님");
            }
            exam.submitAnswer();  // Exam 클래스의 메서드를 사용하여 제출

            return checkResult(exam);
        } catch (UnhandledAlertException e) {
            handleAlertAccept(e);
            return false;
        } catch (Exception e) {
            log.error("    서술형 문제를 처리하지 못했습니다.");
            return false;
        }
    }

    private boolean choiceAnswer(Exam exam) {
        try {
            if (!exam.getAnswerChoices().isEmpty()) {
                int randomIndex = new Random().nextInt(exam.getAnswerChoices().size());
                exam.selectAnswer(randomIndex);  // Exam 클래스의 메서드를 사용하여 선택
            }
            exam.submitAnswer();  // Exam 클래스의 메서드를 사용하여 제출

            return checkResult(exam);
        } catch (UnhandledAlertException e) {
            handleAlertAccept(e);
            return false;
        } catch (Exception e) {
            log.error("    객관식 문제를 처리하지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    private boolean checkResult(Exam exam) throws UnhandledAlertException {
        try {
            WebElement resultCnt = exam.getExamForm().findElement(By.id("resultCnt"));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id(resultCnt.getAttribute("id"))));
            String resultValue = resultCnt.getAttribute("value");

            return "1".equals(resultValue);
        } catch (UnhandledAlertException e) {
            // 문제 풀이 메서드에서 Alert을 처리하도록 Throw
            throw e;
        } catch (NoSuchElementException e) {
            // #resultCnt를 찾지 못하는 경우는 새로운 유형의 퀴즈로 간주
            if (e.getMessage().contains("resultCnt")) {
                return true; // 결과 필드가 없는 새 유형의 문제는 통과로 간주
            }
            // 다른 요소 문제일 경우 에러를 다시 던짐
            throw e;
        } catch (Exception e) {
            log.error("    정답을 확인하지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    private void handleAlertAccept(UnhandledAlertException e) {
        // log.error("알림이 발생했습니다: " + e.getAlertText());
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            if (alert != null) {
                alert.accept();
            }
        } catch (TimeoutException te) {
            // log.warn("Alert 대기 시간 초과: 알림이 존재하지 않습니다.");
            // 이미 처리된 Alert이므로 PASS
        } catch (Exception ex) {
            log.warn("알림 창을 닫지 못했습니다.");
        }
    }

    private boolean handleSurpriseQuiz(WebElement originalFrame) {
        boolean isQuizVisible;
        try {
            // 현재 iframe 내에서 JavaScript를 실행하여 상위 문서의 퀴즈 모달을 '읽기 전용'으로 확인 (포커스 이동 없음)
            // '.modal' div에 'active' 클래스가 있는지 확인하는 방식으로 변경
            isQuizVisible = (boolean) js.executeScript(
                "var topDoc = window.top.document;" +
                "var modals = topDoc.querySelectorAll(arguments[0]);" +
                "var isVisible = false;" +
                "for (var i = 0; i < modals.length; i++) {" +
                "    if (modals[i].classList.contains('active')) {" +
                "        isVisible = true;" +
                "        break;" +
                "    }" +
                "}" +
                "return isVisible;",
                PlayerSelector.SURPRISE_QUIZ_MODAL.get()
            );
        } catch (Exception e) {
            return false; // 감지에 실패하면 이번 주기는 건너뜀 (0.5초마다 재시도)
        }

        // --- 퀴즈가 실제로 보일 때만 아래 로직 실행 ---
        if (isQuizVisible) {
            try {
                // 컨텍스트 전환 (이제 진짜 퀴즈를 풀어야 하므로)
                driver.switchTo().defaultContent();

                // 보이는 모달 찾기
                WebElement visibleModal = null;
                List<WebElement> modals = driver.findElements(By.cssSelector(PlayerSelector.SURPRISE_QUIZ_MODAL.get() + ".active"));
                for (WebElement modal : modals) {
                    if (modal.isDisplayed()) {
                        visibleModal = modal;
                        break;
                    }
                }


                if (visibleModal != null) {

                    WebElement quizFormElement = visibleModal.findElement(By.cssSelector(PlayerSelector.SURPRISE_QUIZ_FORM.get()));
                    Exam quiz = new Exam(quizFormElement, js);

                    // 1. 임의의 보기 선택
                    if (!quiz.getAnswerChoices().isEmpty()) {
                        int randomIndex = new Random().nextInt(quiz.getAnswerChoices().size());
                        quiz.selectAnswer(randomIndex);
                    }

                    // 2. 확인 버튼 클릭
                    quiz.submitAnswer();

                    // 3. "학습 계속하기" 버튼 클릭
                    WebElement closeButton = wait.until(ExpectedConditions.visibilityOf(visibleModal.findElement(By.cssSelector(PlayerSelector.SURPRISE_QUIZ_CLOSE_BUTTON.get()))));
                    js.executeScript("arguments[0].click();", closeButton);

                    // 퀴즈 창이 완전히 사라질 때까지 대기
                    wait.until(ExpectedConditions.invisibilityOf(visibleModal));
                    log.info("    돌발 퀴즈 응답");
                }
            } catch (Exception e) {
                log.error("    돌발 퀴즈를 처리하지 못했습니다: " + e.getMessage());
            } finally {
                // 로직이 끝나면 항상 플레이어 프레임으로 복귀
                try {
                    switchToPopupWindow();
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(originalFrame));
                } catch (Exception e) {
                    log.warn("영상 화면으로 돌아가지 못했습니다.");
                }
            }
            return true;
        }

        return false; // 퀴즈가 보이지 않으면 아무것도 하지 않고 종료
    }

    /**
     * 과목 선택 창. 저장된 선택(defaultSelected)에 있는 과목은 기본 체크 상태로 띄운다.
     *
     * @return 시작으로 확정한 과목 제목 목록, 취소하면 null
     */
    private List<String> showLectureSelectionPopup(List<String> lectureTitles, List<String> defaultSelected) throws Exception {
        // Swing UI는 반드시 EDT에서: invokeAndWait + FutureTask로 결과 회수
        java.util.concurrent.FutureTask<List<String>> task = new java.util.concurrent.FutureTask<>(() -> {
            Window owner = findMainWindow();
            JDialog dialog = new JDialog(owner, "수강할 과목 선택", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            List<JCheckBox> checkBoxes = new ArrayList<>();
            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            Color listBackground = UIManager.getColor("List.background");
            if (listBackground != null) {
                listPanel.setBackground(listBackground);
            }
            for (String title : lectureTitles) {
                JCheckBox checkBox = new JCheckBox(title);
                checkBox.setOpaque(false);
                checkBox.setSelected(defaultSelected.contains(title));
                checkBoxes.add(checkBox);
                listPanel.add(checkBox);
            }
            JScrollPane scrollPane = new JScrollPane(listPanel);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);

            // 전체 선택: 개별 체크 상태에 따라 같이 맞춰 준다
            JCheckBox selectAllCheckBox = new JCheckBox("전체 선택");
            Runnable syncSelectAll = () -> {
                boolean all = !checkBoxes.isEmpty();
                for (JCheckBox checkBox : checkBoxes) {
                    all &= checkBox.isSelected();
                }
                selectAllCheckBox.setSelected(all);
            };
            selectAllCheckBox.addActionListener(e -> {
                boolean selected = selectAllCheckBox.isSelected();
                for (JCheckBox checkBox : checkBoxes) {
                    checkBox.setSelected(selected);
                }
            });
            for (JCheckBox checkBox : checkBoxes) {
                checkBox.addActionListener(e -> syncSelectAll.run());
            }
            syncSelectAll.run();

            boolean[] confirmed = {false};
            JButton startButton = new JButton("시작");
            JButton cancelButton = new JButton("취소");
            startButton.addActionListener(e -> {
                confirmed[0] = true;
                dialog.dispose();
            });
            cancelButton.addActionListener(e -> dialog.dispose());

            // macOS는 기본 버튼이 오른쪽, Windows는 왼쪽
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
            buttonPanel.add(isMac ? cancelButton : startButton);
            buttonPanel.add(isMac ? startButton : cancelButton);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.add(selectAllCheckBox, BorderLayout.WEST);
            bottom.add(buttonPanel, BorderLayout.EAST);

            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            content.add(new JLabel("수강할 과목을 체크하세요."), BorderLayout.NORTH);
            content.add(scrollPane, BorderLayout.CENTER);
            content.add(bottom, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.getRootPane().setDefaultButton(startButton);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(420, 360);
            dialog.setMinimumSize(new java.awt.Dimension(320, 240));
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true); // 모달: 닫힐 때까지 대기

            if (!confirmed[0]) {
                return null;
            }
            List<String> selected = new ArrayList<>();
            for (JCheckBox checkBox : checkBoxes) {
                if (checkBox.isSelected()) {
                    selected.add(checkBox.getText().trim());
                }
            }
            return selected;
        });
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    // 과목 선택 창의 부모로 쓸 메인 창 (없으면 null)
    private static Window findMainWindow() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isVisible()) {
                return frame;
            }
        }
        return null;
    }

    private void clickViewButton(Lecture lecture, Video video) {
        List<WebElement> viewButtonList = driver.findElements(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get()));

        if (viewButtonList.isEmpty()) {
            // 강의보기 버튼이 존재하지 않는 경우(과목 전환 Case)
            expandLecture(lecture);
            waitForViewButtonAndClick(video);
        } else {
            WebElement viewButton = viewButtonList.get(0);
            if (!viewButton.isDisplayed()) {
                // 강의보기 버튼이 가시적이지 않을 경우 펼치기
                expandLecture(lecture);
                waitForViewButtonAndClick(video);
            } else {
                // 클릭 가능한 상태가 될 때까지 대기 후 클릭
                viewButton = wait.until(ExpectedConditions.elementToBeClickable(viewButton));
                js.executeScript("arguments[0].click();", viewButton);
            }
        }
    }

    private void expandLecture(Lecture lecture) {
        WebElement lectureElement = lecture.getLectureElement(driver);
        // 펼치기 버튼은 토글이라, 이미 펼쳐졌거나 펼치는 중인 과목을 다시 누르면 도리어 접힌다
        long sinceLastClick = System.currentTimeMillis() - expandClickedAt.getOrDefault(lecture.getId(), 0L);
        if (isExpanded(lectureElement) || sinceLastClick < Duration.ofSeconds(VIEW_BUTTON_WAIT_SEC).toMillis()) {
            return;
        }
        WebElement expandButton = lectureElement.findElement(By.cssSelector(LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1])));
        if (expandButton.isDisplayed()) {
            try {
                js.executeScript("arguments[0].click();", expandButton);
                expandClickedAt.put(lecture.getId(), System.currentTimeMillis());
            } catch (Exception e) {
                log.error("과목을 펼치지 못했습니다: " + lecture.getTitle());
            }
        }
    }

    // 과목의 영상 목록이 화면에 보이면 펼쳐진 상태
    private boolean isExpanded(WebElement lectureElement) {
        for (WebElement item : lectureElement.findElements(By.cssSelector(LectureSelector.VIDEO_ROOT.get()))) {
            if (item.isDisplayed()) {
                return true;
            }
        }
        return false;
    }

    private void waitForViewButtonAndClick(Video video) {
        By viewButtonSelector = By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get());
        WebElement viewButton;
        try {
            // 펼친 뒤 목록이 늦게 보이는 과목이 있어 기본 대기보다 길게 기다린다
            viewButton = new WebDriverWait(driver, Duration.ofSeconds(VIEW_BUTTON_WAIT_SEC))
                    .until(ExpectedConditions.visibilityOfElementLocated(viewButtonSelector));
            viewButton = wait.until(ExpectedConditions.elementToBeClickable(viewButton));
        } catch (TimeoutException e) {
            // 그래도 안 보이면 숨은 채로 누른다 (스크립트 클릭은 화면에 안 보여도 동작)
            List<WebElement> buttons = driver.findElements(viewButtonSelector);
            if (buttons.isEmpty()) {
                throw e;
            }
            viewButton = buttons.get(0);
        }
        js.executeScript("arguments[0].click();", viewButton);
    }

}