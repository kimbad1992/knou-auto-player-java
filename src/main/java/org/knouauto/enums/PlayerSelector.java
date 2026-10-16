package org.knouauto.enums;

public enum PlayerSelector {
    ROOT("ifrmVODPlayer_0"),
    // 재생 창 하나에 플레이어가 여러 개일 수 있음 (ifrmVODPlayer_0, _1, ...)
    FRAMES("iframe[id^='ifrmVODPlayer_']"),
    // 플레이어 프레임을 감싼 상자와 제목 (오리엔테이션 구분용)
    CONTENT_BOX("./ancestor::div[contains(@class, 'content-box')]"),
    CONTENT_BOX_TITLE(".content-box-title"),
    PLAY(".jw-icon-display2"),
    WATCH_CONTINUE("#wp_elearning_seek"),
    ELAPSED("span.jw-text-elapsed"),
    TOTAL_DURATION("span.jw-text-duration"),
    END_STUDY("button.studyend"),

    // 돌발 퀴즈
    SURPRISE_QUIZ_MODAL("div.modal"),
    SURPRISE_QUIZ_FORM("form[name^='frm_quiz_']"),
    SURPRISE_QUIZ_ANSWER("span[name='exqsCansCn']"),
    SURPRISE_QUIZ_CLOSE_BUTTON("button.quizClose"),
    SURPRISE_QUIZ_CONFIRM_BUTTON(".confirmAnswer");

    private final String selector;

    PlayerSelector(String selector) {
        this.selector = selector;
    }

    public String get() {
        return selector;
    }
}
