package com.project.openlibrary.model;

import jakarta.persistence.*;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String questionText;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String optionA;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String optionB;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String optionC;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String optionD;
    
    @Column(nullable = false)
    private String correctAnswer; // A, B, C, or D
    
    @Column(nullable = false)
    private String explanation;
    
    @Column
    private String userAnswer; // A, B, C, D, or null if not answered
    
    @Column(nullable = false)
    private Integer questionNumber;
    
    @Column(nullable = false)
    private Boolean isCorrect = false;
    
    // Constructors
    public QuizQuestion() {
    }
    
    public QuizQuestion(Quiz quiz, String questionText, String optionA, String optionB, 
                       String optionC, String optionD, String correctAnswer, 
                       String explanation, Integer questionNumber) {
        this.quiz = quiz;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.questionNumber = questionNumber;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Quiz getQuiz() {
        return quiz;
    }
    
    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }
    
    public String getQuestionText() {
        return questionText;
    }
    
    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }
    
    public String getOptionA() {
        return optionA;
    }
    
    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }
    
    public String getOptionB() {
        return optionB;
    }
    
    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }
    
    public String getOptionC() {
        return optionC;
    }
    
    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }
    
    public String getOptionD() {
        return optionD;
    }
    
    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }
    
    public String getCorrectAnswer() {
        return correctAnswer;
    }
    
    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public String getUserAnswer() {
        return userAnswer;
    }
    
    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }
    
    public Integer getQuestionNumber() {
        return questionNumber;
    }
    
    public void setQuestionNumber(Integer questionNumber) {
        this.questionNumber = questionNumber;
    }
    
    public Boolean getIsCorrect() {
        return isCorrect;
    }
    
    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
}
