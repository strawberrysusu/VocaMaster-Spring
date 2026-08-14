package com.vocamaster.review;


import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.DeckService;
import com.vocamaster.review.dto.BoxCountResponse;
import com.vocamaster.review.dto.DueCardResponse;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import com.vocamaster.review.dto.TodaySummaryResponse;
import com.vocamaster.stats.StatsService;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private static final int MAX_BOX = 6;

    // Review의 모든 시간 계산은 KST 기준 (출석부와 동일 — 서버가 UTC여도 '오늘'이 어긋나지 않게).
    // 궁극 해법은 Clock 주입(STRETCH)이나, 지금은 상수 통일로 충분
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 박스별 복습 간격 (ADR-029 확정값). boxLevel N → BOX_INTERVALS[N - 1]
    private static final Duration[] BOX_INTERVALS = {
            Duration.ofMinutes(10), // box 1
            Duration.ofDays(1),     // box 2
            Duration.ofDays(3),     // box 3
            Duration.ofDays(7),     // box 4
            Duration.ofDays(14),    // box 5
            Duration.ofDays(30),    // box 6
    };

    private final CardProgressRepository cardProgressRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;
    private final StatsService statsService;
    private final TodaySummaryCache summaryCache;

    public ReviewAnswerResponse recordAnswer(Long userId, Long cardId, boolean correct) {
        // ① 카드 실존 확인
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다"));

        // ② 검문소 — 남의 덱 카드면 403
        deckService.verifyOwner(card.getDeck().getId(), userId);

        // ③ 성적표 꺼내기 — 처음 만난 카드면 box 1로 생성
        CardProgress progress = cardProgressRepository.findByUserIdAndCardId(userId, cardId)
                .orElseGet(() -> newProgress(userId, card));

        // ④ 상자 옮기기 — 맞으면 한 칸 위로(천장 6), 틀리면 box 1 풀 리셋 (ADR-029)
        if (correct) {
            progress.setBoxLevel(Math.min(progress.getBoxLevel() + 1, MAX_BOX));
            progress.setCorrectStreak(progress.getCorrectStreak() + 1);
        } else {
            progress.setBoxLevel(1);
            progress.setCorrectStreak(0);
            progress.setWrongCount(progress.getWrongCount() + 1);
        }

        // ⑤ 새 박스의 간격만큼 뒤로 다음 복습 시각 도장
        LocalDateTime now = LocalDateTime.now(KST);
        progress.setLastReviewedAt(now);
        progress.setNextReviewAt(now.plus(BOX_INTERVALS[progress.getBoxLevel() - 1]));

        // 출석 도장 — 모든 학습 모드 공통 (연속 학습일). 같은 트랜잭션이라 답변과 함께 성공/롤백
        statsService.recordStudy(userId);

        // ⑥ 저장 — 처음 만난 카드는 INSERT, 기존 카드는 더티체킹으로도 저장되지만 패턴 통일
        return ReviewAnswerResponse.from(cardProgressRepository.save(progress));
    }

    // 복습 대상 목록 — deckId null이면 전체 덱 (새 카드는 A 결정에 따라 미포함)
    @Transactional(readOnly = true)
    public List<DueCardResponse> getDueCards(Long userId, Long deckId) {
        if (deckId != null) {
            deckService.verifyOwner(deckId, userId);    // 남의 덱 필터 요청 차단
        }
        return cardProgressRepository.findDueCards(userId, deckId, LocalDateTime.now(KST))
                .stream()
                .map(DueCardResponse::from)
                .toList();
    }

    // 오늘 현황판 — 숫자 4개 (남은 복습 / 오늘 복습한 카드 장수 / 오늘 전체 답변 횟수 / 연속 학습일)
    // cache-aside: 히트면 집계 쿼리 4방 생략, 미스면 계산 후 5분 캐싱 (ADR-036)
    @Transactional(readOnly = true)
    public TodaySummaryResponse getTodaySummary(Long userId) {
        // now/today를 '한 번만' 뽑아 캐시 조회·계산·저장이 같은 날짜를 쓰게 — 자정 경계에서
        // 23:59 조회 결과가 다음날 키에 저장되는 어긋남 방지 (Codex 검산)
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDate today = now.toLocalDate();

        TodaySummaryResponse cached = summaryCache.get(userId, today);
        if (cached != null) {
            return cached;
        }

        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        TodaySummaryResponse fresh = TodaySummaryResponse.builder()
                .dueCount(cardProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, now))
                .reviewedTodayCount(cardProgressRepository.countReviewedBetween(userId, startOfToday, startOfTomorrow))
                .studyCount(statsService.getTodayStudyCount(userId))
                .streak(statsService.getDisplayStreak(userId))
                .build();
        summaryCache.put(userId, today, fresh);
        return fresh;
    }

    // 박스별 분포 (홈 사다리 차트) — 카드가 없는 박스도 0으로 채워 항상 6칸 고정 반환
    @Transactional(readOnly = true)
    public List<BoxCountResponse> getBoxDistribution(Long userId) {
        Map<Integer, Long> byBox = cardProgressRepository.countByBoxLevel(userId).stream()
                .collect(Collectors.toMap(BoxCountResponse::getBox, BoxCountResponse::getCount));
        return IntStream.rangeClosed(1, MAX_BOX)
                .mapToObj(box -> new BoxCountResponse(box, byBox.getOrDefault(box, 0L)))
                .toList();
    }

    // 처음 만난 카드의 성적표 생성 (box 1, 즉시 복습 대상)
    private CardProgress newProgress(Long userId, Card card) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));
        return CardProgress.builder()
                .user(user)
                .card(card)
                .boxLevel(1)
                .correctStreak(0)
                .wrongCount(0)
                .nextReviewAt(LocalDateTime.now(KST))
                .build();
    }
}
