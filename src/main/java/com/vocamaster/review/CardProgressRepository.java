package com.vocamaster.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardProgressRepository extends JpaRepository<CardProgress, Long> {

    Optional<CardProgress> findByUserIdAndCardId(Long userId, Long cardId);
}
