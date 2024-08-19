package org.knouauto.enums;

// LectureSelector enum 정의
public enum LectureSelector {
    ROOT("div.lecture-progress-item.inactive"),
    TITLE(".lecture-title"),
    MORE("a#btn-toggle-@"),
    BODY(".lecture-progress-item-body"),
    VIDEO_ROOT("ul.lecture-list > li"),
    VIDEO_TITLE(".lecture-title"),
    VIDEO_WATCHED("a.ch"),
    VIDEO_SHOW_VIDEO("a.btn.lecture-view"),
    VIDEO_WAITING("span.con-waiting");

    private final String selector;

    LectureSelector(String selector) {
        this.selector = selector;
    }

    public String get() {
        return selector;
    }
}
