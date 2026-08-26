package com.vocamaster.study;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.study.dto.DeckStatsResponse;
import com.vocamaster.study.dto.StudySummaryResponse;
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
 * OSIV off 운영 조건 회귀 테스트 (Codex 검산 2026-08-26, ADR-042 후속).
 *
 * 기존 169개가 이 결함을 못 잡은 이유: AbstractIntegrationTest의 클래스 @Transactional이
 * 테스트 바깥 트랜잭션으로 영속성 컨텍스트를 열어둔 채 서비스를 불러서, 운영(HTTP 요청)에는
 * 없는 안전망이 테스트에만 있었다. NOT_SUPPORTED가 그 안전망을 걷어 운영 조건을 재현한다.
 * fixture는 TransactionTemplate으로 '커밋'까지 마쳐 진짜 DB 상태로 만든다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudyOsivRegressionTest extends AbstractIntegrationTest {

    @Autowired private StudyService studyService;
    @Autowired private StudySessionRepository sessionRepository;
    @Autowired private StudyRecordRepository recordRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txManager;

    private User user;
    private Deck deck;
    private StudySession session;        // 기록 2개 (안다 1 / 모른다 1)
    private StudySession emptySession;   // 기록 0개

    @BeforeEach
    void fixtureCommitted() {
        new TransactionTemplate(txManager).execute(status -> {
            long tag = System.nanoTime();
            user = userRepository.save(User.builder()
                    .email("osiv_" + tag + "@test.com").password("encoded").nickname("경계러").build());
            deck = deckRepository.save(Deck.builder().title("osiv " + tag).user(user).build());
            Card known = cardRepository.save(Card.builder().front("alpha").back("알파").deck(deck).build());
            Card unknown = cardRepository.save(Card.builder().front("beta").back("베타").deck(deck).build());
            session = sessionRepository.save(StudySession.builder()
                    .deck(deck).user(user).direction("front_to_back").starredOnly(false).build());
            recordRepository.save(StudyRecord.builder().session(session).card(known).known(true).build());
            recordRepository.save(StudyRecord.builder().session(session).card(unknown).known(false).build());
            emptySession = sessionRepository.save(StudySession.builder()
                    .deck(deck).user(user).direction("front_to_back").starredOnly(false).build());
            return null;
        });
    }

    @AfterEach
    void cleanUp() {
        // NOT_SUPPORTED라 자동 롤백이 없다 — 덱 삭제의 DB CASCADE(V13·V15)가 카드·세션·기록을 정리
        new TransactionTemplate(txManager).execute(status -> {
            deckRepository.deleteById(deck.getId());
            userRepository.deleteById(user.getId());
            return null;
        });
    }

    @Test
    @DisplayName("세션 요약 — 트랜잭션 없는 요청 경계에서 500이 아니라 200 + 정확한 집계")
    void summary_worksOutsideTransaction() {
        StudySummaryResponse res = assertDoesNotThrow(
                () -> studyService.getSessionSummary(session.getId(), user.getId()),
                "OSIV 없는 운영 경계에서 LAZY 초기화가 터지면 안 된다");

        assertEquals(2, res.getTotal());
        assertEquals(1, res.getKnown());
        assertEquals(1, res.getUnknown());
        assertEquals(50, res.getAccuracy());
        assertEquals(1, res.getUnknownCards().size());
        assertEquals("beta", res.getUnknownCards().get(0).getFront(), "모르는 카드만 목록에");
        assertTrue(res.getDeckTitle().startsWith("osiv"), "덱 제목(LAZY 프록시 필드)까지 채워져야");
    }

    @Test
    @DisplayName("기록 0개 세션 요약 — 빈 컬렉션도 같은 경계에서 정상")
    void summary_emptySession() {
        StudySummaryResponse res = assertDoesNotThrow(
                () -> studyService.getSessionSummary(emptySession.getId(), user.getId()));
        assertEquals(0, res.getTotal());
        assertEquals(0, res.getAccuracy());
        assertTrue(res.getUnknownCards().isEmpty());
    }

    @Test
    @DisplayName("덱 통계 — 세션들의 기록 컬렉션 순회가 요청 경계에서 정상 + 집계 정확")
    void deckStats_worksOutsideTransaction() {
        DeckStatsResponse res = assertDoesNotThrow(
                () -> studyService.getDeckStats(deck.getId(), user.getId()));

        assertEquals(2, res.getTotalCards());
        assertEquals(2, res.getStudy().getTotalSessions(), "빈 세션 포함 2개");
        assertEquals(2, res.getStudy().getTotalRecords());
        assertEquals(1, res.getStudy().getKnown());
        assertEquals(1, res.getStudy().getUnknown());
        assertEquals(50, res.getStudy().getAccuracy());
    }
}
