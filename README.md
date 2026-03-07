# 📚 Open Library — Smart Library Management System

A full-featured **Library Management System** built with **Spring Boot**, **MySQL**, and **Thymeleaf** — featuring AI-generated quizzes, user/admin dashboards, book management, and more.

---

## ✨ Features

### 👤 User Features
- **User Registration & Login** — Secure authentication with password encryption
- **User Dashboard** — Browse, search and read books
- **Book Reader** — In-browser PDF/book reader
- **AI Quiz Module** — Take randomized quizzes on programming topics with detailed results
- **Quiz History** — Review all past quiz attempts and scores
- **User Profile** — Manage personal information

### 🛡️ Admin Features
- **Admin Dashboard** — Overview of all library data
- **Book Management** — Add, update, and delete books
- **Author Management** — Add, update, and delete authors
- **User Management** — View and manage registered users
- **Admin Login** — Separate secure admin portal

### 🧠 AI Quiz Feature
- 10 subject areas: Java, Python, JavaScript, SQL, Web Development, Spring Boot, React.js, Database Design, General Knowledge, Cloud Computing
- Flexible question count: 10, 20, 30, 40, 50, or 100 questions
- Fresh, randomized questions generated every quiz session
- **Optional Grok AI integration** — real AI-generated questions if API key is configured; rich randomized question bank as fallback
- Detailed results with explanations for each answer
- Score tracking and quiz history

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.2.1 |
| **ORM** | Spring Data JPA, Hibernate |
| **Database** | MySQL 8.x |
| **Frontend** | Thymeleaf, HTML5, CSS3, Bootstrap 5 |
| **Security** | Spring Security Crypto (BCrypt) |
| **AI Integration** | Grok API (xAI) via Java HttpClient |
| **Build Tool** | Maven |
| **Dev Server Port** | 1010 |

---

## 📋 Prerequisites

Before running the project, make sure you have the following installed:

- **Java 17+** — [Download](https://adoptium.net/)
- **Maven 3.6+** — [Download](https://maven.apache.org/download.cgi)
- **MySQL 8.x** — [Download](https://dev.mysql.com/downloads/mysql/)
- **Git** (optional)

---

## 🚀 Setup & Installation

### 1. Clone the Repository

```bash
git clone https://github.com/Subhu0528/Open-Library.git
cd Open-Library
```

### 2. Set Up the Database

Open MySQL Workbench or MySQL CLI and run:

```sql
CREATE DATABASE openlibrarysystem;
```

> The tables are auto-created by Hibernate on first startup (`ddl-auto=update`).

### 3. Configure Application Properties

Open `src/main/resources/application.properties` and update your database credentials:

```properties
# Server port
server.port=1010

# MySQL database connection
spring.datasource.url=jdbc:mysql://localhost:3306/openlibrarysystem
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD

# Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Grok AI (optional — see section below)
grok.api.key=YOUR_GROK_API_KEY_HERE
```

### 4. (Optional) Configure Grok AI for Quiz Questions

The quiz module works **out of the box without an API key** using a built-in randomized question bank.

To enable AI-generated questions:
1. Sign up at [https://platform.x.ai/](https://platform.x.ai/)
2. Generate an API key
3. Replace `YOUR_GROK_API_KEY_HERE` in `application.properties`

### 5. Build and Run

```bash
# Using Maven Wrapper (recommended)
./mvnw spring-boot:run

# Or on Windows:
mvnw.cmd spring-boot:run
```

The application will start at **[http://localhost:1010](http://localhost:1010)**

---

## 🖥️ Application URLs

| Page | URL |
|------|-----|
| Home / Landing | `http://localhost:1010/` |
| User Login | `http://localhost:1010/user_login_page` |
| User Registration | `http://localhost:1010/registerPage` |
| User Dashboard | `http://localhost:1010/userDashBoard` |
| **Quiz** | `http://localhost:1010/quiz` |
| Quiz History | `http://localhost:1010/quiz/history` |
| Admin Login | `http://localhost:1010/admin_login_page` |
| Admin Dashboard | `http://localhost:1010/adminDashboard` |

---

## 📁 Project Structure

```
Open-Library/
├── src/
│   ├── main/
│   │   ├── java/com/project/openlibrary/
│   │   │   ├── controller/
│   │   │   │   ├── AdminController.java
│   │   │   │   ├── AuthorController.java
│   │   │   │   ├── BookController.java
│   │   │   │   ├── QuizController.java      ← Quiz endpoints
│   │   │   │   └── UserController.java
│   │   │   ├── model/
│   │   │   │   ├── Admin.java
│   │   │   │   ├── Author.java
│   │   │   │   ├── Book.java
│   │   │   │   ├── Quiz.java               ← Quiz session entity
│   │   │   │   ├── QuizQuestion.java       ← Individual question entity
│   │   │   │   └── User.java
│   │   │   ├── repository/
│   │   │   │   ├── QuizRepository.java
│   │   │   │   ├── QuestionRepository.java
│   │   │   │   └── ...
│   │   │   ├── service/
│   │   │   │   ├── QuizService.java        ← AI quiz generation logic
│   │   │   │   └── ...
│   │   │   └── SpringbootOpenLibraryApplication.java
│   │   └── resources/
│   │       ├── templates/
│   │       │   ├── quiz.html               ← Quiz selection page
│   │       │   ├── quiz-questions.html     ← Active quiz UI
│   │       │   ├── quiz-result.html        ← Results page
│   │       │   ├── quiz-history.html       ← Quiz history
│   │       │   ├── index.html              ← Landing page
│   │       │   ├── userDashBoard.html
│   │       │   ├── adminDashboard.html
│   │       │   └── ...
│   │       ├── static/
│   │       │   └── css/                    ← Bootstrap & custom CSS
│   │       └── application.properties
│   └── test/
├── pom.xml
└── README.md
```

---

## 🎯 Quiz Feature — How It Works

```
User visits /quiz
      │
      ▼
Selects Subject + Question Count
      │
      ▼
GET /quiz/start  ──► QuizService.createQuiz()
                         │
                         ├── Grok API configured?
                         │      YES ──► Call Grok API for AI questions
                         │      NO  ──► Use randomized question bank
                         │
                         └── Save Quiz + Questions to Database
      │
      ▼
quiz-questions.html  (answer all questions)
      │
      ▼
POST /quiz/complete/{id}  ──► Score calculated & saved
      │
      ▼
GET /quiz/result/{id}  ──► quiz-result.html
      │
      ▼
"Retake Quiz" → New quiz with FRESH randomized questions
```

### 📝 Subjects Available

| # | Subject |
|---|---------|
| 1 | Java Programming |
| 2 | Python Programming |
| 3 | JavaScript |
| 4 | SQL Databases |
| 5 | Web Development |
| 6 | Spring Boot |
| 7 | React.js |
| 8 | Database Design |
| 9 | General Knowledge |
| 10 | Cloud Computing |

---

## 🔑 Default Credentials

> ⚠️ Change these immediately in a production environment!

The application uses database-stored credentials. Register a user via `/registerPage` and create an admin via the admin panel or directly in the database.

---

## 🧪 Running Tests

```bash
./mvnw test
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

This project is open source. Feel free to fork, modify, and distribute.

---

## 👨‍💻 Author

**Subhu0528** — [GitHub](https://github.com/Subhu0528)

---

> Built with ❤️ using Spring Boot | Powered by Grok AI (optional)
