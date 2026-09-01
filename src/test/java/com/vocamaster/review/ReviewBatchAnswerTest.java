package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ConflictException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.dto.BatchAnswerRequest;
import com.vocamaster.review.dto.BatchAnswerResponse;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 학습 세션 일괄 제출 (V21, 2026-08-31).
 *
 * <p>자동 롤백(@Transactional)을 끄는 이유: 이 기능의 핵심 두 가지가 '진짜 커밋'을 요구한다.
 * ① 재전송 멱등은 앞선 제출이 <b>커밋된 뒤</b> INSERT IGNORE가 0을 반환해야 성립하고,
 * ② "하나라도 실패하면 전체 롤백"은 진짜 트랜잭션 경계가 있어야 검증된다.
 * 자동 롤백 모드에는 커밋이 없어 둘 다 못 본다 (ReviewServiceConcurrencyTest와 같은 판단).
 * → 만든 데이터는 @AfterEach에서 FK 역순으로 손수 삭제.</p>
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReviewBatchAnswerTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private ReviewService reviewService;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private ReviewSubmissionRepository submissionRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    private User user;
    private User stranger;
    private Deck deckA;
    private Deck deckB;
    private Deck strangerDeck;
    private Card a1, a2, b1, foreign;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();                      // 컨테이너 재사용(withReuse) 대비 유니크
        user = userRepository.save(User.builder()
                .email("batch_" + seed + "@test.com").password("encoded").nickname("일괄").build());
        stranger = userRepository.save(User.builder()
                .email("stranger_" + seed + "@test.com").password("encoded").nickname("남").build());

        deckA = deckRepository.save(Deck.builder().title("Deck A").user(user).build());
        deckB = deckRepository.save(Deck.builder().title("Deck B").user(user).build());
        strangerDeck = deckRepository.save(Deck.builder().title("남의 덱").user(stranger).build());

        a1 = cardRepository.save(Card.builder().front("apple").back("사과").deck(deckA).build());
        a2 = cardRepository.save(Card.builder().front("banana").back("바나나").deck(deckA).build());
        b1 = cardRepository.save(Card.builder().front("会議").back("회의").deck(deckB).build());
        foreign = cardRepository.save(Card.builder().front("secret").back("비밀").deck(strangerDeck).build());
    }

    @AfterEach
    void cleanUp() {
        for (Card c : List.of(a1, a2, b1, foreign)) {
            cardProgressRepository.findByUserIdAndCardId(user.getId(), c.getId())
                    .ifPresent(cardProgressRepository::delete);
        }
        submissionRepository.findAll().stream()
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .forEach(submissionRepository::delete);
        dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(KST))
                .ifPresent(dailyUserStatRepository::delete);
        cardRepository.deleteAll(List.of(a1, a2, b1, foreign));
        deckRepository.deleteAll(List.of(deckA, deckB, strangerDeck));
        userRepository.deleteAll(List.of(user, stranger));
    }

    private BatchAnswerRequest req(String submissionId, Object... cardIdThenCorrect) {
        BatchAnswerRequest r = new BatchAnswerRequest();
        r.setSubmissionId(submissionId);
        List<BatchAnswerRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < cardIdThenCorrect.length; i += 2) {
            BatchAnswerRequest.Item item = new BatchAnswerRequest.Item();
            item.setCardId((Long) cardIdThenCorrect[i]);
            item.setCorrect((Boolean) cardIdThenCorrect[i + 1]);
            items.add(item);
        }
        r.setAnswers(items);
        return r;
    }

    /** 검증용 조회 — findLocking은 PESSIMISTIC_READ라 트랜잭션 없는 이 테스트에서 쓸 수 없다 */
    private boolean submissionExists(String submissionId) {
        return submissionRepository.findAll().stream()
                .anyMatch(s -> s.getUser().getId().equals(user.getId())
                        && s.getSubmissionId().equals(submissionId));
    }

    private int box(Card c) {
        return cardProgressRepository.findByUserIdAndCardId(user.getId(), c.getId())
                .map(CardProgress::getBoxLevel)
                .orElse(0);   // 0 = 성적표 자체가 없음
    }

    @Test
    @DisplayName("일괄 제출 — 카드마다 정확히 한 번씩만 반영된다")
    void batch_appliesEachCardExactlyOnce() {
        BatchAnswerResponse res = reviewService.recordAnswers(user.getId(),
                req("sub-1", a1.getId(), true, a2.getId(), true, b1.getId(), false));

        assertFalse(res.isAlreadySubmitted());
        assertEquals(3, res.getTotal());
        assertEquals(2, res.getKnown());
        assertEquals(1, res.getUnknown());
        assertEquals(3, res.getResults().size());

        assertEquals(2, box(a1));   // 처음 만난 카드 box 1 생성 → 정답으로 2
        assertEquals(2, box(a2));
        assertEquals(1, box(b1));   // 오답은 box 1 리셋
    }

    @Test
    @DisplayName("같은 submissionId로 재전송 — 진행도가 다시 움직이지 않는다")
    void batch_resubmitSameId_doesNotMoveBoxesAgain() {
        reviewService.recordAnswers(user.getId(), req("sub-dup", a1.getId(), true, a2.getId(), true));
        assertEquals(2, box(a1));

        // 같은 제출을 그대로 다시 (더블클릭·네트워크 재시도)
        BatchAnswerResponse again = reviewService.recordAnswers(user.getId(),
                req("sub-dup", a1.getId(), true, a2.getId(), true));

        assertTrue(again.isAlreadySubmitted());
        assertEquals(2, again.getTotal());
        assertEquals(2, again.getKnown());
        // 그때의 박스 값은 저장하지 않으므로 지어내지 않는다
        assertNull(again.getResults());

        assertEquals(2, box(a1), "재전송이 박스를 한 칸 더 올리면 안 된다");
        assertEquals(2, box(a2));
    }

    @Test
    @DisplayName("같은 cardId가 두 번 들어오면 400 — 박스가 두 칸 움직이는 걸 막는다")
    void batch_duplicateCardId_rejected() {
        assertThrows(BadRequestException.class, () -> reviewService.recordAnswers(user.getId(),
                req("sub-dupcard", a1.getId(), true, a1.getId(), true)));

        assertEquals(0, box(a1), "거부된 제출은 성적표를 만들지 않는다");
        assertFalse(submissionExists("sub-dupcard"));
    }

    @Test
    @DisplayName("남의 카드가 섞이면 403 — 앞서 처리된 카드까지 전부 롤백된다")
    void batch_foreignCard_rollsBackEverything() {
        assertThrows(ForbiddenException.class, () -> reviewService.recordAnswers(user.getId(),
                req("sub-foreign", a1.getId(), true, foreign.getId(), true)));

        assertEquals(0, box(a1), "앞 카드도 반영되면 안 된다 (반쪽 저장 금지)");
        assertFalse(submissionExists("sub-foreign"),
                "영수증도 함께 롤백되어야 재시도가 가능하다");
    }

    @Test
    @DisplayName("여러 덱이 섞인 제출 — 세션의 덱 개념 없이 카드마다 소유권을 본다")
    void batch_multipleDecks_ok() {
        BatchAnswerResponse res = reviewService.recordAnswers(user.getId(),
                req("sub-multi", a1.getId(), true, b1.getId(), true));

        assertEquals(2, res.getTotal());
        assertEquals(2, box(a1));   // Deck A
        assertEquals(2, box(b1));   // Deck B
    }

    @Test
    @DisplayName("같은 submissionId인데 답이 다르면 409 — 바뀐 답을 조용히 버리지 않는다")
    void batch_sameIdDifferentAnswers_conflict() {
        reviewService.recordAnswers(user.getId(), req("sub-edit", a1.getId(), true, a2.getId(), true));

        // 응답이 유실됐다고 믿은 사용자가 a2를 '몰라요'로 고쳐 같은 ID로 재전송
        ConflictException ex = assertThrows(ConflictException.class, () ->
                reviewService.recordAnswers(user.getId(), req("sub-edit", a1.getId(), true, a2.getId(), false)));
        // 클라이언트가 메시지 문자열이 아니라 code로 분기한다 — 같은 409라도 대응이 정반대이므로
        // (일시 경합은 같은 ID로 재시도, 불일치는 새 ID로 제출)
        assertEquals(ConflictException.SUBMISSION_MISMATCH, ex.getCode());

        assertEquals(2, box(a2), "거부됐으므로 첫 제출 결과(알아요)가 그대로여야 한다");
    }

    @Test
    @DisplayName("답변 순서만 다른 재전송은 같은 제출로 본다 — 해시는 cardId 정렬 후 계산")
    void batch_sameAnswersDifferentOrder_isIdempotent() {
        reviewService.recordAnswers(user.getId(), req("sub-order", a1.getId(), true, a2.getId(), false));

        BatchAnswerResponse again = reviewService.recordAnswers(user.getId(),
                req("sub-order", a2.getId(), false, a1.getId(), true));   // 순서만 뒤집음

        assertTrue(again.isAlreadySubmitted());
        assertEquals(2, box(a1));
        assertEquals(1, box(a2));
    }

    @Test
    @DisplayName("스레드 2개가 같은 submissionId를 동시에 — 정확히 한 번만 반영된다")
    void batch_concurrentSameSubmissionId_appliedOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gun = new CountDownLatch(1);   // 둘을 같은 순간에 출발시킨다

        Callable<Object> shot = () -> {
            gun.await(5, TimeUnit.SECONDS);
            try {
                return reviewService.recordAnswers(user.getId(),
                        req("sub-race", a1.getId(), true, a2.getId(), true));
            } catch (RuntimeException e) {
                return e;   // 여기서 잡되, 아래에서 허용 예외인지 반드시 가린다
            }
        };
        Future<Object> f1 = pool.submit(shot);
        Future<Object> f2 = pool.submit(shot);
        gun.countDown();
        Object r1 = f1.get(20, TimeUnit.SECONDS);
        Object r2 = f2.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        // 허용되는 결말은 딱 둘 — 정상 응답, 또는 '아직 커밋 전이라 확정본을 못 읽음' 409.
        // 이 체크가 없으면 NPE나 예상 못 한 500도 아래 count 조건을 통과해 테스트가 초록으로 거짓말한다
        for (Object r : List.of(r1, r2)) {
            boolean allowed = r instanceof BatchAnswerResponse || r instanceof ConflictException;
            assertTrue(allowed, "예상 밖 결말: " + r.getClass().getName() + " / " + r);
        }

        // 둘 중 정확히 하나만 '처음 처리'여야 한다
        long fresh = List.of(r1, r2).stream()
                .filter(r -> r instanceof BatchAnswerResponse b && !b.isAlreadySubmitted())
                .count();
        assertEquals(1, fresh, "동시 두 요청 중 실제 반영은 한 번뿐이어야 한다: " + r1 + " / " + r2);

        // 진행도가 두 번 움직이지 않았다
        assertEquals(2, box(a1), "박스가 두 칸 오르면 멱등이 깨진 것");
        assertEquals(2, box(a2));

        // 오늘 답변 수도 2(=한 제출의 답변 수)여야 한다
        int count = dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(KST))
                .map(st -> st.getStudyCount()).orElse(0);
        assertEquals(2, count, "통계가 두 번 더해지면 안 된다");
    }

    @Test
    @DisplayName("오늘 답변 수는 답변 개수만큼 한 번에 증가한다 (+1 × N이 아니라)")
    void batch_todayStudyCount_increasesByAnswerCount() {
        reviewService.recordAnswers(user.getId(),
                req("sub-stat", a1.getId(), true, a2.getId(), false, b1.getId(), true));

        int count = dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(KST))
                .map(s -> s.getStudyCount())
                .orElse(0);
        assertEquals(3, count);
    }
}
