package com.vocamaster.review;

/** 사용자가 보는 학습 상태. 복습 일정(boxLevel)과 별개로 연속 정답을 기준으로 한다. */
public enum LearningStatus {
    UNKNOWN,
    KNOWN;

    public static final int KNOWN_STREAK = 3;

    public static LearningStatus from(CardProgress progress) {
        return progress != null && progress.getLastReviewedAt() != null
                && progress.getCorrectStreak() >= KNOWN_STREAK ? KNOWN : UNKNOWN;
    }
}
