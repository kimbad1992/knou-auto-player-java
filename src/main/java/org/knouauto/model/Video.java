package org.knouauto.model;

import java.util.Locale;

public class Video {
    private String id;
    private String title;
    private boolean waiting;
    private boolean watched;

    // 비디오 정보를 문자열로 출력하는 메서드
    @Override
    public String toString() {
        String symbol;
        if (waiting) {
            symbol = "◻";  // waiting 상태일 때
        } else if (watched) {
            symbol = "✔";  // watched 상태일 때
        } else {
            symbol = "✖";  // 아직 시청하지 않은 상태일 때
        }
        return String.format("%s %s :: %s", symbol, id, title);
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    public boolean isWatched() {
        return watched;
    }

    public void setWatched(boolean watched) {
        this.watched = watched;
    }
}
