package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.stats.StatsService;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 인기 점수 study 항 — StudyRecordedEvent의 두 번째 구독자 (ADR-038).
 *
 * NOT_SUPPORTED: AFTER_COMMIT 리스너는 진짜 커밋이 있어야 실행됨 (TodaySummaryCacheTest와 같은 이유).
 * recordStudy는 자체 @Transactional이라 테스트에서 직접 부르면 커밋 → 리스너(REQUIRES_NEW) → 출석부 INSERT.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeckStudyRankingListenerTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private StatsService statsService;
    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private DeckStudyDayRepository studyDayRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;

    private User owner;
    private User learner;
    private Deck root;            // owner의 PUBLIC 원본
    private final List<User> extraUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();
        owner = userRepository.save(User.builder()
                .email("owner_" + tag + "@test.com").password("encoded").nickname("원작자").build());
        learner = userRepository.save(User.builder()
                .email("learner_" + tag + "@test.com").password("encoded").nickname("학습자").build());
        root = deckRepository.save(Deck.builder()
                .title("study " + tag).visibility(DeckVisibility.PUBLIC).user(owner).build());
        cardRepository.save(Card.builder().front("event").back("이벤트").deck(root).build());
    }

    @AfterEach
    void cleanUp() {
        List<User> all = new ArrayList<>(List.of(learner, owner));
        all.addAll(extraUsers);
        // 덱 전부 먼저 (deck_study_days는 deck FK CASCADE로 같이 사라짐) → 그다음 사용자.
        // 사용자를 먼저 지우면 남의 덱에 매달린 출석부 행이 user FK(RESTRICT)에 걸림
        for (User u : all) {
            for (Deck d : deckRepository.findByUserIdOrderByCreatedAtDesc(u.getId())) {
                cardRepository.deleteAll(cardRepository.findByDeckId(d.getId()));
                deckRepository.deleteById(d.getId());
            }
        }
        for (User u : all) {
            dailyUserStatRepository.findByUserIdAndStatDate(u.getId(), LocalDate.now(KST))
                    .ifPresent(dailyUserStatRepository::delete);
            userRepository.delete(u);
        }
    }

    private long studyCountOf(Deck d) {
        return deckRepository.findById(d.getId()).orElseThrow().getStudyCount();
    }

    @Test
    @DisplayName("타인이 복사본으로 학습 → 점수는 원본에 (복사본 0). 같은 날 두 번째는 +0 (하루 1회)")
    void copyStudy_attributesToOriginal_oncePerDay() {
        Deck copy = deckRepository.findById(deckService.copy(root.getId(), learner.getId()).getId()).orElseThrow();

        statsService.recordStudy(learner.getId(), copy.getId());
        assertEquals(1, studyCountOf(root), "원본에 +1");
        assertEquals(0, studyCountOf(copy), "복사본은 0");
        assertEquals(1, studyDayRepository.countByDeckId(root.getId()), "출석부 1행");

        statsService.recordStudy(learner.getId(), copy.getId());
        statsService.recordStudy(learner.getId(), copy.getId());
        assertEquals(1, studyCountOf(root), "같은 날 반복 학습은 +0 — unique 출석부가 막음");
        assertEquals(1, studyDayRepository.countByDeckId(root.getId()));
    }

    @Test
    @DisplayName("주인이 자기 원본을 학습 → 0점 (상한 없는 자기 행동은 점수 제외, 자기 복사와 같은 기준)")
    void ownerStudy_noScore() {
        statsService.recordStudy(owner.getId(), root.getId());
        assertEquals(0, studyCountOf(root));
        assertEquals(0, studyDayRepository.countByDeckId(root.getId()), "출석부도 안 남음");
    }

    @Test
    @DisplayName("복사본의 복사본 — original_deck_id가 최상위 원본으로 평탄화되어 점수가 체인을 안 탄다")
    void copyOfCopy_flattensToRoot() {
        Long copy1Id = deckService.copy(root.getId(), learner.getId()).getId();
        Long copy2Id = deckService.copy(copy1Id, learner.getId()).getId();   // 자기 복사 허용(PRIVATE 본인 덱)

        assertEquals(root.getId(), deckRepository.findById(copy2Id).orElseThrow().getOriginalDeckId(),
                "직전 부모(copy1)가 아니라 최상위 원본(root)을 가리켜야");

        statsService.recordStudy(learner.getId(), copy2Id);
        assertEquals(1, studyCountOf(root), "체인 끝 복사본으로 학습해도 원본에");
        assertEquals(0, deckRepository.findById(copy1Id).orElseThrow().getStudyCount(), "중간 복사본엔 0");
    }

    @Test
    @DisplayName("동시 학습 6명 — X 잠금 선점(FOR UPDATE) 덕에 데드락 없이 정확히 6")
    void concurrentLearners_noDeadlock_exactCount() throws Exception {
        int n = 6;
        for (int i = 0; i < n; i++) {
            extraUsers.add(userRepository.save(User.builder()
                    .email("l" + i + "_" + System.nanoTime() + "@test.com").password("encoded").nickname("동시" + i).build()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (User u : extraUsers) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    statsService.recordStudy(u.getId(), root.getId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                    e.printStackTrace();
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, failures.get(), "데드락·예외 없어야 (INSERT 먼저면 FK S→X 승급 교착이 여기서 재현됨)");
        assertEquals(n, studyCountOf(root), "6명 각 1점");
        assertEquals(n, studyDayRepository.countByDeckId(root.getId()));
    }
}
