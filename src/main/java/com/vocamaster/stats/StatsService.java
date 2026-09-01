package com.vocamaster.stats;

import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.CardProgressRepository;
import com.vocamaster.stats.dto.StatsOverviewResponse;
import com.vocamaster.study.event.StudyRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

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
        recordStudy(userId, Collections.singletonList(deckId), 1);
    }

    /**
     * 일괄 제출용 (V21, 2026-08-31). 답변 수만큼 <b>한 번</b> 더하고, 이벤트는 <b>덱마다 한 번</b>만 발행한다.
     *
     * <p>200장 세션에서 단건 경로를 200번 부르면 UPDATE 200회에 이벤트 200발이 나간다.
     * 랭킹 리스너는 deck_study_days의 (user, deck, date) unique 덕에 점수가 터지진 않지만,
     * 그 200번이 전부 덱 행에 X 잠금을 잡았다 놓는다. 캐시 리스너는 @Async라 태스크 200개가 큐에 쌓인다.</p>
     *
     * @param deckIds 답변한 카드들의 덱 — <b>호출 전에 중복 제거</b>해서 넘길 것
     * @param answerCount 이번 제출의 답변 수 (오늘 답변 수에 더할 값)
     */
    public void recordStudy(Long userId, Collection<Long> deckIds, int answerCount) {
        recordStudy(userId, deckIds, answerCount, LocalDate.now(KST));
    }

    /**
     * 호출자가 '오늘'을 정해서 넘기는 형태. 일괄 제출은 카드 시각과 통계 날짜를 같은 기준에서 파생해야 한다 —
     * 각자 now()를 부르면 자정 경계에서 카드는 어제, 출석부는 오늘로 갈린다.
     */
    public void recordStudy(Long userId, Collection<Long> deckIds, int answerCount, LocalDate today) {
        if (answerCount <= 0) return;   // 더할 것이 없으면 출석 도장도 없다

        // 어제 줄을 보고 연속 여부 결정 (잠금 없는 일반 SELECT). 오늘 줄이 이미 있으면 streak 값은 무시됨
        int streak = dailyUserStatRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                .map(yesterday -> yesterday.getStreak() + 1)    // 어제도 공부함 → 연속 +1
                .orElse(1);                                     // 끊김 → 1부터 다시

        // 항상 upsert 한 방: 없으면 INSERT(streak 확정), 있으면 study_count += answerCount.
        // ★ 예전의 "0행 매치 UPDATE로 탐색 → 없으면 INSERT" 2단계는 InnoDB 갭 락 데드락을 냈다 (2026-08-22):
        //   같은 순간 '오늘 첫 학습'인 사용자 여럿이 0행 UPDATE로 같은 인덱스 갭에 갭 락을 쥔 채 INSERT를 기다림.
        //   Phase 6 동시성 테스트(DeckStudyRankingListenerTest 6명 동시)가 잠복 버그를 꺼냄
        dailyUserStatRepository.upsertTodayRow(userId, today, streak, answerCount);

        // 첫 학습이든 N번째든 반드시 도달 — 예전처럼 updated==1에서 조기 return하면
        // 오늘 두 번째 학습부터(최다 경로) 캐시가 안 지워지는 조용한 버그 (Codex 검산)
        // 발행은 트랜잭션 안에서 하지만, AFTER_COMMIT 리스너는 커밋 확정 후에야 실행된다
        for (Long deckId : deckIds) {
            eventPublisher.publishEvent(new StudyRecordedEvent(userId, deckId, today));
        }
    }

    // 오늘 학습 답변 수 — 출석부 오늘 줄이 없으면 0 (아직 오늘 공부 전)
    @Transactional(readOnly = true)
    public int getTodayStudyCount(Long userId) {
        return dailyUserStatRepository.findByUserIdAndStatDate(userId, LocalDate.now(KST))
                .map(DailyUserStat::getStudyCount)
                .orElse(0);
    }

    // ── 통계 화면 (2026-08-23) ──
    static final int OVERVIEW_DAYS = 28;     // 최근 4주
    static final int MASTERED_BOX = 5;       // 박스 5 이상 = 14일+ 간격 = "숙달" (기준 바꾸려면 여기 하나)

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final CardProgressRepository cardProgressRepository;

    @Transactional(readOnly = true)
    public StatsOverviewResponse getOverview(Long userId) {
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(OVERVIEW_DAYS - 1);

        // 1) 최근 28일 — 행이 없는 날은 0으로 채워 차트가 빈칸까지 그리게
        Map<LocalDate, Integer> byDate = dailyUserStatRepository
                .findByUserIdAndStatDateBetweenOrderByStatDateAsc(userId, from, today).stream()
                .collect(Collectors.toMap(DailyUserStat::getStatDate, DailyUserStat::getStudyCount));
        List<StatsOverviewResponse.DayActivity> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            days.add(new StatsOverviewResponse.DayActivity(d, byDate.getOrDefault(d, 0)));
        }

        // 2) 누적 집계 한 방
        Object[] agg = dailyUserStatRepository.aggregate(userId).get(0);
        long totalStudy = ((Number) agg[0]).longValue();
        int bestStreak = ((Number) agg[1]).intValue();
        long activeDays = ((Number) agg[2]).longValue();

        // 3) 덱별 진행률 — 카드 수·진행 수 둘 다 GROUP BY로 받아 메모리에서 합침
        Map<Long, Long> cardCounts = new HashMap<>();
        for (Object[] row : cardRepository.countByDeckForUser(userId)) {
            cardCounts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, long[]> progress = new HashMap<>();
        for (Object[] row : cardProgressRepository.progressByDeck(userId, MASTERED_BOX)) {
            progress.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        List<StatsOverviewResponse.DeckProgress> decks = deckRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(d -> {
                    long[] p = progress.getOrDefault(d.getId(), new long[]{0, 0});
                    return StatsOverviewResponse.DeckProgress.builder()
                            .deckId(d.getId())
                            .title(d.getTitle())
                            .cardCount(cardCounts.getOrDefault(d.getId(), 0L))
                            .started(p[0])
                            .mastered(p[1])
                            .build();
                })
                .toList();

        return StatsOverviewResponse.builder()
                .days(days)
                .streak(getDisplayStreak(userId))
                .bestStreak(bestStreak)
                .totalStudy(totalStudy)
                .activeDays(activeDays)
                .boxes(cardProgressRepository.countByBoxLevel(userId))
                .decks(decks)
                .build();
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
