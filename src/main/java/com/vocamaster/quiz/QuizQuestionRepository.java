package com.vocamaster.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findBySessionIdOrderByQuestionOrderAsc(Long sessionId);
}
