package com.vocamaster.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningStatusTest {

    @Test
    void missingOrUnansweredProgress_isNotStarted() {
        assertEquals(LearningStatus.UNKNOWN, LearningStatus.from(null));
        assertEquals(LearningStatus.UNKNOWN, LearningStatus.from(CardProgress.builder().build()));
    }

    @ParameterizedTest
    @CsvSource({"0, UNKNOWN", "1, UNKNOWN", "2, UNKNOWN", "3, KNOWN", "4, KNOWN", "10, KNOWN"})
    void reviewedCard_usesConsecutiveCorrectAnswers(int streak, LearningStatus expected) {
        CardProgress progress = CardProgress.builder()
                .correctStreak(streak)
                .lastReviewedAt(LocalDateTime.of(2026, 9, 25, 12, 0))
                .build();

        assertEquals(expected, LearningStatus.from(progress));
    }
}
