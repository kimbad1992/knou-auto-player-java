package org.knouauto.model;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

public class Lecture {
    String id;
    String title;
    List<Video> videos = new ArrayList<>();
    private String lectureElement;  // ElementHandle은 직접 저장할 수 없어 문자열로 변환

    // 강의 정보를 문자열로 출력하는 메서드
    @Override
    public String toString() {
        return ":::" + id + "::" + title;
    }

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

    public List<Video> getVideos() {
        return videos;
    }

    public void setVideos(List<Video> videos) {
        this.videos = videos;
    }

    public ElementHandle getLectureElement(Page page) {
        // ID를 사용하여 요소를 찾음
        return page.querySelector("#" + this.id);
    }

    public void setLectureElement(String lectureElement) {
        this.lectureElement = lectureElement;
    }
}
