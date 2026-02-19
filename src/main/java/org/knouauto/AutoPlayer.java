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
import java.util.List;
import java.util.Random;

public class AutoPlayer {

    public static final String LOGIN_URL = "https://ucampus.knou.ac.kr/ekp/user/login/retrieveULOLogin.do";
    public static final String STUDY_URL = "https://ucampus.knou.ac.kr/ekp/user/study/retrieveUMYStudy.sdo";

    public static String[] HEADLESS_OPTIONS = {"--headless", "window-size=800x600", "disable-gpu"};
    public static String[] MUTE_OPTIONS = {"--mute-audio"};

    public static final int VIDEO_ELAPSE_PERCENT = 60;
    public static final long DRIVER_WAIT_SEC = 5L;

    private WebDriver driver;
    private WebDriverWait wait;
    private JavascriptExecutor js;
    private SwingWorker<Void, String> worker;
    private String mainWindowHandle;
    private Runnable stopCallback;

    private final ColorLogger log;

    private boolean isPlayingVideo = false;
    private boolean doOverLimit;

    public AutoPlayer(ColorLogger log) {
        this.log = log;
    }

    public void setStopCallback (Runnable stopCallback) {
        this.stopCallback = stopCallback;
    }

    public void start(String userId, String userPassword, boolean enableHeadless, boolean muteAudio, boolean overLimit) {
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    doOverLimit = overLimit;

                    // WebDriver 초기화 및 옵션 설정
                    initializeDriver(enableHeadless, muteAudio);

                    // 로그인 및 강의 실행
                    startLearning(userId, userPassword);
                } catch (InterruptedException e) {
                    publish("유저 취소: " + e.getMessage());
                } catch (UnhandledAlertException e) {
                    if (e.getAlertText().contains("로그인 정보가 올바르지 않습니다.")) {
                        publish(e.getAlertText());
                    }
                } catch (Exception e) {
                    publish("에러 발생: " + e.getMessage());
                } finally {
                    cleanup();
                    publish("중지했습니다.");

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
        log.info("로그인 중...");
        login(userId, userPassword);
        log.info("로그인 성공, 강의 로딩 ...");
        log.newLine();
        loadAndPlayLectures();
    }

    // SwingWorker의 작업 완료 / 예외로 인한 작업 종료시 호출
    private void cleanup() {
        if (isPlayingVideo) {
            try {
                endVideo(); // 비디오 재생 중이면 종료 처리
            } catch (Exception e) {
                log.error("Cleanup 중 강의 종료 실패: " + e.getMessage());
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
        driver.findElement(By.name("username")).sendKeys(userId);
        WebElement passwordElement = driver.findElement(By.name("password"));
        passwordElement.sendKeys(userPassword);
        passwordElement.sendKeys(Keys.RETURN);
        wait.until(ExpectedConditions.urlToBe(STUDY_URL));
    }

    // 강의 로딩 메서드
    private List<Lecture> loadLectures() {
        List<Lecture> lectureList = new ArrayList<>();
        int totalVideos = 0;
        int watchedVideoCount = 0;
        int waitingVideoCount = 0;
        int notWatchedVideoCount = 0;

        try {
            log.info("강의 로딩 중...");

            // 강의 목록을 가져오기
            List<WebElement> lectures = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(LectureSelector.ROOT.get())));

            // 강의 타이틀을 기준으로 체크박스를 팝업에서 선택받기
            List<String> excludedLectures = showLectureSelectionPopup(lectures);

            for (WebElement lectureElement : lectures) {
                Lecture lecture = new Lecture();
                lecture.setId(lectureElement.getAttribute("id"));
                lecture.setTitle(lectureElement.findElement(By.cssSelector(LectureSelector.TITLE.get())).getText());
                lecture.setLectureElement(lectureElement);  // WebElement 저장

                if (!excludedLectures.contains(lecture.getTitle().trim())) {
                    log.info(lecture.getTitle() + "강의를 스킵합니다.");
                    continue;
                }

                log.info(lecture.toString());

                // 강의를 펼치는 코드 추가 (JavaScript 사용)
                expandLecture(lecture);

                // 비디오 목록 가져오기
                List<WebElement> videos = lectureElement.findElements(By.cssSelector(LectureSelector.VIDEO_ROOT.get()));

                for (WebElement videoElement : videos) {
                    totalVideos++;

                    Video video = new Video();
                    video.setId(videoElement.getAttribute("id"));
                    video.setTitle(videoElement.findElement(By.cssSelector(LectureSelector.VIDEO_TITLE.get())).getText());

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
                    log.info(video.toString());
                }
                lectureList.add(lecture);
                log.newLine();
            }

            // 전체 로깅 메시지를 출력
            log.success(lectures.size() + " lectures, " + totalVideos + " videos loaded");
            log.info("   ├ ✔ watched: " + watchedVideoCount);
            log.info("   ├ ◻ waiting: " + waitingVideoCount);
            log.info("   └ ✖ not watched: " + notWatchedVideoCount);
            log.newLine();

        } catch (Exception e) {
            log.error("강의 로딩 중 에러 발생.");
        }

        return lectureList;
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
                    log.info("시청 생략(강의 대기 혹은 시청 완료) : " + video.toString());
                    continue;
                }

    String title = lecture.getTitle() + " :: " + video.getTitle();
    WebElement targetFrame = null;

    // 비디오 재생
    try {
        if (worker.isCancelled() || Thread.currentThread().isInterrupted()) {
            return; // 작업이 취소되었으면 즉시 종료
        }

        log.newLine();
        log.info("재생 시작 :: " + title);

        // 메인 창으로 포커스 전환
        switchToMainWindow();

        clickViewButton(lecture, video);

        // log.info("비디오 표시 버튼 클릭 완료: " + title);

        // 팝업 창으로 포커스 전환
        switchToPopupWindow();

        // --- 올바른 영상 프레임 선택 로직 (V3) ---
        try {
            // 1. id가 'ifrmVODPlayer_'로 시작하는 모든 iframe을 직접 찾음
            List<WebElement> playerFrames = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("iframe[id^='ifrmVODPlayer_']")));

            // 2. 프레임이 여러 개일 경우, 본 영상을 식별
            if (playerFrames.size() > 1) {
                log.info(playerFrames.size() + "개의 영상 프레임을 직접 찾았습니다. '오리엔테이션'이 아닌 본 영상을 탐색합니다.");
                for (WebElement frame : playerFrames) {
                    try {
                        // 3. 각 iframe에서 상위로 올라가 'content-box'를 찾고, 그 안의 제목을 확인
                        WebElement contentBox = frame.findElement(By.xpath("./ancestor::div[contains(@class, 'content-box')]"));
                        String boxTitle = contentBox.findElement(By.cssSelector(".content-box-title")).getText();

                        // 4. 제목에 '오리엔테이션'이 포함되지 않으면 '본 영상'으로 간주하고 선택
                        if (!boxTitle.contains("오리엔테이션")) {
                            targetFrame = frame;
                            log.info("본 영상 프레임을 찾았습니다: " + frame.getAttribute("id") + " (제목: " + boxTitle + ")");
                            break;
                        }
                    } catch (NoSuchElementException e) {
                        log.warn("프레임 " + frame.getAttribute("id") + "의 제목을 포함한 컨테이너를 찾지 못했습니다.");
                    }
                }

                // '오리엔테이션'이 아닌 영상을 명확히 찾지 못했을 경우, 마지막 프레임을 본 영상으로 간주 (fallback)
                if (targetFrame == null) {
                    log.warn("'오리엔테이션'이 아닌 영상을 명확히 찾지 못했습니다. 목록의 마지막 프레임을 본 영상으로 간주하고 시도합니다.");
                    if (!playerFrames.isEmpty()) {
                        targetFrame = playerFrames.get(playerFrames.size() - 1);
                    }
                }
            }

            // 5. 대상 프레임이 없거나(프레임이 하나였던 경우 등), 최종 선택된 프레임으로 설정
            if (targetFrame == null) {
                if (!playerFrames.isEmpty()) {
                    targetFrame = playerFrames.get(0);
                } else {
                    // CSS 셀렉터로 못찾았을 경우 ID로 최후 시도
                    targetFrame = driver.findElement(By.id(PlayerSelector.ROOT.get()));
                }
            }
            
            // 최종 선택된 프레임으로 전환
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(targetFrame));

        } catch (Exception ex) {
            log.error("영상 프레임 선택 중 복합적인 오류 발생: " + ex.getMessage() + ". 기존 방식(ifrmVODPlayer_0)으로 복구합니다.");
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id(PlayerSelector.ROOT.get())));
        }
        // --- 프레임 선택 로직 종료 ---
                } catch (UnhandledAlertException e) {
                    String alertMsg = e.getAlertText();
                    handleAlertAccept(e);

                    if (alertMsg.contains("초과")) {
                        // 일일 수강 한도 초과
                        log.warn("일일 수강 한도에 도달했습니다.");

                        if (doOverLimit) {
                            break videoLoop;
                        } else {
                            break lectureLoop;
                        }
                    } else if (alertMsg.contains("진도율")) {
                        // 진도율 체크되지 않음
                        log.warn("진도율이 체크 되지 않고 있습니다.");
                    }
                } catch (Exception e) {
                    log.error("비디오 재생 실패: " + video.getTitle() + " (" + e.getMessage() + ")");
                }

                try {
                    Thread.sleep(Duration.ofSeconds(1L));
                } catch (InterruptedException e) {
                    log.error("대기 중 취소.");
                }

                // 비디오 재생 버튼 클릭
                try {
                    WebElement playButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.PLAY.get())));
                    playButton.click();
                    // log.info("비디오 재생 버튼 클릭 완료: " + title);
                } catch (Exception e) {
                    log.error("비디오 재생 버튼을 찾지 못했습니다.");
                    continue;
                }

                try {
                    WebElement continueButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(PlayerSelector.WATCH_CONTINUE.get())));
                    continueButton.click();
                    log.info("이어서 보기 클릭 완료: " + title);
                } catch (NoSuchElementException | TimeoutException e) {
                    // 이어서 보기 버튼이 없거나 대기 실패의 경우 Pass
                }

                // 연습 문제를 통과하지 못한 경우
                if (!video.isExercise()) {
                    log.info("연습 문제를 확인합니다.");
                    // 연습문제 요소를 찾기 위한 컨텍스트 전환
                    driver.switchTo().defaultContent();

                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form[id^='frm_']")));
                        List<WebElement> examForms = driver.findElements(By.cssSelector("form[id^='frm_']"));

                        for (WebElement examForm : examForms) {
                            Exam exam = new Exam(examForm, js);
                            solveExam(exam);  // 각 문제를 해결
                        }

                        log.success(title + "의 문제 풀이를 완료했습니다.");
                    } catch (TimeoutException te) {
                        log.info("연습 문제가 없습니다.");
                    } catch (Exception e) {
                        log.error("연습문제 확인 중 오류 발생: " + e.getMessage());
                    }

                    log.newLine();

                    // 연습 문제 처리 후 다시 플레이어 프레임으로 전환
                    switchToPopupWindow();
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(PlayerSelector.ROOT.get()));
                } else {
                    log.info("기존에 연습 문제를 통과한 이력이 있습니다.");
                }

                try {
                    watchingVideo(title, targetFrame);
                    endVideo();
                } catch (InterruptedException e) {
                    log.newLine();
                    log.warn("유저 취소로 인한 중단");
                }
            }
        }
    }

    private void watchingVideo(String title, WebElement currentFrame) throws InterruptedException {
        String totalTime = (String) js.executeScript("return arguments[0].textContent;",
                driver.findElement(By.cssSelector(PlayerSelector.TOTAL_DURATION.get())));
        int totalSeconds = stringToSecond(totalTime);

        log.info("총 재생 시간: " + totalTime);

        boolean keepPlaying = true;
        int elapsedSeconds = 0;

        isPlayingVideo = true;

        while (keepPlaying) {
            String elapsedTime = (String) js.executeScript("return arguments[0].textContent;",
                    driver.findElement(By.cssSelector(PlayerSelector.ELAPSED.get())));

            elapsedSeconds = stringToSecond(elapsedTime);
            double elapsedPercent = (double) elapsedSeconds / totalSeconds * 100;

            log.logProgress(title, elapsedTime, elapsedPercent);

            if (elapsedPercent >= VIDEO_ELAPSE_PERCENT) {
                keepPlaying = false;
            }

            // 돌발 퀴즈 확인 및 처리
            if (handleSurpriseQuiz(currentFrame)) {
                // 퀴즈 처리 후 영상이 일시정지 상태일 수 있으므로 다시 재생
                try {
                    WebElement playButton = driver.findElement(By.cssSelector(PlayerSelector.PLAY.get()));
                    if(playButton.isDisplayed()) {
                        playButton.click();
                        log.info("돌발 퀴즈 처리 후 영상을 다시 재생합니다.");
                    }
                } catch (Exception e) {
                    log.warn("퀴즈 처리 후 재생 버튼을 찾지 못했지만, 계속 진행합니다.");
                }
            }

            Thread.sleep(500);
        }

        isPlayingVideo = false;

        log.newLine();
        log.info("강의 시청 완료: " + title);
    }

    private void endVideo() {
        try {
            // Cleanup시 endVideo 중복호출 되지 않도록
            isPlayingVideo = false;

            switchToPopupWindow();

            js.executeScript("fnStudyEnd();");

            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();

            log.info("학습 종료 완료");
        } catch (TimeoutException e) {
            log.error("학습 종료 알림을 찾을 수 없습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("플레이어 종료 중 에러 발생 : " + e.getMessage());
        }
    }

    private int stringToSecond(String time) {
        if (time == null || time.isEmpty()) {
            return 0;
        }

        String[] parts = time.split(":");
        int[] units = {3600, 60, 1};
        int seconds = 0;

        // 뒤에서부터 시간을 초 단위로 변환하여 더함
        for (int i = 0; i < parts.length; i++) {
            seconds += Integer.parseInt(parts[i]) * units[units.length - parts.length + i];
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
            log.error("연습문제 풀이 도중 오류 발생: " + e.getMessage());
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
            log.error("서술형 문제 처리 중 에러가 발생했습니다.");
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
            log.error("객관식 문제 처리 중 에러가 발생했습니다." + e.getMessage());
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
                log.info("'#resultCnt'를 찾을 수 없습니다. 새로운 유형의 퀴즈로 간주하고 계속합니다.");
                return true; // 성공으로 간주하고 계속 진행
            }
            // 다른 요소 문제일 경우 에러를 다시 던짐
            throw e;
        } catch (Exception e) {
            log.error("정답 확인 중 에러가 발생했습니다." + e.getMessage());
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
            log.error("Alert 처리 중 오류가 발생했습니다.");
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
            log.error("돌발 퀴즈 감지 스크립트 실행 중 오류: " + e.getMessage());
            return false; // 스크립트 에러시 아무것도 하지 않음
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
                    log.info("돌발 퀴즈가 나타났습니다.");

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
                    log.info("돌발 퀴즈를 완료했습니다.");
                }
            } catch (Exception e) {
                log.error("돌발 퀴즈 처리 중 예외 발생: " + e.getMessage());
            } finally {
                // 로직이 끝나면 항상 플레이어 프레임으로 복귀
                try {
                    switchToPopupWindow();
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(originalFrame));
                } catch (Exception e) {
                    log.error("플레이어 프레임 복귀 중 오류 발생");
                }
            }
            return true;
        }

        return false; // 퀴즈가 보이지 않으면 아무것도 하지 않고 종료
    }

    private List<String> showLectureSelectionPopup(List<WebElement> lectures) {
        List<JCheckBox> checkBoxes = new ArrayList<>();
        JPanel panel = new JPanel(new BorderLayout());

        // 전체 선택/해제 체크박스
        JCheckBox selectAllCheckBox = new JCheckBox("전체 선택/해제");
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(selectAllCheckBox);

        // 강의 목록 체크박스 패널
        JPanel checkBoxPanel = new JPanel(new GridLayout(0, 1));
        JScrollPane scrollPane = new JScrollPane(checkBoxPanel);
        for (WebElement lectureElement : lectures) {
            String lectureTitle = lectureElement.findElement(By.cssSelector(LectureSelector.TITLE.get())).getText();
            JCheckBox checkBox = new JCheckBox(lectureTitle);
            checkBoxes.add(checkBox);
            checkBoxPanel.add(checkBox);
        }

        // 전체 선택/해제 체크박스에 대한 액션 리스너
        selectAllCheckBox.addActionListener(e -> {
            boolean isSelected = selectAllCheckBox.isSelected();
            for (JCheckBox checkBox : checkBoxes) {
                checkBox.setSelected(isSelected);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(null, panel, "강의를 선택하세요 (수강할 강의 체크)", JOptionPane.OK_CANCEL_OPTION);

        List<String> excludedLectures = new ArrayList<>();
        if (result == JOptionPane.OK_OPTION) {
            for (JCheckBox checkBox : checkBoxes) {
                if (checkBox.isSelected()) {
                    excludedLectures.add(checkBox.getText());
                }
            }
        }

        return excludedLectures;
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
        WebElement expandButton = lectureElement.findElement(By.cssSelector(LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1])));
        if (expandButton.isDisplayed()) {
            try {
                js.executeScript("arguments[0].click();", expandButton);
            } catch (Exception e) {
                log.error("강의 펼침 도중 오류 발생: " + lecture.getTitle());
            }
        }
    }

    private void waitForViewButtonAndClick(Video video) {
        // 강의 보기 버튼을 다시 대기
        WebElement viewButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get())));
        viewButton = wait.until(ExpectedConditions.elementToBeClickable(viewButton));
        js.executeScript("arguments[0].click();", viewButton);
    }

}