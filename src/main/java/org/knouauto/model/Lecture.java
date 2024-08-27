package org.knouauto.model;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
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

    public WebElement getLectureElement(WebDriver driver) {
        try {
            // 요소가 여전히 유효한지 확인
            lectureElement.isDisplayed();
        } catch (StaleElementReferenceException e) {
            // StaleElementReferenceException이 발생하면 새로운 요소를 다시 찾음
            refreshLectureElement(driver);
        }
        return lectureElement;
    }

    public void setLectureElement(WebElement lectureElement) {
        this.lectureElement = lectureElement;
    }

    private void refreshLectureElement(WebDriver driver) {
        // 기존에 저장된 ID를 사용하여 새로운 lectureElement를 찾음
        this.lectureElement = driver.findElement(By.id(this.id));
    }

}