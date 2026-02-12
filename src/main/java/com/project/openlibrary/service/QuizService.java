package com.project.openlibrary.service;

import com.project.openlibrary.model.Quiz;
import com.project.openlibrary.model.QuizQuestion;
import com.project.openlibrary.model.User;
import com.project.openlibrary.repository.QuizRepository;
import com.project.openlibrary.repository.QuestionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuizService {
    
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${grok.api.key:}")
    private String grokApiKey;
    
    private static final String GROK_API_URL = "https://api.x.ai/v1/chat/completions";
    
    public QuizService(QuizRepository quizRepository, QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = new ObjectMapper();
    }
    
    public Quiz createQuiz(String subject, Integer numberOfQuestions, User user) {
        Quiz quiz = new Quiz(subject, numberOfQuestions, user);
        quiz = quizRepository.save(quiz);
        
        // Generate questions using Grok API
        generateQuestionsWithGrok(quiz);
        
        return quiz;
    }
    
    private void generateQuestionsWithGrok(Quiz quiz) {
        try {
            System.out.println("Starting question generation for quiz: " + quiz.getId());
            System.out.println("Subject: " + quiz.getSubject());
            System.out.println("Number of questions: " + quiz.getNumberOfQuestions());
            System.out.println("API Key present: " + (grokApiKey != null && !grokApiKey.trim().isEmpty()));
            
            String prompt = buildPrompt(quiz.getSubject(), quiz.getNumberOfQuestions());
            String rawResponse = callGrokAPI(prompt);
            System.out.println("API Response received. Length: " + rawResponse.length());
            System.out.println("Response preview: " + rawResponse.substring(0, Math.min(200, rawResponse.length())));

            // save raw response for debugging
            try {
                Path rawPath = Paths.get("last_grok_response.json");
                Files.writeString(rawPath, rawResponse, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                System.err.println("Failed to write raw Grok response to file: " + e.getMessage());
            }

            String content = extractGrokContentFromResponse(rawResponse);
            // save extracted content for debugging
            try {
                Path contentPath = Paths.get("last_grok_content.txt");
                Files.writeString(contentPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                System.err.println("Failed to write extracted Grok content to file: " + e.getMessage());
            }

            parseAndSaveQuestions(content, quiz);
        } catch (Exception e) {
            // If Grok API fails, generate mock questions as fallback
            System.err.println("Grok API failed, generating mock questions instead. Error: " + e.getMessage());
            e.printStackTrace();
            generateMockQuestions(quiz);
        }
    }
    
    private String buildPrompt(String subject, Integer numberOfQuestions) {
        return String.format(
            "Generate exactly %d multiple-choice quiz questions on the subject: %s\n" +
            "For each question, provide in the exact format below:\n" +
            "Question: <question text>\n" +
            "A) <option A>\n" +
            "B) <option B>\n" +
            "C) <option C>\n" +
            "D) <option D>\n" +
            "Correct: <A|B|C|D>\n" +
            "Explanation: <brief explanation>\n" +
            "---\n" +
            "Make sure the questions are varied, challenging, and educational. Each question must be independent.",
            numberOfQuestions, subject
        );
    }
    
    private String callGrokAPI(String prompt) throws Exception {
        if (grokApiKey == null || grokApiKey.trim().isEmpty() || grokApiKey.contains("YOUR_GROK_API_KEY")) {
            throw new IllegalArgumentException("Grok API key not configured properly. Please add your API key to application.properties");
        }
        
        System.out.println("Calling Grok API with key: " + grokApiKey.substring(0, Math.min(10, grokApiKey.length())) + "...");
        
        HttpClient client = HttpClient.newHttpClient();
        
        String requestBody = createGrokRequestBody(prompt);
        System.out.println("Request body created. Size: " + requestBody.length());
        System.out.println("Request body: " + requestBody.substring(0, Math.min(300, requestBody.length())) + "...");
        
        System.out.println("Calling URL: " + GROK_API_URL);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GROK_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + grokApiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("===== GROK API RESPONSE =====");
        System.out.println("Status code: " + response.statusCode());
        System.out.println("Response headers: " + response.headers());
        System.out.println("Response body: " + response.body());
        System.out.println("=============================");
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Grok API call failed with status: " + response.statusCode() + 
                "\nResponse: " + response.body());
        }
        
        return extractGrokContentFromResponse(response.body());
    }
    
    private String createGrokRequestBody(String prompt) throws Exception {
        Map<String, Object> requestMap = new HashMap<>();
        
        // Grok uses OpenAI-style format
        requestMap.put("model", "grok-beta");
        requestMap.put("temperature", 1.0);
        
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("role", "user");
        messageMap.put("content", prompt);
        
        requestMap.put("messages", Arrays.asList(messageMap));
        
        return objectMapper.writeValueAsString(requestMap);
    }
    
    private String extractGrokContentFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices")
            .get(0)
            .path("message")
            .path("content")
            .asText();
    }
    
    private void parseAndSaveQuestions(String content, Quiz quiz) {
        System.out.println("Parsing questions from API response...");
        String[] questionBlocks = content.split("---");
        System.out.println("Found " + questionBlocks.length + " question blocks");
        
        int questionNumber = 1;
        
        for (String block : questionBlocks) {
            if (block.trim().isEmpty()) continue;
            
            try {
                System.out.println("Parsing question block " + questionNumber);
                QuizQuestion question = parseQuestionBlock(block.trim(), quiz, questionNumber);
                if (question != null) {
                    questionRepository.save(question);
                    System.out.println("Saved question " + questionNumber);
                    questionNumber++;
                }
            } catch (Exception e) {
                System.err.println("Error parsing question: " + e.getMessage());
            }
            
            if (questionNumber > quiz.getNumberOfQuestions()) break;
        }
        
        System.out.println("Total questions saved: " + (questionNumber - 1));
        
        // If we couldn't parse enough questions, generate mock ones
        while (questionNumber <= quiz.getNumberOfQuestions()) {
            System.out.println("Generating mock question " + questionNumber);
            QuizQuestion mockQuestion = generateMockQuestion(quiz, questionNumber);
            questionRepository.save(mockQuestion);
            questionNumber++;
        }
    }
    
    private QuizQuestion parseQuestionBlock(String block, Quiz quiz, Integer questionNumber) {
        try {
            String[] lines = block.split("\n");
            QuizQuestion question = new QuizQuestion();
            question.setQuiz(quiz);
            question.setQuestionNumber(questionNumber);
            
            String questionText = "";
            String optionA = "";
            String optionB = "";
            String optionC = "";
            String optionD = "";
            String correctAnswer = "";
            String explanation = "";
            
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Question:")) {
                    questionText = line.substring("Question:".length()).trim();
                } else if (line.startsWith("A)")) {
                    optionA = line.substring(2).trim();
                } else if (line.startsWith("B)")) {
                    optionB = line.substring(2).trim();
                } else if (line.startsWith("C)")) {
                    optionC = line.substring(2).trim();
                } else if (line.startsWith("D)")) {
                    optionD = line.substring(2).trim();
                } else if (line.startsWith("Correct:")) {
                    correctAnswer = line.substring("Correct:".length()).trim();
                } else if (line.startsWith("Explanation:")) {
                    explanation = line.substring("Explanation:".length()).trim();
                }
            }
            
            if (!questionText.isEmpty() && !optionA.isEmpty()) {
                question.setQuestionText(questionText);
                question.setOptionA(optionA);
                question.setOptionB(!optionB.isEmpty() ? optionB : "N/A");
                question.setOptionC(!optionC.isEmpty() ? optionC : "N/A");
                question.setOptionD(!optionD.isEmpty() ? optionD : "N/A");
                question.setCorrectAnswer(!correctAnswer.isEmpty() ? correctAnswer : "A");
                question.setExplanation(!explanation.isEmpty() ? explanation : "");
                return question;
            }
        } catch (Exception e) {
            System.err.println("Error parsing question block: " + e.getMessage());
        }
        return null;
    }
    
    private void generateMockQuestions(Quiz quiz) {
        List<String> subjects = Arrays.asList(
            "Java", "Python", "JavaScript", "SQL", "HTML", "CSS", 
            "Spring Boot", "React", "Angular", "Database Design"
        );
        
        for (int i = 1; i <= quiz.getNumberOfQuestions(); i++) {
            QuizQuestion question = generateMockQuestion(quiz, i);
            questionRepository.save(question);
        }
    }
    
    private QuizQuestion generateMockQuestion(Quiz quiz, Integer questionNumber) {
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setQuestionNumber(questionNumber);
        question.setQuestionText(quiz.getSubject() + " - Question " + questionNumber + 
            ": What is concept " + questionNumber + " in " + quiz.getSubject() + "?");
        question.setOptionA("Option A for question " + questionNumber);
        question.setOptionB("Option B for question " + questionNumber);
        question.setOptionC("Option C for question " + questionNumber);
        question.setOptionD("Option D for question " + questionNumber);
        question.setCorrectAnswer("A");
        question.setExplanation("This is the explanation for question " + questionNumber);
        return question;
    }
    
    public Quiz getQuiz(Long quizId, User user) {
        return quizRepository.findByIdAndUser(quizId, user).orElse(null);
    }
    
    public List<QuizQuestion> getQuizQuestions(Quiz quiz) {
        return questionRepository.findByQuizOrderByQuestionNumber(quiz);
    }
    
    public void submitAnswer(QuizQuestion question, String answer) {
        question.setUserAnswer(answer);
        question.setIsCorrect(answer != null && answer.equals(question.getCorrectAnswer()));
        questionRepository.save(question);
    }
    
    public void completeQuiz(Quiz quiz) {
        List<QuizQuestion> questions = getQuizQuestions(quiz);
        int correctCount = 0;
        
        for (QuizQuestion q : questions) {
            if (q.getIsCorrect()) {
                correctCount++;
            }
        }
        
        quiz.setScore(correctCount);
        quiz.setCompleted(true);
        quizRepository.save(quiz);
    }
    
    public List<Quiz> getUserQuizzes(User user) {
        return quizRepository.findByUser(user);
    }
}
