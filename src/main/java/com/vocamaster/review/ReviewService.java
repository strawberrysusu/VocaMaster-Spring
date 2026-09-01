package com.vocamaster.review;


import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ConflictException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.DeckService;
import com.vocamaster.review.dto.BatchAnswerRequest;
import com.vocamaster.review.dto.BatchAnswerResponse;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ReviewSubmissionRepository submissionRepository;
    private final TodaySummaryCache summaryCache;

    public ReviewAnswerResponse recordAnswer(Long userId, Long cardId, boolean correct) {
        Card card = loadOwnedCard(userId, cardId);
        CardProgress progress = applyAnswer(userId, card, correct, LocalDateTime.now(KST));

        // 출석 도장 — 모든 학습 모드 공통 (연속 학습일). 같은 트랜잭션이라 답변과 함께 성공/롤백
        statsService.recordStudy(userId, card.getDeck().getId());

        return ReviewAnswerResponse.from(progress);
    }

    /**
     * 학습 세션 일괄 제출 (V21, 2026-08-31).
     *
     * <p>세션 도중의 알아요/몰라요는 프론트의 임시 답안이고, '완료' 한 번으로 전체가 여기서 확정된다.
     * 그래야 이전 카드로 돌아가 답을 고칠 수 있다 — 즉시 저장 구조에서는 되돌리려면 박스를 되돌려야 하는데,
     * 오답은 boxLevel을 1로 풀 리셋해서 이전 값이 어디에도 남지 않는다.</p>
     *
     * <p>덱이 여럿 섞여도 된다(/study 전체 복습). 카드마다 {@link #loadOwnedCard}가 소유권을 다시 보므로
     * '세션의 덱'이라는 개념 자체가 필요 없다.</p>
     */
    public BatchAnswerResponse recordAnswers(Long userId, BatchAnswerRequest request) {
        List<BatchAnswerRequest.Item> answers = request.getAnswers();

        // ① 같은 카드가 두 번 들어오면 박스가 두 칸 움직인다 — 프론트 버그든 악의든 여기서 막는다
        Set<Long> seen = new LinkedHashSet<>();
        for (BatchAnswerRequest.Item item : answers) {
            if (!seen.add(item.getCardId())) {
                throw new BadRequestException("같은 카드가 두 번 들어왔습니다 (cardId=" + item.getCardId() + ")");
            }
        }
        int known = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
        String hash = payloadHash(answers);

        // ② 처리권 확보. 예외가 아니라 반환값 0으로 중복을 안다 —
        //    같은 트랜잭션에서 unique 위반을 catch하면 rollback-only에 걸려 뒤이은 조회가 못 나간다
        int claimed = submissionRepository.insertIgnore(
                userId, request.getSubmissionId(), answers.size(), known, hash);
        if (claimed == 0) {
            // 진 쪽은 잠금 읽기로 확정본을 본다. 아직 커밋 전이라 못 읽으면 409 — 클라이언트가 재시도한다
            ReviewSubmission done = submissionRepository.findLocking(userId, request.getSubmissionId())
                    .orElseThrow(() -> new ConflictException("같은 제출이 처리 중입니다. 잠시 후 다시 시도해주세요"));
            // 같은 ID인데 답이 다르면 조용히 무시하면 안 된다 —
            // 응답만 유실된 뒤 사용자가 답을 고쳐 재전송한 경우, 바뀐 답이 통째로 버려진다
            if (!done.getPayloadHash().equals(hash)) {
                throw new ConflictException("이미 제출된 세션과 답안이 다릅니다. 새로 시작해주세요",
                        ConflictException.SUBMISSION_MISMATCH);
            }
            return BatchAnswerResponse.alreadySubmitted(done);
        }

        // ③ 카드별 반영 — 하나라도 실패하면 영수증까지 통째로 롤백된다(반쪽 저장 없음).
        //    now를 여기서 한 번만 뽑는다: 카드마다 now()를 부르면 자정 경계에서
        //    앞 카드는 어제 날짜로, 뒤 카드와 출석부는 오늘 날짜로 갈린다
        LocalDateTime now = LocalDateTime.now(KST);
        List<ReviewAnswerResponse> results = new ArrayList<>(answers.size());
        Set<Long> deckIds = new LinkedHashSet<>();
        for (BatchAnswerRequest.Item item : answers) {
            Card card = loadOwnedCard(userId, item.getCardId());
            deckIds.add(card.getDeck().getId());
            results.add(ReviewAnswerResponse.from(applyAnswer(userId, card, item.getCorrect(), now)));
        }

        // ④ 출석 도장은 한 번 — 답변 수만큼 더하고, 이벤트는 덱마다 한 발 (200장 = 이벤트 200발 방지).
        //    날짜도 위 now에서 파생 — 카드 시간과 통계 날짜가 갈리지 않게
        statsService.recordStudy(userId, deckIds, answers.size(), now.toLocalDate());

        return BatchAnswerResponse.builder()
                .submissionId(request.getSubmissionId())
                .total(answers.size())
                .known(known)
                .unknown(answers.size() - known)
                .alreadySubmitted(false)
                .results(results)
                .build();
    }

    // 카드 실존 확인 + 검문소 — 남의 덱 카드면 403
    private Card loadOwnedCard(Long userId, Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다"));
        deckService.verifyOwner(card.getDeck().getId(), userId);
        return card;
    }

    /**
     * 제출 내용의 지문 — cardId 오름차순으로 정규화한 뒤 SHA-256.
     * 답변 <b>순서</b>가 달라도 내용이 같으면 같은 해시여야 재시도가 멱등하게 통과한다.
     */
    private String payloadHash(List<BatchAnswerRequest.Item> answers) {
        String canonical = answers.stream()
                .sorted(Comparator.comparing(BatchAnswerRequest.Item::getCardId))
                .map(a -> a.getCardId() + ":" + (Boolean.TRUE.equals(a.getCorrect()) ? "1" : "0"))
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);   // 표준 JDK에는 항상 있다
        }
    }

    /**
     * Leitner 규칙 (ADR-029) — 단건·일괄이 <b>이 한 곳만</b> 쓴다.
     * 두 벌로 나뉘면 언젠가 한쪽만 고쳐져 박스 규칙이 갈라진다.
     *
     * @param now 호출자가 뽑은 기준 시각. 일괄 제출은 카드마다 now()를 부르면
     *            자정 경계에서 카드 시간과 통계 날짜가 갈리므로 한 값을 흘려보낸다
     */
    private CardProgress applyAnswer(Long userId, Card card, boolean correct, LocalDateTime now) {
        // 성적표 꺼내기 — 처음 만난 카드면 box 1로 생성
        CardProgress progress = cardProgressRepository.findByUserIdAndCardId(userId, card.getId())
                .orElseGet(() -> newProgress(userId, card));

        // 상자 옮기기 — 맞으면 한 칸 위로(천장 6), 틀리면 box 1 풀 리셋
        if (correct) {
            progress.setBoxLevel(Math.min(progress.getBoxLevel() + 1, MAX_BOX));
            progress.setCorrectStreak(progress.getCorrectStreak() + 1);
        } else {
            progress.setBoxLevel(1);
            progress.setCorrectStreak(0);
            progress.setWrongCount(progress.getWrongCount() + 1);
        }

        // 새 박스의 간격만큼 뒤로 다음 복습 시각 도장 (now는 호출자가 뽑은 한 값)
        progress.setLastReviewedAt(now);
        progress.setNextReviewAt(now.plus(BOX_INTERVALS[progress.getBoxLevel() - 1]));

        // 저장 — 처음 만난 카드는 INSERT, 기존 카드는 더티체킹으로도 저장되지만 패턴 통일
        return cardProgressRepository.save(progress);
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
