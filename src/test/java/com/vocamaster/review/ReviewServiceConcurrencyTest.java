package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 동시 답변 충돌 검증 — @Version 낙관적 락 (ADR-029).
 *
 * 자동 롤백(@Transactional)을 끄는 이유 2개:
 * 1) 트랜잭션 B(REQUIRES_NEW)는 '커밋된' 데이터만 볼 수 있음 — 테스트 트랜잭션 안의 미커밋 데이터는 안 보임
 * 2) 버전 충돌은 '커밋하는 순간' 터지는데, 자동 롤백 모드에는 진짜 커밋이 없음
 * → 대신 만든 데이터는 @AfterEach에서 손으로 삭제 (FK 역순: progress → card → deck → user)
 *
 * 스레드 2개를 쓰지 않는 이유: 진짜 스레드는 타이밍 복불복이라 테스트가 flaky해짐.
 * "A가 읽음 → B가 끼어들어 커밋 → A가 낡은 버전으로 커밋 시도"라는
 * 동시 요청의 최악 인터리빙을 트랜잭션 2개로 결정적으로 재현한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReviewServiceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PlatformTransactionManager txManager;

    private User user;
    private Deck deck;
    private Card card;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("concurrency_" + System.nanoTime() + "@test.com")    // 컨테이너 재사용 대비 유니크
                .password("encoded")
                .nickname("동시성")
                .build());
        deck = deckRepository.save(Deck.builder().title("Concurrency Deck").user(user).build());
        card = cardRepository.save(Card.builder().front("race").back("경쟁 상태").deck(deck).build());
    }

    @AfterEach
    void cleanUp() {
        cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .ifPresent(cardProgressRepository::delete);
        cardRepository.delete(card);
        deckRepository.delete(deck);
        userRepository.delete(user);
    }

    @Test
    @DisplayName("동시 답변 — 낡은 버전으로 커밋하는 쪽은 OptimisticLock 예외, DB엔 승자만 남음")
    void concurrentAnswer_staleVersion_throws() {
        // 성적표 생성 + 커밋 (box 1 생성 → 정답이라 2, streak 1)
        reviewService.recordAnswer(user.getId(), card.getId(), true);

        TransactionTemplate txA = new TransactionTemplate(txManager);
        TransactionTemplate txB = new TransactionTemplate(txManager);
        txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                txA.execute(statusA -> {
                    // 요청 A: 성적표를 읽음 — 이 순간의 version을 기억
                    CardProgress stale = cardProgressRepository
                            .findByUserIdAndCardId(user.getId(), card.getId()).orElseThrow();

                    // 요청 B: A가 읽은 '뒤에' 끼어들어 정식 답변 완료 + 커밋 (version + 1)
                    txB.execute(statusB ->
                            reviewService.recordAnswer(user.getId(), card.getId(), true));

                    // 요청 A: 낡은 버전 기반 수정 → execute 끝 = 커밋 시도
                    //         UPDATE ... AND version = 낡은값 → 0행 → 예외
                    stale.setBoxLevel(stale.getBoxLevel() + 1);
                    return null;
                }));

        // 승자는 B 하나뿐 — A의 낡은 쓰기는 DB에 흔적이 없어야 함 (lost update 방지 확인)
        CardProgress finalState = cardProgressRepository
                .findByUserIdAndCardId(user.getId(), card.getId()).orElseThrow();
        assertEquals(3, finalState.getBoxLevel(), "B의 답변(2→3)만 반영");
        assertEquals(2, finalState.getCorrectStreak(), "정상 반영된 정답은 2회뿐 (생성 1 + B 1)");
    }
}
