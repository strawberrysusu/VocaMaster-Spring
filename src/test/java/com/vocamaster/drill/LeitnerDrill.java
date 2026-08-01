package com.vocamaster.drill;



import java.time.Duration;
import java.time.LocalDateTime;


public class LeitnerDrill {

private static final int MAX_BOX = 6;
    public static Duration[] BOX_INTERVALS = {

            Duration.ofMinutes(10),   // box 1 — 첫 칸은 내가 채워줌
            Duration.ofDays(1),
            Duration.ofDays(3),
            Duration.ofDays(7),
            Duration.ofDays(14),
            Duration.ofDays(30)


    };

    static class CardProgress{
        public int boxLevel;
        public int correctStreak;
        public int wrongCount;
        public LocalDateTime nextReviewAt;
    }

    static void recordAnswer(CardProgress progress, boolean correct) {
        //정답일때 오답일때 날짜 계산
        if (correct) {
            progress.boxLevel =
                    Math.min(progress.boxLevel + 1, MAX_BOX);
            progress.correctStreak++;
        } else {
            progress.boxLevel = 1;
            progress.correctStreak = 0;
            progress.wrongCount++;
        }
        LocalDateTime now = LocalDateTime.now();
        progress.nextReviewAt =
                now.plus(BOX_INTERVALS[progress.boxLevel - 1]);
    }

    public static void main(String[] args) {
        // progress 하나생성
        //맞음틀림맞음?\
        //boxlevel 출력
        CardProgress progress = new CardProgress();
        progress.boxLevel = 1;

        recordAnswer(progress, true);
        System.out.println(progress.boxLevel); // 2

        recordAnswer(progress, true);
        System.out.println(progress.boxLevel); // 3

        recordAnswer(progress, false);
        System.out.println(progress.boxLevel); // 1
    }
}

