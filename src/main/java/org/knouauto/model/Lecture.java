package org.knouauto.model;

import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

public class Lecture {
    String id;
    String title;
    List<Video> videos = new ArrayList<>();
    private WebElement lectureElement;  // WebElement 필드 추가

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

    public WebElement getLectureElement() {
        return lectureElement;
    }

    public void setLectureElement(WebElement lectureElement) {
        this.lectureElement = lectureElement;
    }
}