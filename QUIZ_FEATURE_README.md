# Quiz Feature Documentation

## Overview
The Quiz Feature is an interactive learning tool integrated into the Open Library application. It allows users to:
- Select quiz subjects from 10+ different topics
- Choose the number of questions (10, 20, 30, 40, 50, or 100)
- Take dynamic quizzes powered by Google's Gemini AI API
- Get fresh questions every time they start a new quiz
- View detailed results with explanations
- Track quiz history and progress

## Features

### 1. Subject Selection
Users can select from various subjects:
- Java Programming
- Python Programming
- JavaScript
- SQL Databases
- Web Development
- Spring Boot
- React.js
- Database Design
- General Knowledge
- Cloud Computing

### 2. Question Count Options
Users can choose to take:
- 10 questions
- 20 questions
- 30 questions
- 40 questions
- 50 questions
- 100 questions

### 3. Dynamic Question Generation
Questions are generated using Google's Gemini 1.5 Pro API, ensuring:
- Fresh questions every time a quiz is started
- Varied difficulty levels
- Educational and relevant content
- Detailed explanations for each answer

### 4. Quiz Features
- **Progress Tracking**: Visual progress bar showing quiz completion
- **Navigation**: Users can move between questions freely
- **Answer Review**: View selected answers before submission
- **Results Dashboard**: Detailed breakdown of performance
- **Explanations**: Learn why each answer is correct

## Setup Instructions

### 1. Get Gemini API Key

1. Go to [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Sign in with your Google account
3. Click "Create API Key" button
4. Copy the API key

### 2. Configure Application

1. Open `src/main/resources/application.properties`
2. Find the line: `gemini.api.key=YOUR_GEMINI_API_KEY_HERE`
3. Replace `YOUR_GEMINI_API_KEY_HERE` with your actual API key
4. Save the file

### 3. Run the Application

```bash
mvn clean install
mvn spring-boot:run
```

Or using the Maven wrapper:
```bash
./mvnw clean install
./mvnw spring-boot:run
```

The application will start on `http://localhost:1010`

## Database Schema

### Quiz Table
```sql
CREATE TABLE quizzes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject VARCHAR(255) NOT NULL,
    number_of_questions INT NOT NULL,
    score INT DEFAULT 0,
    completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    user_id BIGINT
);
```

### QuizQuestion Table
```sql
CREATE TABLE quiz_questions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quiz_id BIGINT NOT NULL,
    question_text LONGTEXT NOT NULL,
    option_a LONGTEXT NOT NULL,
    option_b LONGTEXT NOT NULL,
    option_c LONGTEXT NOT NULL,
    option_d LONGTEXT NOT NULL,
    correct_answer VARCHAR(1) NOT NULL,
    explanation TEXT,
    user_answer VARCHAR(1),
    question_number INT NOT NULL,
    is_correct BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (quiz_id) REFERENCES quizzes(id)
);
```

## API Endpoints

### 1. Get Quiz Page
```
GET /quiz
```
Returns the quiz selection page with subject and question count options.

### 2. Start Quiz
```
GET /quiz/start?subject=Java Programming&questions=10
```
Creates a new quiz and displays questions.

Parameters:
- `subject`: Subject for the quiz
- `questions`: Number of questions (10, 20, 30, 40, 50, 100)

### 3. Submit Answer
```
POST /quiz/submit-answer
Content-Type: application/json

{
    "questionId": 1,
    "answer": "A"
}
```

### 4. Complete Quiz
```
POST /quiz/complete/{quizId}
```
Submits the completed quiz and calculates score.

### 5. Get Results
```
GET /quiz/result/{quizId}
```
Returns the detailed results page for a completed quiz.

### 6. Get Quiz History
```
GET /quiz/history
```
Returns the user's quiz history and statistics.

## Frontend Pages

### 1. `/quiz` - Quiz Selection Page
- Subject dropdown with 10 subjects
- Question count selector
- Option to view quiz history
- Beautiful gradient UI with smooth animations

### 2. `/quiz/start` - Quiz Taking Page
- Question display with progress bar
- 4 multiple-choice options (A, B, C, D)
- Previous/Next navigation buttons
- Real-time progress tracking
- Question counter

### 3. `/quiz/result/{quizId}` - Results Page
- Overall score display with percentage
- Performance breakdown (Correct/Total/Accuracy)
- Performance feedback based on score
- Detailed answer review
- Explanations for each question
- Options to retake quiz or return home

### 4. `/quiz/history` - Quiz History Page
- Grid view of all quizzes
- Filter by status (All/Completed/In Progress)
- Statistics dashboard
- Quick access to results or continue quiz

## Error Handling

### Fallback Mechanism
If the Gemini API fails:
1. The system will use mock questions as fallback
2. Questions will still be generated for the selected subject
3. User experience remains uninterrupted

### API Key Missing
If no API key is configured:
- The application will log an error
- Mock questions will be generated automatically
- Consider this for development/testing

## Scoring System

- Each correct answer = 1 point
- Unanswered questions = 0 points
- Score is displayed as:
  - Raw score: X/Total Questions
  - Percentage: X%

### Performance Levels
- **90%+**: 🏆 Excellent - Exceptional understanding
- **70-89%**: 🎊 Good Job - Solid knowledge
- **50-69%**: 📚 Acceptable - Basic understanding
- **Below 50%**: 💪 Keep Learning - Continue studying

## File Structure

```
src/main/java/com/project/openlibrary/
├── model/
│   ├── Quiz.java
│   └── QuizQuestion.java
├── repository/
│   ├── QuizRepository.java
│   └── QuestionRepository.java
├── controller/
│   └── QuizController.java
└── service/
    └── QuizService.java

src/main/resources/
├── templates/
│   ├── quiz.html
│   ├── quiz-questions.html
│   ├── quiz-result.html
│   └── quiz-history.html
└── application.properties
```

## Technologies Used

1. **Backend**
   - Spring Boot 3.2.1
   - Spring Data JPA
   - MySQL Database
   - Google Generative AI (Gemini) API
   - Jackson for JSON processing

2. **Frontend**
   - HTML5
   - CSS3 (with Flexbox and Grid)
   - JavaScript (Vanilla)
   - Thymeleaf Template Engine
   - Bootstrap (optional utility classes)

3. **Database**
   - MySQL
   - Hibernate ORM

## Future Enhancements

1. **Timed Quizzes**: Add countdown timer
2. **Question Categories**: Sub-categories within subjects
3. **Difficulty Levels**: Easy, Medium, Hard
4. **Leaderboard**: User rankings
5. **Question Banking**: Save frequently used questions
6. **PDF Export**: Export quiz results as PDF
7. **Mobile App**: Native mobile application
8. **Social Features**: Share results, compete with friends
9. **Analytics Dashboard**: Detailed learning analytics
10. **Question Review**: Bookmark questions for later review

## Troubleshooting

### Issue: "Gemini API key not configured"
**Solution**: Ensure `gemini.api.key` is set in `application.properties` with a valid API key.

### Issue: "HTTP 429 - Too Many Requests"
**Solution**: Gemini API has rate limits. Wait a few moments before retrying.

### Issue: Questions not generating
**Solution**: Check your internet connection and API key validity. The system will fallback to mock questions.

### Issue: Database errors
**Solution**: Ensure MySQL is running and the database `openlibrarysystem` exists with proper credentials.

## Best Practices

1. **API Key Security**: Never commit API keys to version control
2. **Usage Limits**: Monitor Gemini API usage to avoid unexpected costs
3. **Question Quality**: Review feedback to improve question generation
4. **User Testing**: Test quiz feature with different question counts
5. **Performance**: Cache frequently used subjects and questions

## Support & Documentation

For more information:
- [Google Generative AI Documentation](https://ai.google.dev/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Thymeleaf Documentation](https://www.thymeleaf.org/)

## License

This quiz feature is part of the Open Library project and follows the same license.

---

**Version**: 1.0.0  
**Last Updated**: February 2026  
**Author**: Open Library Development Team
