package org.knouauto.settings;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * 자동 로그인 정보 저장소
 * - 아이디/실행 옵션: Preferences (Windows 레지스트리, macOS plist)
 * - 비밀번호: OS 자격 증명 저장소 (Windows 자격 증명 관리자, macOS 키체인)
 * - 마지막으로 선택한 과목 목록: Preferences (자동 로그인과 별개로 유지)
 */
public class AutoLoginStore {

    private static final String KEYRING_SERVICE = "KNOU Auto Player";

    private static final String KEY_ENABLED = "autoLogin";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_HEADLESS = "headless";
    private static final String KEY_MUTE_AUDIO = "muteAudio";
    private static final String KEY_OVER_LIMIT = "overLimit";
    private static final String KEY_SKIP_LECTURE_POPUP = "skipLecturePopup";
    private static final String KEY_SELECTED_LECTURES = "selectedLectures";

    // 과목 제목 구분자 (제목에 들어갈 일이 없는 제어 문자)
    private static final String LECTURE_DELIMITER = "\u001F";

    private final Preferences prefs = Preferences.userNodeForPackage(AutoLoginStore.class);

    public record Settings(String userId, boolean headless, boolean muteAudio, boolean overLimit,
                           boolean skipLecturePopup) {
    }

    // 자동 로그인이 설정되어 있지 않으면 empty
    public Optional<Settings> load() {
        String userId = prefs.get(KEY_USER_ID, null);
        if (!prefs.getBoolean(KEY_ENABLED, false) || userId == null) {
            return Optional.empty();
        }
        return Optional.of(new Settings(
                userId,
                prefs.getBoolean(KEY_HEADLESS, false),
                prefs.getBoolean(KEY_MUTE_AUDIO, false),
                prefs.getBoolean(KEY_OVER_LIMIT, false),
                prefs.getBoolean(KEY_SKIP_LECTURE_POPUP, false)));
    }

    public String loadPassword(String userId) throws Exception {
        try (Keyring keyring = Keyring.create()) {
            return keyring.getPassword(KEYRING_SERVICE, userId);
        }
    }

    public void save(Settings settings, String password) throws Exception {
        String previousUserId = prefs.get(KEY_USER_ID, null);
        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(KEYRING_SERVICE, settings.userId(), password);
            // 아이디가 바뀌었으면 이전 아이디의 비밀번호는 삭제
            if (previousUserId != null && !previousUserId.equals(settings.userId())) {
                deletePasswordQuietly(keyring, previousUserId);
            }
        }

        prefs.putBoolean(KEY_ENABLED, true);
        prefs.put(KEY_USER_ID, settings.userId());
        prefs.putBoolean(KEY_HEADLESS, settings.headless());
        prefs.putBoolean(KEY_MUTE_AUDIO, settings.muteAudio());
        prefs.putBoolean(KEY_OVER_LIMIT, settings.overLimit());
        prefs.putBoolean(KEY_SKIP_LECTURE_POPUP, settings.skipLecturePopup());
        prefs.flush();
    }

    // 자동 로그인 정보만 삭제 (과목 선택 기억은 유지)
    public void clear() throws Exception {
        String userId = prefs.get(KEY_USER_ID, null);
        prefs.remove(KEY_ENABLED);
        prefs.remove(KEY_USER_ID);
        prefs.remove(KEY_HEADLESS);
        prefs.remove(KEY_MUTE_AUDIO);
        prefs.remove(KEY_OVER_LIMIT);
        prefs.remove(KEY_SKIP_LECTURE_POPUP);
        prefs.flush();

        if (userId != null) {
            try (Keyring keyring = Keyring.create()) {
                deletePasswordQuietly(keyring, userId);
            }
        }
    }

    // 마지막으로 팝업에서 확정한 과목 제목 목록 (저장값 없으면 빈 목록)
    public List<String> loadSelectedLectures() {
        String raw = prefs.get(KEY_SELECTED_LECTURES, "");
        List<String> titles = new ArrayList<>();
        for (String title : raw.split(LECTURE_DELIMITER)) {
            if (!title.isBlank()) {
                titles.add(title.trim());
            }
        }
        return titles;
    }

    public void saveSelectedLectures(List<String> titles) throws Exception {
        List<String> cleaned = new ArrayList<>();
        for (String title : titles) {
            if (title != null && !title.isBlank()) {
                cleaned.add(title.trim());
            }
        }
        String joined = String.join(LECTURE_DELIMITER, cleaned);
        if (joined.length() > Preferences.MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("과목 목록이 너무 길어 저장할 수 없습니다.");
        }
        prefs.put(KEY_SELECTED_LECTURES, joined);
        prefs.flush();
    }

    // 저장된 비밀번호가 없어도 예외 없이 넘어감
    private void deletePasswordQuietly(Keyring keyring, String userId) {
        try {
            keyring.deletePassword(KEYRING_SERVICE, userId);
        } catch (PasswordAccessException ignored) {
        }
    }
}
