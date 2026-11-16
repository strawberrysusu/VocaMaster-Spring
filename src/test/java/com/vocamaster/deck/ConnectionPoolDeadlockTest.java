package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.stats.StatsService;
import com.vocamaster.study.event.StudyRecordedEvent;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 연결 풀 교착 (11/16 Codex 7차 검산, ADR-061).
 *
 * <p>학습 기록이 커밋되면 인기 점수 리스너(DeckStudyRankingListener)가 새 트랜잭션(REQUIRES_NEW)을 연다. 그때 요청 스레드가
 * 방금 쓴 연결을 아직 쥐고 있으면 연결을 하나 더 요구하게 되고, 풀 크기만큼의 요청이 동시에 그 자리에 오면 모두가 남의 연결을
 * 기다리기만 한다(운영 기본 풀 10개 · 대기 30초 — 그동안 앱 전체가 멈췄다).</p>
 *
 * <p>여기서는 풀을 3개로 줄이고, 학습자 셋이 모두 '커밋 직전(연결을 하나씩 쥔 채)'에 모인 뒤에 함께 커밋하게 해서 그 상황을 매번 만든다.
 * 연결 대기 시간도 2초로 줄였다 — 교착이면 2초 뒤 누군가 포기해야 나머지가 진행한다.</p>
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=2000"})
@Import(ConnectionPoolDeadlockTest.GateConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // AFTER_COMMIT 리스너는 진짜 커밋이 있어야 돈다
class ConnectionPoolDeadlockTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int POOL = 3;

    /** 학습 트랜잭션의 커밋 직전에 서서, 정해진 수가 다 모이면 함께 지나가게 하는 문 (테스트 전용) */
    static class Gate {
        volatile CyclicBarrier barrier;   // null이면 그냥 통과

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void holdBeforeCommit(StudyRecordedEvent event) {
            CyclicBarrier waiting = barrier;
            if (waiting == null) return;
            try {
                waiting.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("커밋 직전에 셋이 모이지 못했다", e);
            }
        }
    }

    @TestConfiguration
    static class GateConfig {
        @Bean
        Gate gate() {
            return new Gate();
        }
    }

    @Autowired private Gate gate;
    @Autowired private StatsService statsService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private DeckStudyDayRepository studyDayRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;

    private User owner;
    private Deck root;                                    // owner의 공개 덱 — 남이 공부하면 인기 점수 +1
    private final List<User> learners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();
        owner = userRepository.save(User.builder()
                .email("pool_owner_" + tag + "@test.com").password("encoded").nickname("원작자").build());
        root = deckRepository.save(Deck.builder()
                .title("pool " + tag).visibility(DeckVisibility.PUBLIC).user(owner).build());
        cardRepository.save(Card.builder().front("pool").back("풀").deck(root).build());
        for (int i = 0; i < POOL; i++) {
            learners.add(userRepository.save(User.builder()
                    .email("pool_learner" + i + "_" + tag + "@test.com").password("encoded").nickname("학습자" + i).build()));
        }
    }

    @AfterEach
    void cleanUp() {
        gate.barrier = null;
        // 덱 먼저(출석부는 덱 FK CASCADE로 같이 사라진다) → 그다음 사용자 (DeckStudyRankingListenerTest와 같은 순서)
        cardRepository.deleteAll(cardRepository.findByDeckId(root.getId()));
        deckRepository.deleteById(root.getId());
        List<User> all = new ArrayList<>(learners);
        all.add(owner);
        for (User u : all) {
            dailyUserStatRepository.findByUserIdAndStatDate(u.getId(), LocalDate.now(KST))
                    .ifPresent(dailyUserStatRepository::delete);
            userRepository.delete(u);
        }
    }

    @Test
    @DisplayName("풀 크기만큼의 학습 기록이 동시에 커밋돼도 서로의 연결을 기다리지 않는다 — 셋 다 인기 점수에 반영된다")
    void concurrentCommits_doNotWaitForEachOthersConnection() throws Exception {
        gate.barrier = new CyclicBarrier(POOL);   // 셋 다 '커밋 직전(연결을 하나씩 쥠 = 풀이 빔)'에 모이면 함께 통과

        ExecutorService threads = Executors.newFixedThreadPool(POOL);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (User learner : learners) {
                done.add(threads.submit(() -> statsService.recordStudy(learner.getId(), root.getId())));
            }
            for (Future<?> f : done) {
                f.get(20, TimeUnit.SECONDS);      // 학습 기록 자체는 어느 쪽이든 커밋된다 (리스너의 실패는 로그로만 남는다)
            }
        } finally {
            threads.shutdownNow();
        }

        // 고치기 전: 셋 다 원래 연결을 쥔 채 두 번째 연결을 기다린다 → 누군가 2초 뒤 포기해야 나머지가 진행한다 → 셋이 다 반영될 수 없다
        assertEquals(POOL, studyDayRepository.countByDeckId(root.getId()), "출석부 — 학습자 셋 모두");
        assertEquals(POOL, deckRepository.findById(root.getId()).orElseThrow().getStudyCount(), "공개 덱의 study_count");
    }
}
