package com.vocamaster.typing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TypingQuestionRepository extends JpaRepository<TypingQuestion, Long> {

    List<TypingQuestion> findBySessionIdOrderByQuestionOrderAsc(Long sessionId);
}
