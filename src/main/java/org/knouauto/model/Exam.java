package org.knouauto.model;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import java.util.List;

public class Exam {
    private String id;
    private ElementHandle examForm;
    private ElementHandle confirmButton;
    private List<ElementHandle> answerChoices;
    private Page page;

    // 생성자
    public Exam(ElementHandle examForm, Page page) {
        this.examForm = examForm;
        this.page = page;
        this.id = examForm.getAttribute("id");
        this.confirmButton = examForm.querySelector(".confirmAnswer");
        this.answerChoices = examForm.querySelectorAll(".exam-answer .answerCh");
    }

    public String getId() {
        return id;
    }

    public boolean isDescriptive() {
        // exqsDc 값이 "2"가 아닌 경우 서술형이 아님
        ElementHandle exqsDc = examForm.querySelector("[name='exqsDc']");
        return exqsDc != null && "2".equals(exqsDc.getAttribute("value"));
    }

    public void submitAnswer() {
        confirmButton.click();
    }

    public void selectAnswer(int index) {
        if (index >= 0 && index < answerChoices.size()) {
            ElementHandle answerChoice = answerChoices.get(index);
            answerChoice.click();
        }
    }

    public List<ElementHandle> getAnswerFields() {
        return examForm.querySelectorAll("textarea, input[type='text']");
    }

    public ElementHandle getResultElement() {
        return examForm.querySelector("input[name='resultCnt']");
    }

    public void setId(String id) {
        this.id = id;
    }

    public ElementHandle getExamForm() {
        return examForm;
    }

    public void setExamForm(ElementHandle examForm) {
        this.examForm = examForm;
    }

    public ElementHandle getConfirmButton() {
        return confirmButton;
    }

    public void setConfirmButton(ElementHandle confirmButton) {
        this.confirmButton = confirmButton;
    }

    public List<ElementHandle> getAnswerChoices() {
        return answerChoices;
    }

    public void setAnswerChoices(List<ElementHandle> answerChoices) {
        this.answerChoices = answerChoices;
    }
}
