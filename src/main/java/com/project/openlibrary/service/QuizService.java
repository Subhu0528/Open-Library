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
import java.util.*;

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
            // callGrokAPI now returns the raw content string (already extracted)
            String content = callGrokAPI(prompt);
            System.out.println("API content received. Length: " + content.length());
            System.out.println("Content preview: " + content.substring(0, Math.min(200, content.length())));

            parseAndSaveQuestions(content, quiz);
        } catch (Exception e) {
            System.err.println("Grok API failed, generating randomized questions instead. Error: " + e.getMessage());
            generateRandomizedQuestions(quiz);
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
                numberOfQuestions, subject);
    }

    private String callGrokAPI(String prompt) throws Exception {
        if (grokApiKey == null || grokApiKey.trim().isEmpty() || grokApiKey.contains("YOUR_GROK_API_KEY")) {
            throw new IllegalArgumentException(
                    "Grok API key not configured. Please add your API key to application.properties");
        }

        System.out.println(
                "Calling Grok API with key: " + grokApiKey.substring(0, Math.min(10, grokApiKey.length())) + "...");

        HttpClient client = HttpClient.newHttpClient();
        String requestBody = createGrokRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROK_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + grokApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Grok API status: " + response.statusCode());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Grok API call failed with status: " + response.statusCode() +
                    "\nResponse: " + response.body());
        }

        // Extract and return the text content only once
        return extractGrokContentFromResponse(response.body());
    }

    private String createGrokRequestBody(String prompt) throws Exception {
        Map<String, Object> requestMap = new HashMap<>();
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

        // Fill remaining with randomized questions if API didn't provide enough
        if (questionNumber <= quiz.getNumberOfQuestions()) {
            List<QuizQuestion> extras = buildRandomizedPool(quiz.getSubject(), quiz.getNumberOfQuestions());
            int extraIdx = 0;
            while (questionNumber <= quiz.getNumberOfQuestions() && extraIdx < extras.size()) {
                QuizQuestion q = extras.get(extraIdx++);
                q.setQuiz(quiz);
                q.setQuestionNumber(questionNumber++);
                questionRepository.save(q);
            }
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

    // -------------------------------------------------------------------------
    // Randomized question bank — called when Grok API is unavailable
    // -------------------------------------------------------------------------

    private void generateRandomizedQuestions(Quiz quiz) {
        List<QuizQuestion> pool = buildRandomizedPool(quiz.getSubject(), quiz.getNumberOfQuestions());
        for (int i = 0; i < quiz.getNumberOfQuestions(); i++) {
            QuizQuestion q = pool.get(i % pool.size());
            q.setQuiz(quiz);
            q.setQuestionNumber(i + 1);
            questionRepository.save(q);
        }
        System.out.println("Generated " + quiz.getNumberOfQuestions() + " randomized questions for: " + quiz.getSubject());
    }

    /**
     * Builds a shuffled list of QuizQuestion objects (without quiz/questionNumber set)
     * from a rich, subject-specific question bank.  If the bank has fewer entries than
     * needed, questions are cycled with answer-option rotation so they feel different.
     */
    private List<QuizQuestion> buildRandomizedPool(String subject, int needed) {
        List<QuizQuestion> bank = getQuestionBank(subject);

        // Shuffle so every call produces a different order
        Collections.shuffle(bank, new Random());

        // If we need more than the bank holds, rotate options to create variety
        List<QuizQuestion> pool = new ArrayList<>(bank);
        while (pool.size() < needed) {
            for (QuizQuestion original : bank) {
                if (pool.size() >= needed) break;
                pool.add(rotateOptions(original));
            }
        }
        return pool;
    }

    /** Creates a copy of the question with options rotated (A→B→C→D→A) and correct answer updated. */
    private QuizQuestion rotateOptions(QuizQuestion q) {
        QuizQuestion copy = new QuizQuestion();
        // Rotate: new A = old B, new B = old C, new C = old D, new D = old A
        copy.setQuestionText(q.getQuestionText());
        copy.setOptionA(q.getOptionB());
        copy.setOptionB(q.getOptionC());
        copy.setOptionC(q.getOptionD());
        copy.setOptionD(q.getOptionA());
        copy.setExplanation(q.getExplanation());

        // Update correct answer after rotation
        Map<String, String> rotate = new HashMap<>();
        rotate.put("A", "D"); // old A is now D
        rotate.put("B", "A"); // old B is now A
        rotate.put("C", "B");
        rotate.put("D", "C");
        copy.setCorrectAnswer(rotate.getOrDefault(q.getCorrectAnswer(), "A"));
        return copy;
    }

    private List<QuizQuestion> getQuestionBank(String subject) {
        return switch (subject) {
            case "Java Programming" -> javaQuestions();
            case "Python Programming" -> pythonQuestions();
            case "JavaScript" -> javascriptQuestions();
            case "SQL Databases" -> sqlQuestions();
            case "Web Development" -> webDevQuestions();
            case "Spring Boot" -> springBootQuestions();
            case "React.js" -> reactQuestions();
            case "Database Design" -> databaseDesignQuestions();
            case "General Knowledge" -> generalKnowledgeQuestions();
            case "Cloud Computing" -> cloudComputingQuestions();
            default -> genericQuestions(subject);
        };
    }

    // ---- Java ----------------------------------------------------------------
    private List<QuizQuestion> javaQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What is the default value of an int variable in Java?",
                "0", "null", "-1", "undefined", "A",
                "In Java, the default value for numeric types like int is 0."),
            makeQ("Which keyword is used to prevent a class from being subclassed in Java?",
                "static", "abstract", "final", "sealed", "C",
                "The 'final' keyword prevents a class from being extended."),
            makeQ("What does JVM stand for?",
                "Java Virtual Machine", "Java Variable Method", "Java Version Manager", "Just-in-Time Virtual Machine", "A",
                "JVM stands for Java Virtual Machine, which executes Java bytecode."),
            makeQ("Which collection type does NOT allow duplicate elements in Java?",
                "ArrayList", "LinkedList", "HashSet", "Vector", "C",
                "HashSet implements the Set interface which does not allow duplicates."),
            makeQ("What is the output of: System.out.println(10 / 3) in Java?",
                "3.33", "3", "4", "Compilation error", "B",
                "Integer division in Java truncates the decimal part, giving 3."),
            makeQ("Which interface must be implemented by a class to be used in a for-each loop?",
                "Serializable", "Comparable", "Iterable", "Cloneable", "C",
                "The Iterable interface provides the iterator() method used by for-each."),
            makeQ("What is autoboxing in Java?",
                "Converting a String to int", "Automatic conversion between primitive and wrapper types",
                "Memory allocation for arrays", "A design pattern", "B",
                "Autoboxing is the automatic conversion between primitive types and their wrapper classes."),
            makeQ("Which access modifier makes a member visible only within its own class?",
                "protected", "package-private", "private", "public", "C",
                "'private' restricts access to within the declaring class."),
            makeQ("What is the purpose of the 'super' keyword in Java?",
                "Calls the current class constructor", "Refers to the parent class", "Creates a new object", "Declares a constant", "B",
                "'super' is used to refer to the parent class, access its methods and constructors."),
            makeQ("Which Java feature allows a method to have multiple signatures with different parameters?",
                "Overriding", "Overloading", "Inheritance", "Polymorphism", "B",
                "Method overloading allows multiple methods with the same name but different parameters."),
            makeQ("What does the 'volatile' keyword do in Java?",
                "Makes a variable constant", "Ensures a variable is always read from main memory",
                "Prevents multithreading", "Speeds up variable access", "B",
                "'volatile' ensures the variable is read/written directly to main memory, not CPU cache."),
            makeQ("Which exception is thrown when you access an array with an out-of-bound index?",
                "NullPointerException", "IllegalArgumentException", "ArrayIndexOutOfBoundsException", "ClassCastException", "C",
                "ArrayIndexOutOfBoundsException is thrown when accessing an invalid array index."),
            makeQ("What is the difference between == and .equals() in Java?",
                "No difference", "== compares references; .equals() compares content",
                "== compares content; .equals() compares references", ".equals() only works on primitives", "B",
                "== compares object references; .equals() compares the actual content/values."),
            makeQ("Which Java collection maintains insertion order and allows duplicates?",
                "HashSet", "TreeSet", "HashMap", "ArrayList", "D",
                "ArrayList maintains insertion order and allows duplicate elements."),
            makeQ("What is a lambda expression in Java?",
                "A type of loop", "An anonymous function", "A new data type", "A compiler directive", "B",
                "Lambda expressions are anonymous functions introduced in Java 8, enabling functional programming."),
            makeQ("Which method is the entry point of a Java program?",
                "start()", "run()", "main()", "init()", "C",
                "The main() method is the standard entry point for a Java program."),
            makeQ("What is the purpose of the 'try-with-resources' statement?",
                "Catching runtime exceptions", "Automatic resource management (auto-close)", "Creating multiple threads", "Handling null values", "B",
                "try-with-resources automatically closes resources like streams when the block exits."),
            makeQ("Which Java keyword is used to create an object?",
                "create", "allocate", "new", "object", "C",
                "The 'new' keyword allocates memory and creates a new object instance."),
            makeQ("What is a checked exception in Java?",
                "An exception thrown at compile time", "An exception the compiler forces you to handle",
                "A runtime error", "An exception from the JVM", "B",
                "Checked exceptions must be declared in the method signature or caught in a try-catch block."),
            makeQ("What does the 'static' keyword mean for a class method?",
                "It can only be called once", "It belongs to the class, not an instance",
                "It cannot take parameters", "It runs on a separate thread", "B",
                "Static methods belong to the class itself and can be called without creating an instance.")
        ));
    }

    // ---- Python ----------------------------------------------------------------
    private List<QuizQuestion> pythonQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What is the output of: type([]) in Python?",
                "<class 'list'>", "<class 'array'>", "<class 'tuple'>", "None", "A",
                "[] is a list literal, so type([]) returns <class 'list'>."),
            makeQ("Which Python keyword is used to define a function?",
                "function", "define", "def", "func", "C",
                "The 'def' keyword is used to define a function in Python."),
            makeQ("What does PEP 8 refer to in Python?",
                "A Python version", "The Python style guide", "A package manager", "A testing framework", "B",
                "PEP 8 is Python's official style guide for writing clean, readable code."),
            makeQ("What is the difference between a list and a tuple in Python?",
                "Lists are ordered, tuples are not", "Tuples are mutable, lists are not",
                "Lists are mutable, tuples are immutable", "No difference", "C",
                "Lists are mutable (can be changed); tuples are immutable (cannot be changed after creation)."),
            makeQ("Which built-in function returns the length of a list?",
                "size()", "count()", "length()", "len()", "D",
                "len() is the built-in function to get the number of items in a list."),
            makeQ("What does 'self' represent in a Python class method?",
                "The class itself", "The parent class", "The current instance of the class", "A global variable", "C",
                "'self' refers to the current instance of the class, allowing access to its attributes and methods."),
            makeQ("Which operator is used for floor division in Python?",
                "/", "%", "//", "**", "C",
                "// performs floor division, returning the largest integer less than or equal to the result."),
            makeQ("What is a Python decorator?",
                "A comment style", "A function that modifies another function", "A loop construct", "A data type", "B",
                "Decorators are functions that wrap other functions to extend or modify their behavior."),
            makeQ("What is the output of: 'hello'[::-1]?",
                "hello", "olleh", "Error", "h", "B",
                "[::-1] reverses the string, so 'hello' becomes 'olleh'."),
            makeQ("Which method adds an element to the end of a list in Python?",
                "add()", "insert()", "append()", "push()", "C",
                "The append() method adds an element to the end of a list."),
            makeQ("What does the 'yield' keyword do in Python?",
                "Exits a function", "Returns a value and pauses execution (generator)", "Imports a module", "Defines a class", "B",
                "'yield' is used in generator functions to return values one at a time, pausing execution between calls."),
            makeQ("What is a dictionary in Python?",
                "An ordered list", "A set of unique values", "A collection of key-value pairs", "A type of loop", "C",
                "A dictionary is an unordered collection of key-value pairs with unique keys."),
            makeQ("Which statement correctly creates a set in Python?",
                "s = []", "s = ()", "s = {}", "s = set()", "D",
                "{} creates an empty dict; set() is used to create an empty set."),
            makeQ("What is list comprehension in Python?",
                "A method to copy lists", "A concise way to create lists using a single expression",
                "A sorting algorithm", "A way to import lists", "B",
                "List comprehension provides a concise syntax to create lists: [x*2 for x in range(5)]."),
            makeQ("What does __init__ do in a Python class?",
                "Destroys an object", "Imports a module", "Initializes a new object (constructor)",
                "Defines a static method", "C",
                "__init__ is the constructor method called when a new object is created.")
        ));
    }

    // ---- JavaScript ----------------------------------------------------------------
    private List<QuizQuestion> javascriptQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What does 'typeof null' return in JavaScript?",
                "'null'", "'undefined'", "'object'", "'boolean'", "C",
                "This is a well-known JavaScript bug — typeof null returns 'object' instead of 'null'."),
            makeQ("Which method converts a JSON string to a JavaScript object?",
                "JSON.stringify()", "JSON.parse()", "JSON.convert()", "JSON.decode()", "B",
                "JSON.parse() converts a JSON string into a JavaScript object."),
            makeQ("What is the difference between 'let' and 'var' in JavaScript?",
                "No difference", "let is function-scoped; var is block-scoped",
                "var is function-scoped; let is block-scoped", "let is for numbers only", "C",
                "var is function-scoped and hoisted; let is block-scoped and not hoisted."),
            makeQ("What does the '===' operator check in JavaScript?",
                "Value only", "Type only", "Both value and type (strict equality)", "Reference equality", "C",
                "=== is strict equality — it checks both value AND type without type coercion."),
            makeQ("Which method is used to add an element to the end of an array in JavaScript?",
                "push()", "pop()", "shift()", "append()", "A",
                "The push() method adds one or more elements to the end of an array."),
            makeQ("What is a Promise in JavaScript?",
                "A function that runs synchronously", "An object representing the eventual completion of an async operation",
                "A type of loop", "A CSS property", "B",
                "A Promise represents a future value from an asynchronous operation and can be pending, fulfilled, or rejected."),
            makeQ("What is the purpose of 'async/await' in JavaScript?",
                "To write synchronous code faster",
                "To write asynchronous code that looks synchronous",
                "To create multi-threaded programs", "To handle CSS animations", "B",
                "async/await is syntactic sugar over Promises, making async code easier to read and write."),
            makeQ("What does 'closure' mean in JavaScript?",
                "A way to close the browser window",
                "A function that retains access to its outer scope even after the outer function returns",
                "A method to end a loop", "A CSS property", "B",
                "Closures allow inner functions to remember and access variables from their outer function's scope."),
            makeQ("What is the event loop in JavaScript?",
                "A special type of for loop", "A mechanism that handles asynchronous callbacks",
                "A built-in timer", "A way to iterate DOM elements", "B",
                "The event loop continuously checks the call stack and callback queue to handle async operations."),
            makeQ("Which method removes the last element of an array and returns it?",
                "push()", "shift()", "pop()", "splice()", "C",
                "pop() removes and returns the last element of an array."),
            makeQ("What does 'hoisting' mean in JavaScript?",
                "Moving code to the server", "Variable/function declarations are moved to the top of their scope",
                "Adding event listeners", "Creating a new scope", "B",
                "Hoisting moves var declarations and function definitions to the top of their scope before execution."),
            makeQ("What is the spread operator (...) used for in JavaScript?",
                "Multiplying arrays", "Expanding iterable elements into individual items",
                "Creating loops", "Declaring variables", "B",
                "The spread operator expands arrays or objects into individual elements: [...arr1, ...arr2]."),
            makeQ("What does 'NaN' stand for in JavaScript?",
                "No Array Notation", "Not a Number", "Null and Numeric", "Node and Network", "B",
                "NaN stands for 'Not a Number' and is returned when a mathematical operation fails."),
            makeQ("Which JavaScript method is used to iterate over array elements?",
                "loop()", "for()", "forEach()", "iterate()", "C",
                "forEach() executes a provided function once for each array element."),
            makeQ("What is destructuring in JavaScript?",
                "Deleting objects", "Extracting values from arrays or objects into variables",
                "Building new arrays", "A way to destroy unused code", "B",
                "Destructuring lets you unpack values from arrays or properties from objects into distinct variables.")
        ));
    }

    // ---- SQL ----------------------------------------------------------------
    private List<QuizQuestion> sqlQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("Which SQL statement is used to retrieve data from a database?",
                "INSERT", "UPDATE", "SELECT", "DELETE", "C",
                "SELECT is the SQL command used to query and retrieve data from a database."),
            makeQ("What does the WHERE clause do in SQL?",
                "Groups rows", "Filters rows based on a condition", "Sorts results", "Joins tables", "B",
                "The WHERE clause filters records that satisfy the specified condition."),
            makeQ("What is a PRIMARY KEY in SQL?",
                "A key that can be null", "A column that uniquely identifies each row", "A foreign table reference", "An index type", "B",
                "A PRIMARY KEY uniquely identifies each record in a table and cannot be NULL."),
            makeQ("Which SQL clause is used to sort query results?",
                "GROUP BY", "ORDER BY", "SORT BY", "HAVING", "B",
                "ORDER BY sorts the result set in ascending (ASC) or descending (DESC) order."),
            makeQ("What does 'INNER JOIN' do in SQL?",
                "Returns all rows from both tables", "Returns only matching rows from both tables",
                "Returns unmatched rows", "Deletes duplicate rows", "B",
                "INNER JOIN returns only rows where there is a match in both tables."),
            makeQ("What is the difference between DELETE and TRUNCATE in SQL?",
                "No difference", "DELETE removes specific rows; TRUNCATE removes all rows quickly",
                "TRUNCATE supports WHERE clause", "DELETE cannot be rolled back", "B",
                "DELETE removes rows conditionally with WHERE; TRUNCATE removes all rows without logging individual deletes."),
            makeQ("Which aggregate function counts the number of rows?",
                "SUM()", "AVG()", "COUNT()", "MAX()", "C",
                "COUNT() returns the total number of rows matching the condition."),
            makeQ("What does 'GROUP BY' do in SQL?",
                "Sorts data alphabetically", "Groups rows with the same values for aggregate functions",
                "Filters data", "Joins tables", "B",
                "GROUP BY groups rows that share a value so aggregate functions can be applied per group."),
            makeQ("What is a FOREIGN KEY in SQL?",
                "A key that hashes data", "A column referencing the PRIMARY KEY of another table",
                "A key for encryption", "An auto-incrementing column", "B",
                "A FOREIGN KEY links two tables by referencing the PRIMARY KEY of another table."),
            makeQ("Which SQL command creates a new table?",
                "NEW TABLE", "ADD TABLE", "CREATE TABLE", "MAKE TABLE", "C",
                "CREATE TABLE is the SQL DDL command to create a new table in the database."),
            makeQ("What does the HAVING clause do in SQL?",
                "Filters individual rows", "Filters groups created by GROUP BY",
                "Sorts grouped results", "Creates indexes", "B",
                "HAVING filters groups after GROUP BY, similar to WHERE but for aggregated data."),
            makeQ("What is a SQL VIEW?",
                "A physical copy of a table", "A virtual table based on a SELECT query",
                "A backup of the database", "A type of index", "B",
                "A VIEW is a stored SQL query that acts like a virtual table."),
            makeQ("Which SQL function returns the current date and time?",
                "DATE()", "CURRENT()", "NOW()", "TIME()", "C",
                "NOW() returns the current date and time in SQL."),
            makeQ("What does DISTINCT do in a SELECT statement?",
                "Removes NULL values", "Returns only unique values, eliminating duplicates",
                "Counts unique rows", "Sorts results", "B",
                "SELECT DISTINCT eliminates duplicate rows from the result set."),
            makeQ("What is an INDEX in SQL used for?",
                "Encrypting data", "Speeding up data retrieval operations",
                "Joining tables", "Backing up a database", "B",
                "Indexes improve query performance by allowing faster data lookup.")
        ));
    }

    // ---- Web Development ----------------------------------------------------------------
    private List<QuizQuestion> webDevQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What does HTML stand for?",
                "Hyper Text Markup Language", "High Transfer Markup Language",
                "Hyper Transfer Meta Link", "Home Tool Markup Language", "A",
                "HTML stands for HyperText Markup Language, the standard for web page structure."),
            makeQ("Which CSS property is used to change the text color?",
                "font-color", "text-color", "color", "foreground", "C",
                "The 'color' CSS property sets the color of text."),
            makeQ("What does CSS stand for?",
                "Creative Style Sheets", "Cascading Style Sheets", "Computer Style System", "Colorful Sheet Syntax", "B",
                "CSS stands for Cascading Style Sheets, used to style HTML elements."),
            makeQ("Which HTTP method is used to submit form data to a server?",
                "GET", "PUT", "POST", "DELETE", "C",
                "POST is used to send data to the server, commonly used for form submissions."),
            makeQ("What is the purpose of the <head> tag in HTML?",
                "Displays the page title on screen", "Contains metadata and resources not displayed directly",
                "Creates a navigation menu", "Defines the page header", "B",
                "The <head> element contains metadata like title, links to stylesheets, and scripts."),
            makeQ("What is responsive web design?",
                "Making websites respond to user clicks faster",
                "Designing websites that adapt to different screen sizes and devices",
                "Creating interactive animations", "A specific CSS framework", "B",
                "Responsive design ensures websites look good and work well on all devices and screen sizes."),
            makeQ("What does the 'alt' attribute in an <img> tag provide?",
                "Image alignment", "Alternative text for accessibility and broken images",
                "Image animation", "Image size", "B",
                "The alt attribute provides descriptive text when an image cannot be displayed and aids accessibility."),
            makeQ("Which CSS display value makes elements appear in a flexible container?",
                "block", "inline", "flex", "grid", "C",
                "display: flex enables the Flexbox layout model for flexible container layouts."),
            makeQ("What is the purpose of the <semantic> elements in HTML5?",
                "Improve page loading speed", "Give meaning to the structure of web content",
                "Add animations", "Create database connections", "B",
                "Semantic elements like <article>, <nav>, <header> describe the meaning of content, improving SEO and accessibility."),
            makeQ("What is a CDN in web development?",
                "Central Design Network", "Content Delivery Network", "Code Distribution Node", "Client Data Network", "B",
                "A CDN distributes content across multiple servers geographically to improve loading speeds."),
            makeQ("Which CSS property controls the space between elements outside their border?",
                "padding", "spacing", "margin", "border-spacing", "C",
                "The 'margin' property controls the space outside an element's border."),
            makeQ("What does the <canvas> element in HTML5 allow you to do?",
                "Display tables", "Draw 2D graphics using JavaScript", "Embed videos", "Create forms", "B",
                "<canvas> provides an area for drawing 2D graphics dynamically using JavaScript."),
            makeQ("What is localStorage in a web browser?",
                "Server-side storage", "A way to store data in the browser with no expiration",
                "Session-only storage", "Cookie-based storage", "B",
                "localStorage stores key-value data in the browser persistently, surviving browser restarts."),
            makeQ("What does API stand for in web development?",
                "Application Program Interface", "Application Programming Interface",
                "Automated Protocol Integration", "Adaptive Program Interface", "B",
                "API stands for Application Programming Interface, defining how software components interact."),
            makeQ("Which tag is used to link a CSS file to an HTML page?",
                "<style>", "<script>", "<link>", "<css>", "C",
                "The <link> tag in <head> connects external CSS stylesheets to an HTML document.")
        ));
    }

    // ---- Spring Boot ----------------------------------------------------------------
    private List<QuizQuestion> springBootQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What is Spring Boot?",
                "A database framework", "An opinionated framework for building Spring applications with minimal configuration",
                "A front-end JavaScript library", "A build tool", "B",
                "Spring Boot simplifies Spring app development with auto-configuration and embedded servers."),
            makeQ("Which annotation marks a class as a Spring Boot application entry point?",
                "@EnableSpring", "@SpringApplication", "@SpringBootApplication", "@MainApplication", "C",
                "@SpringBootApplication combines @Configuration, @EnableAutoConfiguration, and @ComponentScan."),
            makeQ("What does @RestController do in Spring Boot?",
                "Creates a web page", "Marks a class as a REST controller returning data directly in the response body",
                "Connects to a database", "Handles security", "B",
                "@RestController combines @Controller and @ResponseBody, making all methods return response body data."),
            makeQ("Which annotation is used for dependency injection in Spring Boot?",
                "@Inject", "@Autowired", "@Resource", "@Dependency", "B",
                "@Autowired is Spring's annotation for automatic dependency injection."),
            makeQ("What does @Entity annotation do in Spring Data JPA?",
                "Creates a REST endpoint", "Marks a class as a database table entity",
                "Defines a Spring service", "Handles HTTP requests", "B",
                "@Entity marks a Java class as a JPA entity mapped to a database table."),
            makeQ("What is the default embedded server in Spring Boot?",
                "Jetty", "Undertow", "Tomcat", "GlassFish", "C",
                "Spring Boot uses Apache Tomcat as its default embedded web server."),
            makeQ("What does @GetMapping do in Spring Boot?",
                "Maps HTTP DELETE requests", "Maps HTTP GET requests to a handler method",
                "Maps HTTP POST requests", "Maps all HTTP methods", "B",
                "@GetMapping is a shorthand for @RequestMapping(method = RequestMethod.GET)."),
            makeQ("What is the role of application.properties in Spring Boot?",
                "Stores Java source code", "Configures application settings like database and server port",
                "Defines HTML templates", "Manages Maven dependencies", "B",
                "application.properties (or .yml) holds configuration properties for the Spring Boot application."),
            makeQ("What is Spring Data JPA?",
                "A front-end framework", "A Spring module that simplifies database access using JPA",
                "A testing library", "A security module", "B",
                "Spring Data JPA reduces boilerplate by providing repository interfaces for common database operations."),
            makeQ("Which annotation marks a method to run after Bean initialization in Spring?",
                "@BeforeInit", "@PostConstruct", "@AfterCreate", "@Init", "B",
                "@PostConstruct marks a method to be executed after dependency injection is complete."),
            makeQ("What does @Service annotation indicate in Spring?",
                "A REST endpoint", "A class containing business logic", "A database entity", "A configuration class", "B",
                "@Service marks a class as a service layer component holding business logic."),
            makeQ("What is the purpose of @Repository in Spring?",
                "Marks a REST controller", "Marks a class as a data access layer component",
                "Handles scheduled tasks", "Manages application events", "B",
                "@Repository marks a DAO class and enables Spring to translate persistence exceptions."),
            makeQ("What does the 'spring.jpa.hibernate.ddl-auto=update' property do?",
                "Drops the database on startup", "Updates the schema automatically based on entity changes",
                "Creates a new database", "Validates schema without changing it", "B",
                "'update' modifies the existing schema to match entities without dropping data."),
            makeQ("Which annotation is used to handle form data in Spring MVC?",
                "@RequestBody", "@PathVariable", "@RequestParam", "@FormData", "C",
                "@RequestParam extracts query parameters or form data from the HTTP request."),
            makeQ("What is Thymeleaf in Spring Boot?",
                "A Java testing library", "A server-side Java template engine for HTML",
                "A database ORM", "A REST client", "B",
                "Thymeleaf is a server-side template engine used in Spring Boot for rendering HTML views.")
        ));
    }

    // ---- React.js ----------------------------------------------------------------
    private List<QuizQuestion> reactQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What is React.js?",
                "A backend framework", "A JavaScript library for building user interfaces",
                "A CSS framework", "A database tool", "B",
                "React is a JavaScript library developed by Facebook for building interactive UIs."),
            makeQ("What is JSX in React?",
                "A Java extension", "A syntax extension allowing HTML-like syntax in JavaScript",
                "A JSON format", "A CSS preprocessor", "B",
                "JSX is syntactic sugar that lets you write HTML-like code inside JavaScript."),
            makeQ("What is a React component?",
                "A CSS class", "A reusable, self-contained piece of UI", "A database entity", "A JavaScript event", "B",
                "React components are reusable building blocks that accept props and return JSX."),
            makeQ("What does 'useState' hook do in React?",
                "Fetches data from an API", "Adds state management to functional components",
                "Connects to a database", "Handles routing", "B",
                "useState returns a state variable and a setter function for managing state in functional components."),
            makeQ("What is the virtual DOM in React?",
                "A browser API", "A lightweight copy of the real DOM for efficient updates",
                "A database connection", "A testing tool", "B",
                "React's virtual DOM enables efficient UI updates by diffing changes before applying them to the real DOM."),
            makeQ("What does 'useEffect' hook do in React?",
                "Stores component state", "Handles side effects like API calls and subscriptions",
                "Renders JSX", "Creates new components", "B",
                "useEffect runs side effects after render, replacing lifecycle methods like componentDidMount."),
            makeQ("What are props in React?",
                "State variables", "Read-only data passed from parent to child components",
                "CSS properties", "Database values", "B",
                "Props are the mechanism for passing data and callbacks from parent to child components."),
            makeQ("What is React Router used for?",
                "State management", "Handling navigation and routing in React apps",
                "API calls", "Component styling", "B",
                "React Router provides navigation between different views/pages in a React application."),
            makeQ("What is the key prop in React lists for?",
                "Styling list items", "Helping React identify which items changed for efficient re-rendering",
                "Sorting items", "Accessing list values", "B",
                "Keys help React efficiently update lists by identifying which elements changed, were added, or removed."),
            makeQ("What is Redux used for in React applications?",
                "Styling components", "Centralized state management across the application",
                "Server-side rendering", "API integration", "B",
                "Redux provides a predictable state container for managing global application state."),
            makeQ("What is the difference between controlled and uncontrolled components?",
                "No difference", "Controlled components have state managed by React; uncontrolled use DOM refs",
                "Uncontrolled components use hooks only", "Controlled components are class-based only", "B",
                "Controlled components have their value managed by React state; uncontrolled access DOM directly via refs."),
            makeQ("What does React.memo() do?",
                "Memorizes variables", "Prevents unnecessary re-renders if props haven't changed",
                "Manages async operations", "Clears component state", "B",
                "React.memo is a higher-order component that memoizes the result to skip re-rendering with same props."),
            makeQ("What is Context API in React?",
                "A way to style components", "A way to pass data through the component tree without props drilling",
                "A router library", "A testing utility", "B",
                "Context API lets you share values between components without manually passing props at every level."),
            makeQ("What is a React Hook?",
                "A lifecycle method in class components", "A function that lets functional components use state and lifecycle features",
                "A CSS animation", "A routing mechanism", "B",
                "Hooks like useState, useEffect let functional components access React features previously only in classes."),
            makeQ("What does the 'key' prop do when rendering lists?",
                "Encrypts each list item", "Provides a unique identifier to help React track element identity",
                "Adds CSS styles", "Counts list items", "B",
                "The key prop is used by React's reconciliation algorithm to efficiently update UI lists.")
        ));
    }

    // ---- Database Design ----------------------------------------------------------------
    private List<QuizQuestion> databaseDesignQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What is database normalization?",
                "Speeding up queries", "Organizing data to reduce redundancy and improve integrity",
                "Encrypting database data", "Creating backups", "B",
                "Normalization eliminates data redundancy and ensures data dependencies make logical sense."),
            makeQ("What does 1NF (First Normal Form) require?",
                "No duplicate rows and all columns contain atomic values",
                "No partial dependencies", "No transitive dependencies", "A primary key on every table", "A",
                "1NF requires that each column contains atomic (indivisible) values and each row is unique."),
            makeQ("What is a one-to-many relationship in databases?",
                "One record maps to exactly one other record", "One record can relate to many records in another table",
                "Many records map to many records", "A record with no relationships", "B",
                "One-to-many: one row in table A can associate with multiple rows in table B."),
            makeQ("What is an ER diagram used for?",
                "Writing SQL queries", "Visualizing entities and their relationships in a database",
                "Designing CSS layouts", "Planning server infrastructure", "B",
                "An Entity-Relationship diagram visually represents database entities and their relationships."),
            makeQ("What is ACID in database transactions?",
                "A security protocol", "Atomicity, Consistency, Isolation, Durability — properties ensuring reliable transactions",
                "A query optimization algorithm", "A type of database index", "B",
                "ACID properties guarantee that database transactions are processed reliably."),
            makeQ("What is the purpose of database indexing?",
                "Encrypting rows", "Speeding up data retrieval at the cost of additional storage",
                "Normalizing data", "Creating backups", "B",
                "Indexes create data structures that speed up queries but require extra storage and update overhead."),
            makeQ("What does a composite key consist of?",
                "Multiple primary keys", "Two or more columns combined to uniquely identify a row",
                "A foreign key and a primary key", "An auto-incrementing ID", "B",
                "A composite key uses two or more columns together to uniquely identify a record."),
            makeQ("What is database denormalization?",
                "Removing all indexes", "Adding controlled redundancy to improve read performance",
                "Encrypting data columns", "Deleting unused tables", "B",
                "Denormalization intentionally adds redundancy to reduce complex joins and speed up read operations."),
            makeQ("What is a stored procedure in databases?",
                "A type of index", "A precompiled SQL program stored in the database",
                "A backup mechanism", "A foreign key constraint", "B",
                "Stored procedures are precompiled SQL code blocks stored in the database and executed by name."),
            makeQ("What is referential integrity in databases?",
                "Making all data unique", "Ensuring foreign key values match existing primary key values",
                "Encrypting table data", "Optimizing queries", "B",
                "Referential integrity ensures that a foreign key always points to a valid primary key in the referenced table.")
        ));
    }

    // ---- General Knowledge ----------------------------------------------------------------
    private List<QuizQuestion> generalKnowledgeQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What does CPU stand for?",
                "Central Processing Unit", "Computer Personal Unit", "Central Program Utility", "Core Processing Unit", "A",
                "CPU stands for Central Processing Unit, the primary component that executes instructions."),
            makeQ("What does HTTP stand for?",
                "Hyper Transfer Text Protocol", "HyperText Transfer Protocol", "High Traffic Transmission Protocol", "Home Tool Transfer Protocol", "B",
                "HTTP stands for HyperText Transfer Protocol, the foundation of data communication on the web."),
            makeQ("What is open-source software?",
                "Software that costs nothing", "Software with publicly available source code that can be modified",
                "Software made by governments", "Software with no license", "B",
                "Open-source software has its source code publicly available, allowing anyone to inspect, modify, and distribute it."),
            makeQ("What does RAM stand for?",
                "Read Access Memory", "Random Access Memory", "Rapid Application Module", "Remote Array Memory", "B",
                "RAM stands for Random Access Memory, the computer's short-term memory used for active processes."),
            makeQ("What is the Internet of Things (IoT)?",
                "A high-speed internet service", "Network of physical objects embedded with sensors and connectivity",
                "A social media platform", "A programming language", "B",
                "IoT refers to physical devices connected to the internet, collecting and sharing data."),
            makeQ("What does URL stand for?",
                "Uniform Resource Locator", "Universal Reference Link", "Unified Resource Label", "User Request Language", "A",
                "URL stands for Uniform Resource Locator, the address used to access resources on the web."),
            makeQ("What is binary code?",
                "A code based on 10 digits", "A number system using only 0 and 1",
                "A type of encryption", "Programming in C language", "B",
                "Binary uses only two states (0 and 1) to represent all data in computer systems."),
            makeQ("What does GUI stand for?",
                "General User Interface", "Graphical User Interface", "Global Utility Integration", "Grid UI", "B",
                "GUI stands for Graphical User Interface, allowing users to interact with software visually."),
            makeQ("What is cybersecurity?",
                "Securing physical computer hardware", "Practice of protecting systems, networks, and data from digital attacks",
                "Internet speed optimization", "A programming language", "B",
                "Cybersecurity encompasses technologies and practices to defend systems from unauthorized access and attacks."),
            makeQ("What does SSD stand for?",
                "Software System Drive", "Solid State Drive", "Structured Storage Device", "System Speed Disk", "B",
                "SSD stands for Solid State Drive, a faster storage medium using flash memory instead of spinning disks."),
            makeQ("What is machine learning?",
                "Teaching machines to type", "AI subset where systems learn from data without explicit programming",
                "Programming robotic arms", "A computer hardware component", "B",
                "Machine learning enables systems to automatically learn and improve from experience without being explicitly programmed."),
            makeQ("What is version control in software development?",
                "Website version numbering", "System that tracks changes to code over time",
                "A database backup system", "Managing software licenses", "B",
                "Version control (like Git) tracks changes to files over time, allowing collaboration and rollback."),
            makeQ("What does API stand for?",
                "Advanced Program Interface", "Application Programming Interface",
                "Automated Process Integration", "Application Protocol Integration", "B",
                "API stands for Application Programming Interface, defining how software components interact."),
            makeQ("What is encryption?",
                "Compressing data for storage", "Converting data into a coded format to prevent unauthorized access",
                "Speeding up data transfer", "A type of database", "B",
                "Encryption transforms readable data into an encoded format that only authorized parties can decode."),
            makeQ("What does DNS stand for?",
                "Digital Network System", "Domain Name System", "Data Naming Service", "Direct Network Server", "B",
                "DNS stands for Domain Name System, translating human-readable domain names to IP addresses.")
        ));
    }

    // ---- Cloud Computing ----------------------------------------------------------------
    private List<QuizQuestion> cloudComputingQuestions() {
        return new ArrayList<>(Arrays.asList(
            makeQ("What are the three main cloud service models?",
                "Public, Private, Hybrid", "IaaS, PaaS, SaaS", "Storage, Compute, Network", "AWS, Azure, GCP", "B",
                "IaaS (Infrastructure), PaaS (Platform), and SaaS (Software) are the three main cloud service models."),
            makeQ("What does IaaS stand for?",
                "Internet as a Service", "Infrastructure as a Service", "Integrated Application Software", "Information as a System", "B",
                "IaaS provides virtualized computing resources over the internet (servers, storage, networking)."),
            makeQ("What is a cloud region?",
                "A specific cloud pricing tier", "A geographic area containing multiple data centers",
                "A type of cloud storage", "A cloud security zone", "B",
                "A cloud region is a geographic location with multiple isolated data centers (availability zones)."),
            makeQ("What is auto-scaling in cloud computing?",
                "Automatically updating software", "Dynamically adjusting resource capacity based on demand",
                "Encrypting data automatically", "Automatic billing adjustment", "B",
                "Auto-scaling automatically increases or decreases compute resources to match current traffic demand."),
            makeQ("What is serverless computing?",
                "Computing without any servers", "Cloud execution model where infrastructure is fully managed by the provider",
                "Free cloud services", "Local computer processing", "B",
                "Serverless lets developers run code without managing servers — the provider handles scaling and infrastructure."),
            makeQ("What does SaaS stand for?",
                "Software as a Service", "System as a Service", "Security as a Software", "Scalable as a Service", "A",
                "SaaS delivers software applications over the internet on a subscription basis (e.g., Gmail, Salesforce)."),
            makeQ("What is a cloud availability zone?",
                "A geographic cloud region", "An isolated data center within a region for high availability",
                "A security perimeter", "A cloud pricing tier", "B",
                "Availability zones are isolated data centers within a region designed to be independent failure domains."),
            makeQ("What is object storage in cloud computing?",
                "Storage for database objects", "A flat data storage architecture for unstructured data",
                "RAM-based temporary storage", "Block-level storage for VMs", "B",
                "Object storage (like S3) stores data as flat objects with metadata, ideal for unstructured data like files."),
            makeQ("What is a Virtual Private Cloud (VPC)?",
                "A private server company", "An isolated, logically defined network within a public cloud",
                "A type of SaaS application", "A cloud security tool", "B",
                "A VPC provides a private, isolated network within the public cloud for secure resource deployment."),
            makeQ("What is the purpose of a load balancer in cloud computing?",
                "Encrypting network traffic", "Distributing incoming traffic across multiple servers for high availability",
                "Storing data across regions", "Managing cloud billing", "B",
                "Load balancers distribute network requests across multiple servers to ensure no single server is overloaded."),
            makeQ("What is cloud native development?",
                "Developing only for on-premise systems", "Building applications that fully exploit cloud computing capabilities",
                "Using native programming languages", "A type of hybrid cloud", "B",
                "Cloud-native apps are designed for cloud environments using containers, microservices, and CI/CD pipelines."),
            makeQ("What is a container in cloud computing?",
                "A physical storage box", "A lightweight, portable package containing an application and its dependencies",
                "A cloud billing unit", "A virtual machine type", "B",
                "Containers (e.g., Docker) package code with all dependencies for consistent runtime across environments."),
            makeQ("What is Kubernetes used for?",
                "Database management", "Automating deployment, scaling, and management of containerized applications",
                "Network security", "Cloud billing", "B",
                "Kubernetes is an open-source orchestration platform for automating container deployment and management.")
        ));
    }

    // ---- Generic fallback ----------------------------------------------------------------
    private List<QuizQuestion> genericQuestions(String subject) {
        String[] topics = {
            "fundamentals", "advanced concepts", "best practices", "design patterns",
            "common algorithms", "data structures", "optimization techniques", "debugging",
            "testing strategies", "performance tuning", "security considerations",
            "architecture patterns", "scalability", "maintainability", "integration"
        };
        String[] optionSuffixes = {
            "approach A — commonly used in industry",
            "approach B — recommended by official documentation",
            "approach C — suitable for large-scale systems",
            "approach D — used for rapid prototyping"
        };
        String[] correctAnswers = {"A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C"};

        List<QuizQuestion> questions = new ArrayList<>();
        Random rand = new Random();
        List<String> shuffledTopics = new ArrayList<>(Arrays.asList(topics));
        Collections.shuffle(shuffledTopics, rand);

        for (int i = 0; i < shuffledTopics.size(); i++) {
            String topic = shuffledTopics.get(i);
            String correct = correctAnswers[i % correctAnswers.length];
            questions.add(makeQ(
                "In " + subject + ", which of the following best describes " + topic + "?",
                subject + " " + optionSuffixes[0],
                subject + " " + optionSuffixes[1],
                subject + " " + optionSuffixes[2],
                subject + " " + optionSuffixes[3],
                correct,
                "Understanding " + topic + " is fundamental to mastering " + subject + "."
            ));
        }
        return questions;
    }

    // ---- Helper to create QuizQuestion without quiz/number ----
    private QuizQuestion makeQ(String text, String a, String b, String c, String d,
                               String correct, String explanation) {
        QuizQuestion q = new QuizQuestion();
        q.setQuestionText(text);
        q.setOptionA(a);
        q.setOptionB(b);
        q.setOptionC(c);
        q.setOptionD(d);
        q.setCorrectAnswer(correct);
        q.setExplanation(explanation);
        return q;
    }

    // -------------------------------------------------------------------------
    // Public service methods
    // -------------------------------------------------------------------------

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
            if (q.getIsCorrect()) correctCount++;
        }
        quiz.setScore(correctCount);
        quiz.setCompleted(true);
        quizRepository.save(quiz);
    }

    public List<Quiz> getUserQuizzes(User user) {
        return quizRepository.findByUser(user);
    }
}
