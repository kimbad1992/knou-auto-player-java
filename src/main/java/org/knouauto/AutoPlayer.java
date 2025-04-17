package org.knouauto;

import org.knouauto.enums.LectureSelector;
import org.knouauto.enums.PlayerSelector;
import org.knouauto.logger.ColorLogger;
import org.knouauto.model.Exam;
import org.knouauto.model.Lecture;
import org.knouauto.model.Video;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoPlayer {

    public static final String LOGIN_URL = "https://ucampus.knou.ac.kr/ekp/user/login/retrieveULOLogin.do";
    public static final String STUDY_URL = "https://ucampus.knou.ac.kr/ekp/user/study/retrieveUMYStudy.sdo";

    public static final int VIDEO_ELAPSE_PERCENT = 60;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private Page popupPage;
    private SwingWorker<Void, String> worker;
    private Runnable stopCallback;

    private final ColorLogger log;

    private boolean isPlayingVideo = false;
    private boolean doOverLimit;

    public AutoPlayer(ColorLogger log) {
        this.log = log;
    }

    public void setStopCallback(Runnable stopCallback) {
        this.stopCallback = stopCallback;
    }

    public void start(String userId, String userPassword, boolean enableHeadless, boolean muteAudio, boolean overLimit) {
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    doOverLimit = overLimit;

                    // Playwright 초기화 및 옵션 설정
                    initializePlaywright(enableHeadless, muteAudio);

                    // 로그인 및 강의 실행
                    startLearning(userId, userPassword);
                } catch (InterruptedException e) {
                    publish("유저 취소: " + e.getMessage());
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

    // Playwright 초기화
    private void initializePlaywright(boolean enableHeadless, boolean muteAudio) {
        playwright = Playwright.create();
        
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(enableHeadless);
        
        if (muteAudio) {
            launchOptions.setArgs(List.of("--mute-audio"));
        }
        
        browser = playwright.chromium().launch(launchOptions);
        context = browser.newContext();
        page = context.newPage();
        
        // 팝업 페이지 처리를 위한 이벤트 리스너 설정
        context.onPage(newPage -> {
            if (popupPage == null) {
                popupPage = newPage;
            }
        });
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

        if (page != null) {
            page.close();
        }
        
        if (popupPage != null) {
            popupPage.close();
        }
        
        if (context != null) {
            context.close();
        }
        
        if (browser != null) {
            browser.close();
        }
        
        if (playwright != null) {
            playwright.close();
        }
        
        isPlayingVideo = false;
    }

    public void stop() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true); // 작업 취소
        }
    }

    private void login(String userId, String userPassword) throws Exception {
        page.navigate(LOGIN_URL);
        
        // 로그인 폼 작성
        page.fill("input[name='username']", userId);
        page.fill("input[name='password']", userPassword);
        
        // 로그인 버튼 클릭 (Enter 키 대신 클릭 사용)
        page.press("input[name='password']", "Enter");
        
        // URL 변경 대기
        page.waitForURL(STUDY_URL);
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
            List<ElementHandle> lectures = page.querySelectorAll(LectureSelector.ROOT.get());

            // 강의 타이틀을 기준으로 체크박스를 팝업에서 선택받기
            List<String> includedLectures = showLectureSelectionPopup(lectures);

            for (ElementHandle lectureElement : lectures) {
                Lecture lecture = new Lecture();
                lecture.setId(lectureElement.getAttribute("id"));
                lecture.setTitle(lectureElement.querySelector(LectureSelector.TITLE.get()).textContent());
                lecture.setLectureElement(lectureElement.toString());  // ElementHandle은 직접 저장할 수 없어 문자열로 변환

                if (!includedLectures.contains(lecture.getTitle().trim())) {
                    log.info(lecture.getTitle() + "강의를 스킵합니다.");
                    continue;
                }

                log.info(lecture.toString());

                // 강의를 펼치는 코드 추가
                expandLecture(lecture);

                // 비디오 목록 가져오기
                List<ElementHandle> videos = lectureElement.querySelectorAll(LectureSelector.VIDEO_ROOT.get());

                for (ElementHandle videoElement : videos) {
                    totalVideos++;

                    Video video = new Video();
                    video.setId(videoElement.getAttribute("id"));
                    video.setTitle(videoElement.querySelector(LectureSelector.VIDEO_TITLE.get()).textContent());

                    // 대기 중인 비디오인지 확인
                    ElementHandle waitingElement = videoElement.querySelector(LectureSelector.VIDEO_WAITING.get());
                    boolean isWaiting = waitingElement != null && waitingElement.isVisible();
                    video.setWaiting(isWaiting);

                    // 시청한 비디오인지 확인
                    ElementHandle watchedElement = videoElement.querySelector(LectureSelector.VIDEO_WATCHED.get());
                    boolean isWatched = watchedElement != null && watchedElement.getAttribute("class").contains("on");
                    video.setWatched(isWatched);

                    // 연습문제 확인
                    ElementHandle exerciseElement = videoElement.querySelector(LectureSelector.VIDEO_EXERCISE.get());
                    boolean isExercise = exerciseElement != null && exerciseElement.getAttribute("class").contains("on");
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
            log.error("강의 로딩 중 에러 발생: " + e.getMessage());
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

                // 비디오 재생
                try {
                    if (worker.isCancelled() || Thread.currentThread().isInterrupted()) {
                        return; // 작업이 취소되었으면 즉시 종료
                    }

                    log.newLine();
                    log.info("재생 시작 :: " + title);

                    // 메인 창으로 포커스 전환
                    switchToMainWindow();

                    // 비디오 재생 버튼 클릭
                    clickViewButton(lecture, video);

                    // 팝업 창으로 포커스 전환
                    switchToPopupWindow();
                    
                    // 프레임으로 전환
                    FrameLocator playerFrameLocator = popupPage.frameLocator(PlayerSelector.ROOT.get());
                    if (playerFrameLocator == null) {
                        log.error("플레이어 프레임을 찾을 수 없습니다.");
                        continue;
                    }
                    
                } catch (Exception e) {
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && errorMessage.contains("초과")) {
                        // 일일 수강 한도 초과
                        log.warn("일일 수강 한도에 도달했습니다.");

                        if (doOverLimit) {
                            break videoLoop;
                        } else {
                            break lectureLoop;
                        }
                    } else if (errorMessage != null && errorMessage.contains("진도율")) {
                        // 진도율 체크되지 않음
                        log.warn("진도율이 체크 되지 않고 있습니다.");
                    } else {
                        log.error("비디오 재생 실패: " + video.getTitle() + " (" + e.getMessage() + ")");
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("대기 중 취소.");
                }

                // 연습 문제를 통과하지 못한 경우
                if (!video.isExercise()) {
                    log.info("연습 문제를 확인합니다.");
                    
                    try {
                        // Playwright에서는 기본 컨텍스트로 돌아갈 필요가 없음
                        List<ElementHandle> examForms = popupPage.querySelectorAll("form[id^='frm_']");

                        for (ElementHandle examForm : examForms) {
                            Exam exam = new Exam(examForm, popupPage);
                            solveExam(exam);  // 각 문제를 해결
                        }

                        log.success(title + "의 문제 풀이를 완료했습니다.");
                    } catch (Exception e) {
                        log.info("연습 문제가 없거나 처리 중 오류 발생: " + e.getMessage());
                    }

                    log.newLine();
                } else {
                    log.info("기존에 연습 문제를 통과한 이력이 있습니다.");
                }

                try {
                    playAllVideosInPopup(title);
                    endVideo();
                } catch (InterruptedException e) {
                    log.newLine();
                    log.warn("유저 취소로 인한 중단");
                }
            }
        }
    }

    private void watchingVideo(String title) throws InterruptedException {
        FrameLocator playerFrameLocator = popupPage.frameLocator(PlayerSelector.ROOT.get());
        String totalTime = playerFrameLocator.locator(PlayerSelector.TOTAL_DURATION.get()).textContent();
        int totalSeconds = stringToSecond(totalTime);
    
        log.info("총 재생 시간: " + totalTime);
    
        boolean keepPlaying = true;
        int elapsedSeconds = 0;
    
        isPlayingVideo = true;
    
        while (keepPlaying) {
            String elapsedTime = playerFrameLocator.locator(PlayerSelector.ELAPSED.get()).textContent();
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

    private void endVideo() {
        try {
            // Cleanup시 endVideo 중복호출 되지 않도록
            isPlayingVideo = false;

            // 팝업 페이지로 전환
            switchToPopupWindow();
            
            // 학습 종료 스크립트 실행
            popupPage.evaluate("fnStudyEnd();");
            
            // 알림 대기 및 처리
            popupPage.waitForPopup(() -> {
                popupPage.keyboard().press("Enter");
            });

            log.info("학습 종료 완료");
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
        // Playwright에서는 page 객체를 사용하므로 메인 페이지로 전환만 필요
        if (popupPage != null) {
            page.bringToFront();
        }
    }

    private void switchToPopupWindow() {
        // 팝업 페이지가 이미 생성되어 있다면 그 페이지를
        if (popupPage != null) {
            popupPage.bringToFront();
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

                attemptCount++;
            }
        } catch (Exception e) {
            log.error("연습문제 풀이 도중 오류 발생: " + e.getMessage());
        }
    }


    private boolean descriptiveAnswer(Exam exam) {
        try {
            List<ElementHandle> answerFields = exam.getAnswerFields();
            for (ElementHandle field : answerFields) {
                field.fill("잘모루겠습니다교수님");
            }
            exam.submitAnswer();

            return checkResult(exam);
        } catch (Exception e) {
            handleDialogIfPresent();
            log.error("서술형 문제 처리 중 에러가 발생했습니다: " + e.getMessage());
            return false;
        }
    }

    private boolean choiceAnswer(Exam exam) {
        try {
            List<ElementHandle> choices = exam.getAnswerChoices();
            if (!choices.isEmpty()) {
                int randomIndex = new Random().nextInt(choices.size());
                exam.selectAnswer(randomIndex);
            }
            exam.submitAnswer();

            return checkResult(exam);
        } catch (Exception e) {
            handleDialogIfPresent();
            log.error("객관식 문제 처리 중 에러가 발생했습니다: " + e.getMessage());
            return false;
        }
    }

    private boolean checkResult(Exam exam) {
        try {
            ElementHandle resultCnt = exam.getResultElement();
            String resultValue = resultCnt.getAttribute("value");

            return "1".equals(resultValue);
        } catch (Exception e) {
            log.error("정답 확인 중 에러가 발생했습니다: " + e.getMessage());
            return false;
        }
    }

    private void handleDialogIfPresent() {
        try {
            // 다이얼로그(알림창) 처리를 위한 이벤트 리스너 설정
            popupPage.onDialog(dialog -> {
                dialog.accept();
            });
        } catch (Exception e) {
            log.error("다이얼로그 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private List<String> showLectureSelectionPopup(List<ElementHandle> lectures) {
        List<JCheckBox> checkBoxes = new ArrayList<>();
        JPanel panel = new JPanel(new BorderLayout());

        // 전체 선택/해제 체크박스
        JCheckBox selectAllCheckBox = new JCheckBox("전체 선택/해제");
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(selectAllCheckBox);

        // 강의 목록 체크박스 패널
        JPanel checkBoxPanel = new JPanel(new GridLayout(0, 1));
        JScrollPane scrollPane = new JScrollPane(checkBoxPanel);
        for (ElementHandle lectureElement : lectures) {
            String lectureTitle = lectureElement.querySelector(LectureSelector.TITLE.get()).textContent();
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

        List<String> includedLectures = new ArrayList<>();
        if (result == JOptionPane.OK_OPTION) {
            for (JCheckBox checkBox : checkBoxes) {
                if (checkBox.isSelected()) {
                    includedLectures.add(checkBox.getText());
                }
            }
        }

        return includedLectures;
    }

    private void clickViewButton(Lecture lecture, Video video) {
        String selector = "#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get();
        ElementHandle viewButton = page.querySelector(selector);
        
        if (viewButton == null) {
            // 강의보기 버튼이 존재하지 않는 경우(과목 전환 Case)
            expandLecture(lecture);
            waitForViewButtonAndClick(video);
        } else if (!viewButton.isVisible()) {
            // 강의보기 버튼이 가시적이지 않을 경우 펼치기
            expandLecture(lecture);
            waitForViewButtonAndClick(video);
        } else {
            // 버튼 클릭
            viewButton.click();
        }
    }

    private void expandLecture(Lecture lecture) {
        String expandSelector = LectureSelector.MORE.get().replace("@", lecture.getId().split("-")[1]);
        ElementHandle expandButton = page.querySelector(expandSelector);
        if (expandButton != null && expandButton.isVisible()) {
            try {
                expandButton.click();
            } catch (Exception e) {
                log.error("강의 펼침 도중 오류 발생: " + lecture.getTitle() + ", " + e.getMessage());
            }
        }
    }

    private void waitForViewButtonAndClick(Video video) {
        String selector = "#" + video.getId() + " > " + LectureSelector.VIDEO_SHOW_VIDEO.get();
        // Playwright는 자동으로 요소가 나타날 때까지 대기
        ElementHandle viewButton = page.waitForSelector(selector);
        viewButton.click();
    }

    private void playAllVideosInPopup(String title) throws InterruptedException {
        FrameLocator playerFrameLocator = popupPage.frameLocator(PlayerSelector.ROOT.get());
        
        List<Locator> playButtons = getAllVideoButtonsWithScrolling(playerFrameLocator);
        log.info("팝업 내 발견된 재생 버튼 개수: " + playButtons.size());
    
        for (int i = 0; i < playButtons.size(); i++) {
            if (worker.isCancelled()) return;
    
            Locator playButton = playButtons.get(i);
            
            // 재생 버튼 클릭
            playButton.scrollIntoViewIfNeeded();
            playButton.click();
    
            Thread.sleep(1000); // 클릭 후 플레이어 로딩 대기
    
            // 이어보기 버튼 처리(존재하면 클릭)
            Locator continueButton = playerFrameLocator.locator(PlayerSelector.WATCH_CONTINUE.get());
            if (continueButton.isVisible()) {
                continueButton.click();
            }

            // 각 비디오별 재생 시간 처리
            String currentVideoTitle = title + " - 파트 " + (i + 1);
            watchingVideo(currentVideoTitle);

            log.info("강의 파트 시청 완료: " + currentVideoTitle);
            log.newLine();
        }
    }

    private List<Locator> getAllVideoButtonsWithScrolling(FrameLocator playerFrameLocator) throws InterruptedException {
        List<Locator> playButtons = new ArrayList<>();
        int previousSize = -1;
    
        while (true) {
            Locator buttonsLocator = playerFrameLocator.locator(PlayerSelector.PLAY.get());
            int currentSize = buttonsLocator.count();
    
            if (currentSize == previousSize) {
                // 더 이상 새 요소가 로딩되지 않으면 종료
                break;
            }
    
            previousSize = currentSize;
    
            // 맨 마지막 요소로 스크롤하여 추가 콘텐츠 로딩 유도
            if (currentSize > 0) {
                buttonsLocator.nth(currentSize - 1).scrollIntoViewIfNeeded();
                Thread.sleep(500); // 로딩 대기
            } else {
                break;
            }
        }
    
        Locator allButtons = playerFrameLocator.locator(PlayerSelector.PLAY.get());
        int count = allButtons.count();
        List<Locator> result = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            result.add(allButtons.nth(i));
        }
        
        log.info("최종 발견된 재생 버튼 개수: " + result.size());
        return result;
    }
}