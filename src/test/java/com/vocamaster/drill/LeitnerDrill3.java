package com.vocamaster.drill;

/*
 * ═══════════════════════════════════════════════════════════════════
 *  폐쇄훈련 #3 — 3회차 (2026-08-15) : 빈칸 채우기 단계
 * ═══════════════════════════════════════════════════════════════════
 *  결과 장부 (정직): 자력 4 / 참조 0 / 모름 2
 *   자력 — 간격 배열, 필드 4개, 승급 천장 Math.min(★1·2회차 연속 벽이었던 것 첫 자력),
 *          리셋 3줄("규칙 3줄→문장 모양" 힌트 후 자력)
 *   모름 — 날짜 계산(인덱스 어긋남+plus 문법), main 배치 → 4회차는 이 2개만 빈칸으로
 *   진단 유지: 벽은 개념이 아니라 '문장을 어디에 놓나'(문법 배치)
 * ═══════════════════════════════════════════════════════════════════
 *  룰
 *   1. Codex 창 닫았나? ✔️  ChatGPT/검색도 ❌  (문법 기억 안 나면 컴파일 에러 메시지가 힌트다)
 *   2. ★ 표시 빈칸을 채운다. 골격은 이미 있다 — 채우는 것에만 집중.
 *   3. 막히면 5분 고민 → 그래도 안 되면 그 빈칸에  // 모름: (왜 막혔는지 한 줄)  적고 다음으로.
 *      "빈칸 목록 확보"가 오늘의 진짜 목적. 90분 다 채울 필요 없음.
 *   4. 다 채우면 IntelliJ에서 실행 (Run) → 기대 출력 2, 3, 1 이 나오면 통과.
 *   5. 끝나면 파일 상단에 정직하게:  자력 __개 / 참조 __개 / 모름 __개
 *
 *  힌트 (여기까지만 — 답이 아니라 '무엇을 결정해야 하는가'):
 *   - 박스는 1~6. 6에서 맞아도 7이 되면 안 됨 → '천장'이 필요 (2회차 때 참조했던 그것)
 *   - 다음 복습 시각 = 지금 + 그 박스의 간격. 배열은 0부터 시작하는데 박스는 1부터 → 어긋남 하나
 *   - 날짜 계산은 정답/오답 분기 '안'인가 '밖'인가? (양쪽 다 필요하면 어디 두는 게 한 번만 쓰는 길인가)
 * ═══════════════════════════════════════════════════════════════════
 */

import java.time.Duration;
import java.time.LocalDateTime;

public class LeitnerDrill3 {

    private static final int MAX_BOX = 6;

    // ★1. 박스별 복습 간격 6개 — box1=10분, box2=1일, box3=3일, box4=7일, box5=14일, box6=30일
    //     (첫 칸은 채워둠. Duration의 팩토리 메서드 두 종류가 필요)
    public static Duration[] BOX_INTERVALS = {
            Duration.ofMinutes(10),
            Duration.ofDays(1),
            Duration.ofDays(3),
            Duration.ofDays(7),
            Duration.ofDays(14),
            Duration.ofDays(30)
    };

    // ★2. 카드 한 장의 성적표 — 필드 4개: 박스 레벨(정수), 연속 정답 수(정수), 오답 수(정수), 다음 복습 시각(날짜시간)
    static class CardProgress {
       public int boxLevel;
       public int correctstreak;
       public int wrongCount;
       public LocalDateTime nextReviewAt;
    }

    // ★3. 답변 기록 — 정답이면 승급(천장 있음)+연속 정답 +1 / 오답이면 박스 1로 리셋+연속 0+오답 +1
    //     그리고 다음 복습 시각 계산
    static void recordAnswer(CardProgress progress, boolean correct) {
        if (correct) {
            progress.boxLevel = Math.min(progress.boxLevel + 1, MAX_BOX);
        } else {
            progress.boxLevel = 1;
            progress.wrongCount++;
            progress.correctstreak = 0;
        }
        LocalDateTime now = LocalDateTime.now();
        progress.nextReviewAt = /* ★ 지금 + 현재 박스의 간격 (배열 인덱스 주의) */ ;
    }

    public static void main(String[] args) {
        // ★4. 시나리오: 성적표 하나 만들고 박스 1에서 시작 → 정답, 정답, 오답 순으로 기록하며 매번 boxLevel 출력
        //     기대 출력: 2 / 3 / 1
        /* ★ */
    }
}
