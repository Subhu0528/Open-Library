package com.project.openlibrary.repository;

import com.project.openlibrary.model.Quiz;
import com.project.openlibrary.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByUser(User user);

    List<Quiz> findByUserAndCompleted(User user, Boolean completed);

    Optional<Quiz> findByIdAndUser(Long id, User user);
}
