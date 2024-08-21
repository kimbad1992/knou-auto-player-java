package org.knouauto.enums;

// LectureSelector enum 정의
public enum LectureSelector {
    ROOT("div.lecture-progress-item.inactive"),
    TITLE(".lecture-title"),
    MORE("a#btn-toggle-@"),
    BODY(".lecture-progress-item-body"),
    VIDEO_ROOT("ul.lecture-list > li"),
    VIDEO_TITLE(".lecture-title"),
    VIDEO_WATCHED("li > a.ch"), // li 내의 a.ch 요소를 선택
    VIDEO_EXERCISE("fieldset a.ch"), // fieldset 내의 a.ch 요소를 선택
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
