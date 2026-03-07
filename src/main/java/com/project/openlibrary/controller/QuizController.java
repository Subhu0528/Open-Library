package com.project.openlibrary.controller;

import com.project.openlibrary.model.Quiz;
import com.project.openlibrary.model.QuizQuestion;
import com.project.openlibrary.model.User;
import com.project.openlibrary.repository.UserRepository;
import com.project.openlibrary.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quiz")
public class QuizController {

    @Autowired
    private QuizService quizService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String quizPage(Model model) {
        List<String> subjects = List.of(
                "Java Programming",
                "Python Programming",
                "JavaScript",
                "SQL Databases",
                "Web Development",
                "Spring Boot",
                "React.js",
                "Database Design",
                "General Knowledge",
                "Cloud Computing");

        model.addAttribute("subjects", subjects);
        model.addAttribute("questionCounts", List.of(10, 20, 30, 40, 50, 100));

        return "quiz";
    }

    @GetMapping("/start")
    public String startQuiz(@RequestParam String subject,
            @RequestParam Integer questions,
            Model model) {
        // For demo, using a default user. In production, get from session
        User user = userRepository.findAll().stream().findFirst().orElse(null);

        if (user == null) {
            return "redirect:/user_login_page";
        }

        Quiz quiz = quizService.createQuiz(subject, questions, user);
        List<QuizQuestion> quizQuestions = quizService.getQuizQuestions(quiz);

        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", quizQuestions);
        model.addAttribute("currentQuestionIndex", 0);

        return "quiz-questions";
    }

    @PostMapping("/submit-answer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitAnswer(@RequestBody Map<String, Object> request) {
        try {
            Long questionId = Long.parseLong(request.get("questionId").toString());
            String answer = request.get("answer").toString();

            QuizQuestion question = new QuizQuestion();
            question.setId(questionId);

            // In real implementation, fetch from repo
            quizService.submitAnswer(question, answer);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Answer submitted");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/complete/{quizId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeQuiz(@PathVariable Long quizId,
            @RequestBody Map<String, Object> request) {
        try {
            System.out.println("Quiz complete request for ID: " + quizId);
            System.out.println("Request body: " + request);

            // Get user from session/context
            User user = userRepository.findAll().stream().findFirst().orElse(null);
            Quiz quiz = quizService.getQuiz(quizId, user);

            if (quiz == null) {
                throw new RuntimeException("Quiz not found");
            }

            // Process answers from request
            List<Map<String, Object>> answers = (List<Map<String, Object>>) request.get("answers");
            if (answers != null) {
                System.out.println("Processing " + answers.size() + " answers");
                List<QuizQuestion> questions = quizService.getQuizQuestions(quiz);

                for (Map<String, Object> answer : answers) {
                    Integer questionIndex = ((Number) answer.get("questionIndex")).intValue();
                    String userAnswer = (String) answer.get("answer");

                    if (questionIndex >= 0 && questionIndex < questions.size()) {
                        QuizQuestion question = questions.get(questionIndex);
                        question.setUserAnswer(userAnswer);
                        boolean isCorrect = userAnswer.equals(question.getCorrectAnswer());
                        question.setIsCorrect(isCorrect);
                        quizService.submitAnswer(question, userAnswer);
                        System.out.println("Question " + (questionIndex + 1) + ": User=" + userAnswer + ", Correct="
                                + question.getCorrectAnswer() + ", IsCorrect=" + isCorrect);
                    }
                }
            }

            quizService.completeQuiz(quiz);

            List<QuizQuestion> questions = quizService.getQuizQuestions(quiz);
            int correctAnswers = (int) questions.stream()
                    .filter(QuizQuestion::getIsCorrect)
                    .count();

            int percentage = questions.size() > 0 ? (correctAnswers * 100 / questions.size()) : 0;

            System.out.println(
                    "Quiz completed. Correct: " + correctAnswers + "/" + questions.size() + " = " + percentage + "%");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("quizId", quizId);
            response.put("score", correctAnswers);
            response.put("totalQuestions", questions.size());
            response.put("percentage", percentage);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error completing quiz: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/result/{quizId}")
    public String getQuizResult(@PathVariable Long quizId, Model model) {
        try {
            System.out.println("Getting result for quiz: " + quizId);

            // Get user from session/context
            User user = userRepository.findAll().stream().findFirst().orElse(null);
            Quiz quiz = quizService.getQuiz(quizId, user);

            if (quiz == null) {
                System.out.println("Quiz not found for ID: " + quizId);
                return "redirect:/quiz";
            }

            System.out.println("Quiz found: " + quiz.getSubject());

            List<QuizQuestion> questions = quizService.getQuizQuestions(quiz);
            System.out.println("Total questions: " + questions.size());

            int correctAnswers = (int) questions.stream()
                    .filter(QuizQuestion::getIsCorrect)
                    .count();

            int totalQuestions = questions.size();
            int percentage = totalQuestions > 0 ? (correctAnswers * 100 / totalQuestions) : 0;

            System.out.println("Correct answers: " + correctAnswers);
            System.out.println("Percentage: " + percentage);

            // Log each question
            for (int i = 0; i < questions.size(); i++) {
                QuizQuestion q = questions.get(i);
                System.out.println("Q" + (i + 1) + ": UserAnswer=" + q.getUserAnswer() + ", CorrectAnswer="
                        + q.getCorrectAnswer() + ", IsCorrect=" + q.getIsCorrect());
            }

            model.addAttribute("quiz", quiz);
            model.addAttribute("questions", questions);
            model.addAttribute("correctAnswers", correctAnswers);
            model.addAttribute("totalQuestions", totalQuestions);
            model.addAttribute("percentage", percentage);

            System.out.println("Rendering quiz-result page");
            return "quiz-result";
        } catch (Exception e) {
            System.err.println("Error getting quiz result: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/quiz";
        }
    }

    @GetMapping("/history")
    public String getQuizHistory(Model model) {
        // Get user from session/context
        User user = userRepository.findAll().stream().findFirst().orElse(null);

        if (user == null) {
            return "redirect:/user_login_page";
        }

        List<Quiz> quizzes = quizService.getUserQuizzes(user);
        model.addAttribute("quizzes", quizzes);

        return "quiz-history";
    }
}
