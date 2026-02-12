package com.project.openlibrary.repository;

import com.project.openlibrary.model.QuizQuestion;
import com.project.openlibrary.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<QuizQuestion, Long> {
    List<QuizQuestion> findByQuiz(Quiz quiz);
    List<QuizQuestion> findByQuizOrderByQuestionNumber(Quiz quiz);
}
