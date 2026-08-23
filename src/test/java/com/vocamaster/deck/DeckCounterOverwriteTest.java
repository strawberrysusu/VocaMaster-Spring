package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 메타데이터 수정 vs 카운터 증가 (Codex 전수 감사 2026-08-23).
 * Deck 저장이 전체 컬럼 UPDATE면 "읽었을 때의 likeCount"를 같이 써서 그 사이 원자적 +1이 증발한다 → @DynamicUpdate.
 * NOT_SUPPORTED: 트랜잭션 두 개가 진짜로 겹쳐야 해서 커밋 데이터 사용.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeckCounterOverwriteTest extends AbstractIntegrationTest {

    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txManager;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("ow_" + System.nanoTime() + "@test.com").password("encoded").nickname("덮어쓰기").build());
        deck = deckRepository.save(Deck.builder().title("원래 제목").user(user).build());
    }

    @AfterEach
    void cleanUp() {
        deckRepository.deleteById(deck.getId());
        userRepository.delete(user);
    }

    @Test
    @DisplayName("제목 수정 트랜잭션 도중 다른 트랜잭션이 like +1 → 제목도 바뀌고 카운터도 1 (증발 없음)")
    void metaUpdate_doesNotOverwriteCounters() {
        Long deckId = deck.getId();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        tx.execute(s1 -> {
            Deck loaded = deckRepository.findById(deckId).orElseThrow();        // likeCount 0을 읽음
            loaded.setTitle("수정된 제목");
            TransactionTemplate other = new TransactionTemplate(txManager);
            other.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            other.execute(s2 -> deckRepository.incrementLikeCount(deckId));      // 그 사이 원자적 +1 (커밋됨)
            deckRepository.save(loaded);                                          // 전체 컬럼 UPDATE였다면 likeCount=0으로 덮어씀
            return null;
        });

        Deck after = deckRepository.findById(deckId).orElseThrow();
        assertEquals("수정된 제목", after.getTitle());
        assertEquals(1, after.getLikeCount(), "@DynamicUpdate — 바뀐 컬럼(title)만 UPDATE, 카운터 보존");
    }
}
