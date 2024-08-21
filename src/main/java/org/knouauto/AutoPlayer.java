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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AutoPlayer {

    public static final String LOGIN_URL = "https://ucampus.knou.ac.kr/ekp/user/login/retrieveULOLogin.do";
    public static final String STUDY_URL = "https://ucampus.knou.ac.kr/ekp/user/study/retrieveUMYStudy.sdo";

    public static String[] HEADLESS_OPTIONS = {"--headless", "window-size=800x600", "disable-gpu"};
    public static String[] MUTE_OPTIONS = {"--mute-audio"};

    public static final int VIDEO_ELAPSE_PERCENT = 60;
    public static final long DRIVER_WAIT_SEC = 5L;

    // TOOD : 강의 스킵용 임시 변수
    public static final List<String> SKIP_LECTURES =
             // Arrays.asList("철학의이해", "자료구조", "프로그래밍언어론", "UNIX시스템", "심리학에게묻다");
             new ArrayList<>();

    private WebDriver driver;
    private JavascriptExecutor js;
    private WebDriverWait wait;
    private final ColorLogger log;
    private SwingWorker<Void, String> worker;

    private boolean isPlayingVideo = false;

    public AutoPlayer(ColorLogger log) {
        this.log = log;
    }

    public void start(String userId, String userPassword, boolean enableHeadless, boolean muteAudio) {
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // WebDriver 초기화 및 옵션 설정
                    initializeDriver(enableHeadless, muteAudio);

                    // 로그인 및 강의 실행
                    startLearning(userId, userPassword);
                } catch (InterruptedException e) {
                    publish("유저 취소: " + e.getMessage());
                } catch (Exception e) {
                    publish("에러 발생: " + e.getMessage());
                } finally {
                    cleanup();
                    publish("중지했습니다.");
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

            for (WebElement lectureElement : lectures) {
                Lecture lecture = new Lecture();
                lecture.setId(lectureElement.getAttribute("id"));
                lecture.setTitle(lectureElement.findElement(By.cssSelector(LectureSelector.TITLE.get())).getText());
                lecture.setLectureElement(lectureElement);  // WebElement 저장

                // TODO : 테스트용 강의 스킵 임시 추가
                if (SKIP_LECTURES.contains(lecture.getTitle().trim())) {
                    log.info(lecture.getTitle() + "강의를 스킵합니다.");
                    continue;
                }

                log.info(lecture.toString());

                // 강의를 펼치는 코드 추가 (JavaScript 사용)
                WebElement moreButton = lectureElement.findElement(By.cssSelector(LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1])));
                if (moreButton.isDisplayed()) {
                    try {
                        js.executeScript("arguments[0].click();", moreButton);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        log.error("강의 펼침 도중 오류 발생: " + lecture.getTitle());
                    }
                }

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

        for (Lecture lecture : lectures) {
            for (Video video : lecture.getVideos()) {
                if (worker.isCancelled()) {
                    return; // 작업이 취소되었으면 즉시 종료
                }

                if (video.isWaiting() || video.isWatched()) {
                    log.info("시청 생략(강의 대기 혹은 시청 완료) : " + video.toString());
                    continue;
                }

                String title = lecture.getTitle() + " :: " + video.getTitle();

                // 비디오 재생
                try {
                    if (worker.isCancelled() || Thread.currentThread().isInterrupted()) {
                        return; // 작업이 취소되었으면 즉시 종료
                    }

                    log.newLine();
                    log.info("재생 시작 :: " + title);

                    // 메인 창으로 포커스 전환
                    switchToMainWindow();

                    // 강의 펼치기
                    WebElement showButton = driver.findElement(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get()));
                    if (!showButton.isDisplayed()) {
                        WebElement lectureElement = lecture.getLectureElement();
                        WebElement moreButton = lectureElement.findElement(By.cssSelector(LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1])));
                        if (moreButton.isDisplayed()) {
                            try {
                                js.executeScript("arguments[0].click();", moreButton);
                                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get())));
                            } catch (Exception e) {
                                log.error("강의 펼침 도중 오류 발생: " + lecture.getTitle());
                            }
                        }
                        showButton = driver.findElement(By.cssSelector("#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get()));
                    }
                    showButton = wait.until(ExpectedConditions.elementToBeClickable(showButton));

                    js.executeScript("arguments[0].click();", showButton);

                    // log.info("비디오 표시 버튼 클릭 완료: " + title);

                    // 팝업 창으로 포커스 전환
                    switchToPopupWindow();

                    // 프레임이 로드될 때까지 대기
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(PlayerSelector.ROOT.get()));
                } catch (UnhandledAlertException e) {
                    String alertMsg = e.getAlertText();
                    if (alertMsg.indexOf("초과") > 0) {
                        log.warn("일일 수강 한도에 도달했습니다.");
                        handleAlertAccept(e);
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
                    watchingVideo(title);
                    endVideo();
                } catch (InterruptedException e) {
                    log.warn("유저 취소로 인한 중단");
                }
            }
        }
    }

    public void watchingVideo(String title) throws InterruptedException {
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

            Thread.sleep(500);
        }

        isPlayingVideo = false;

        log.newLine();
        log.info("강의 시청 완료: " + title);
    }

    public void endVideo() {
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
        driver.switchTo().defaultContent();
        driver.switchTo().window(driver.getWindowHandles().iterator().next());
    }

    private void switchToPopupWindow() {
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowHandle.equals(driver.getWindowHandles().iterator().next())) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }
        driver.switchTo().defaultContent();
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

}