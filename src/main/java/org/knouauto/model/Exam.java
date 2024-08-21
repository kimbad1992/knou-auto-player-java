package org.knouauto.model;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.util.List;

public class Exam {
    private String id;
    private WebElement examForm;
    private WebElement confirmButton;
    private List<WebElement> answerChoices;
    private JavascriptExecutor js;

    // 생성자
    public Exam(WebElement examForm, JavascriptExecutor js) {
        this.examForm = examForm;
        this.js = js;
        this.id = examForm.getAttribute("id");
        this.confirmButton = examForm.findElement(By.cssSelector(".confirmAnswer"));
        this.answerChoices = examForm.findElements(By.cssSelector(".exam-answer .answerCh"));
    }

    public String getId() {
        return id;
    }

    public boolean isDescriptive() {
        // exqsDc 값이 "2"가 아닌 경우 서술형이 아님
        return "2".equals(examForm.findElement(By.name("exqsDc")).getAttribute("value"));
    }

    public void submitAnswer() {
        js.executeScript("arguments[0].click();", confirmButton);
    }

    public void selectAnswer(int index) {
        if (index >= 0 && index < answerChoices.size()) {
            WebElement answerChoice = answerChoices.get(index);
            js.executeScript("arguments[0].click();", answerChoice);
        }
    }

    public void setId(String id) {
        this.id = id;
    }

    public WebElement getExamForm() {
        return examForm;
    }

    public void setExamForm(WebElement examForm) {
        this.examForm = examForm;
    }

    public WebElement getConfirmButton() {
        return confirmButton;
    }

    public void setConfirmButton(WebElement confirmButton) {
        this.confirmButton = confirmButton;
    }

    public List<WebElement> getAnswerChoices() {
        return answerChoices;
    }

    public void setAnswerChoices(List<WebElement> answerChoices) {
        this.answerChoices = answerChoices;
    }
}
