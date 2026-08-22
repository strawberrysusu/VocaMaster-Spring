package com.vocamaster.stats;

import com.vocamaster.study.event.StudyRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class StatsService {

    // 정책이 "KST 자정 기준"이므로 서버 기본 시간대에 의존하지 않고 명시 (배포 서버가 UTC여도 동일 동작)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyUserStatRepository dailyUserStatRepository;
    // Phase 6: 캐시를 직접 알던 결합(ADR-036 승인 냄새)을 이벤트로 해소.
    // 출석은 "학습했다"고 외치기만 — 누가 듣는지(캐시·랭킹·배지) 모른다 (ADR-037)
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 학습 활동 1회 = 출석 도장. 모든 학습 모드(Review/Quiz/Typing/Study)가 호출.
     * 호출한 쪽 트랜잭션에 합류하므로 답변 저장과 출석이 같이 성공하거나 같이 롤백된다.
     */
    public void recordStudy(Long userId, Long deckId) {
        LocalDate today = LocalDate.now(KST);

        // 어제 줄을 보고 연속 여부 결정 (잠금 없는 일반 SELECT). 오늘 줄이 이미 있으면 streak 값은 무시됨
        int streak = dailyUserStatRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                .map(yesterday -> yesterday.getStreak() + 1)    // 어제도 공부함 → 연속 +1
                .orElse(1);                                     // 끊김 → 1부터 다시

        // 항상 upsert 한 방: 없으면 INSERT(streak 확정), 있으면 study_count +1.
        // ★ 예전의 "0행 매치 UPDATE로 탐색 → 없으면 INSERT" 2단계는 InnoDB 갭 락 데드락을 냈다 (2026-08-22):
        //   같은 순간 '오늘 첫 학습'인 사용자 여럿이 0행 UPDATE로 같은 인덱스 갭에 갭 락을 쥔 채 INSERT를 기다림.
        //   Phase 6 동시성 테스트(DeckStudyRankingListenerTest 6명 동시)가 잠복 버그를 꺼냄
        dailyUserStatRepository.upsertTodayRow(userId, today, streak);

        // 첫 학습이든 N번째든 반드시 도달 — 예전처럼 updated==1에서 조기 return하면
        // 오늘 두 번째 학습부터(최다 경로) 캐시가 안 지워지는 조용한 버그 (Codex 검산)
        // 발행은 트랜잭션 안에서 하지만, AFTER_COMMIT 리스너는 커밋 확정 후에야 실행된다
        eventPublisher.publishEvent(new StudyRecordedEvent(userId, deckId, today));
    }

    // 오늘 학습 답변 수 — 출석부 오늘 줄이 없으면 0 (아직 오늘 공부 전)
    @Transactional(readOnly = true)
    public int getTodayStudyCount(Long userId) {
        return dailyUserStatRepository.findByUserIdAndStatDate(userId, LocalDate.now(KST))
                .map(DailyUserStat::getStudyCount)
                .orElse(0);
    }

    // 표시용 streak (A 정책): 오늘 줄 있으면 오늘 값, 없으면 "어제까지의 연속"을 오늘 하루 유예로 보여줌.
    // 어제 줄도 없으면 끊김 확정 → 0
    @Transactional(readOnly = true)
    public int getDisplayStreak(Long userId) {
        LocalDate today = LocalDate.now(KST);
        return dailyUserStatRepository.findByUserIdAndStatDate(userId, today)
                .map(DailyUserStat::getStreak)
                .orElseGet(() -> dailyUserStatRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                        .map(DailyUserStat::getStreak)
                        .orElse(0));
    }
}
