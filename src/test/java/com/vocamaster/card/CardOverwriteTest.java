package com.vocamaster.card;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.dto.SetStarredRequest;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 별표 저장 vs 카드 수정 (Codex 검산 2026-11-14, P2).
 * Card 저장이 전체 컬럼 UPDATE면 별표만 켜려던 저장이 '읽었을 때의 설명(memo)'을 같이 써서 그 사이 고친 설명이 되돌아가고,
 * 반대로 설명 저장이 그 사이 켜진 별표를 끈다 → @DynamicUpdate + 수정·토글을 한 트랜잭션으로 (Deck과 같은 처방, ADR-029 보완).
 * NOT_SUPPORTED: 트랜잭션 두 개가 진짜로 겹쳐야 해서 커밋 데이터 사용.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CardOverwriteTest extends AbstractIntegrationTest {

    @Autowired private CardService cardService;
    @Autowired private CardRepository cardRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txManager;

    private User user;
    private Deck deck;
    private Card card;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("co_" + System.nanoTime() + "@test.com").password("encoded").nickname("덮어쓰기").build());
        deck = deckRepository.save(Deck.builder().title("덮어쓰기 덱").user(user).build());
        card = cardRepository.save(Card.builder()
                .front("meticulous").back("꼼꼼한").memo("옛 설명").starred(false).deck(deck).build());
    }

    @AfterEach
    void cleanUp() {
        cardRepository.deleteById(card.getId());
        deckRepository.deleteById(deck.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("별표 일괄 저장 도중 설명 수정이 커밋 → 별표도 켜지고 새 설명도 남는다")
    void starSave_doesNotRevertMemoEdit() {
        newTx().execute(s1 -> {
            cardService.setStarred(deck.getId(), user.getId(), star(true, card.getId()));   // 카드를 읽음 (memo = 옛 설명)
            newTx().execute(s2 -> cardService.update(card.getId(), user.getId(), memo("새 설명")));   // 그 사이 설명 수정 커밋
            return null;
        });   // 전체 컬럼 UPDATE였다면 여기서 memo = 옛 설명으로 덮어씀

        Card after = cardRepository.findById(card.getId()).orElseThrow();
        assertTrue(after.getStarred());
        assertEquals("새 설명", after.getMemo(), "@DynamicUpdate — 별표 저장은 starred만 UPDATE");
    }

    @Test
    @DisplayName("카드 수정 도중 별표가 켜져 커밋 → 새 설명도 남고 별표도 켜진 채로 남는다")
    void memoEdit_doesNotRevertStar() {
        newTx().execute(s1 -> {
            cardRepository.findById(card.getId()).orElseThrow();   // 수정 트랜잭션이 카드를 먼저 읽음 (starred = false)
            newTx().execute(s2 -> cardService.setStarred(deck.getId(), user.getId(), star(true, card.getId())));   // 그 사이 별표 커밋
            cardService.update(card.getId(), user.getId(), memo("새 설명"));   // 같은 트랜잭션 — 읽어 둔 카드(starred = false)를 고쳐 저장
            return null;
        });

        Card after = cardRepository.findById(card.getId()).orElseThrow();
        assertEquals("새 설명", after.getMemo());
        assertTrue(after.getStarred(), "@DynamicUpdate — 설명 저장은 memo만 UPDATE, 그 사이 켜진 별표 보존");
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private static SetStarredRequest star(boolean on, Long cardId) {
        SetStarredRequest req = new SetStarredRequest();
        req.setCardIds(List.of(cardId));
        req.setStarred(on);
        return req;
    }

    private static UpdateCardRequest memo(String memo) {
        UpdateCardRequest req = new UpdateCardRequest();
        req.setMemo(memo);
        return req;
    }

    @Test
    @DisplayName("카드 수정·별표 토글은 트랜잭션 안에서 — 무트랜잭션이면 읽은 카드가 detached라 save()의 merge가 전체 컬럼을 되돌린다")
    void updateAndToggle_areTransactional() throws Exception {
        // 운영에서는 이 둘이 바깥 트랜잭션 없이 불린다. @Transactional이 빠지면 위 테스트들은 (바깥 트랜잭션 안이라) 그대로 통과하지만
        // 실제로는 detached merge가 낡은 값 전체를 덮어써 @DynamicUpdate가 소용없다 — 그래서 붙어 있는지 직접 본다 (11/14 검토)
        assertNotNull(CardService.class.getMethod("update", Long.class, Long.class, UpdateCardRequest.class)
                .getAnnotation(Transactional.class), "CardService.update에 @Transactional");
        assertNotNull(CardService.class.getMethod("toggleStar", Long.class, Long.class)
                .getAnnotation(Transactional.class), "CardService.toggleStar에 @Transactional");
    }

    @Test
    @DisplayName("별표 토글 두 번이 겹쳐도 두 번 다 뒤집힌다 — 읽고 뒤집어 쓰면 같은 값을 읽은 둘이 한 번으로 줄었다")
    void overlappingToggles_bothApply() {
        newTx().execute(s1 -> {
            cardRepository.findById(card.getId()).orElseThrow();   // 첫 토글이 먼저 읽음 (starred = false)
            newTx().execute(s2 -> cardService.toggleStar(card.getId(), user.getId()));   // 다른 탭의 토글이 먼저 커밋 (→ 켜짐)
            cardService.toggleStar(card.getId(), user.getId());   // 읽어 둔 false를 뒤집어 쓰면 다시 '켜짐' — 두 번 눌렀는데 켜진 채
            return null;
        });

        assertFalse(cardRepository.findById(card.getId()).orElseThrow().getStarred(), "두 번 뒤집혀 다시 꺼짐");
    }
}
