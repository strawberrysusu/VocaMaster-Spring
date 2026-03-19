package com.vocamaster.study.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeckStatsResponse {

    private Long deckId;
    private long totalCards;
    private StudyStat study;
    private QuizStat quiz;
    private RecentStat recent7Days;

    @Getter
    @Builder
    public static class StudyStat {
        private long totalSessions;
        private long totalRecords;
        private long known;
        private long unknown;
        private long accuracy;
    }

    @Getter
    @Builder
    public static class QuizStat {
        private long totalAttempts;
        private long correct;
        private long wrong;
        private long accuracy;
    }

    @Getter
    @Builder
    public static class RecentStat {
        private long studySessions;
        private long quizAttempts;
    }
}
