package com.vocamaster.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 학습 랭킹 (ADR-059).
 *
 * @param period  today | all
 * @param day     오늘 랭킹의 날짜(KST). 전체면 null
 * @param entries 위에서부터 (같은 점수면 같은 순위 — 1, 2, 2, 4)
 * @param me      내 순위·점수. 아직 점수가 없으면 null
 */
public record StudyRankingResponse(String period, LocalDate day, List<Entry> entries, Entry me) {

    /** name은 이미 가려진 값이 올 수 있다(닉네임 가리기) — 화면은 그대로 보여 주기만 한다 */
    public record Entry(int rank, String name, long points, boolean me) {
    }
}
