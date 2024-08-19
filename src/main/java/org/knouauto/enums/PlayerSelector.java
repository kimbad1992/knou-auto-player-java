package org.knouauto.enums;

public enum PlayerSelector {
    ROOT("ifrmVODPlayer_0"),
    PLAY(".jw-icon-display2"),
    WATCH_CONTINUE("#wp_elearning_seek"),
    ELAPSED("span.jw-text-elapsed"),
    TOTAL_DURATION("span.jw-text-duration"),
    END_STUDY("button.studyend");

    private final String selector;

    PlayerSelector(String selector) {
        this.selector = selector;
    }

    public String get() {
        return selector;
    }
}
