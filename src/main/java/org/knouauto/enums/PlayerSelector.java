package org.knouauto.enums;

public enum PlayerSelector {
    ROOT("ifrmVODPlayer_0"),
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
