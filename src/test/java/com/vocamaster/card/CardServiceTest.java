package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.CardProgress;
import com.vocamaster.review.CardProgressRepository;
import com.vocamaster.review.LearningStatus;
import com.vocamaster.review.ReviewService;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.vocamaster.AbstractIntegrationTest;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardServiceTest extends AbstractIntegrationTest {

    @Autowired private CardService cardService;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private ReviewService reviewService;
    @Autowired private EntityManager entityManager;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("card@test.com")
                .password("encoded")
                .nickname("carder")
                .build());

        deck = deckRepository.save(Deck.builder()
                .title("Test Deck")
                .user(user)
                .build());
    }

    @Test
    @DisplayName("카드 생성 성공")
    void create_success() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");

        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        assertNotNull(card.getId());
        assertEquals("apple", card.getFront());
        assertEquals("사과", card.getBack());
        assertFalse(card.getStarred());
        assertEquals(LearningStatus.UNKNOWN, card.getLearningStatus());
        assertEquals(0, card.getCorrectStreak());
        assertEquals(0, card.getWrongCount());
    }

    @Test
    @DisplayName("카드 수정 성공")
    void update_success() {
        CreateCardRequest createReq = new CreateCardRequest();
        createReq.setFront("apple");
        createReq.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), createReq);

        UpdateCardRequest updateReq = new UpdateCardRequest();
        updateReq.setBack("빨간 사과");

        CardResponse updated = cardService.update(card.getId(), user.getId(), updateReq);

        assertEquals("apple", updated.getFront());
        assertEquals("빨간 사과", updated.getBack());
    }

    @Test
    @DisplayName("읽기(요미가나) — 생성·수정·빈 문자열은 null (V14)")
    void reading_createUpdateAndBlankToNull() {
        CreateCardRequest createReq = new CreateCardRequest();
        createReq.setFront("会議");
        createReq.setBack("회의");
        createReq.setReading("  かいぎ ");
        CardResponse card = cardService.create(deck.getId(), user.getId(), createReq);
        assertEquals("かいぎ", card.getReading(), "앞뒤 공백 정리");

        UpdateCardRequest clear = new UpdateCardRequest();
        clear.setReading("");                                   // ""로 보내면 읽기 삭제
        assertNull(cardService.update(card.getId(), user.getId(), clear).getReading());

        UpdateCardRequest untouched = new UpdateCardRequest();
        untouched.setBack("회의(명사)");                          // reading 미전송(null)이면 그대로
        CardResponse u = cardService.update(card.getId(), user.getId(), untouched);
        assertNull(u.getReading());

        CreateCardRequest en = new CreateCardRequest();
        en.setFront("apple");
        en.setBack("사과");
        assertNull(cardService.create(deck.getId(), user.getId(), en).getReading(), "영어 덱은 null");
    }

    @Test
    @DisplayName("별표 토글")
    void toggleStar() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        assertFalse(card.getStarred());

        CardResponse toggled = cardService.toggleStar(card.getId(), user.getId());
        assertTrue(toggled.getStarred());

        CardResponse toggledBack = cardService.toggleStar(card.getId(), user.getId());
        assertFalse(toggledBack.getStarred());
    }

    @Test
    @DisplayName("다른 사용자의 카드 접근 불가")
    void accessDenied() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        User other = userRepository.save(User.builder()
                .email("other@test.com")
                .password("encoded")
                .nickname("other")
                .build());

        assertThrows(ForbiddenException.class, () ->
                cardService.findOne(card.getId(), other.getId()));
    }

    @Test
    @DisplayName("카드 삭제 성공")
    void delete_success() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        cardService.remove(card.getId(), user.getId());

        assertFalse(cardRepository.findById(card.getId()).isPresent());
    }
    @Test
    @DisplayName("카드 목록 - keyword로 front/back 검색 + 전체 조회")
    void findAll_search() {
        // given: 카드 3개 생성
        createCard("apple", "사과");
        createCard("banana", "바나나");
        createCard("grape", "포도");

        // when 1 & then 1: 영어 front 검색
        Page<CardResponse> en = cardService.findAll(deck.getId(), user.getId(), 0, 20, "apple", null,null);
        assertEquals(1, en.getTotalElements());
        assertEquals("apple", en.getContent().get(0).getFront());

        // when 2 & then 2: 한국어 back 검색
        Page<CardResponse> ko = cardService.findAll(deck.getId(), user.getId(), 0, 20, "바나나", null,null);
        assertEquals(1, ko.getTotalElements());
        assertEquals("banana", ko.getContent().get(0).getFront());

        // when 3 & then 3: keyword=null이면 전체
        Page<CardResponse> all = cardService.findAll(deck.getId(), user.getId(), 0, 20, null, null,null);
        assertEquals(3, all.getTotalElements());
    }

    @Test
    @DisplayName("단어 상태 — 3연속 정답에 알아요, 오답 즉시 몰라요, 다시 3연속 정답으로 회복")
    void learningStatus_followsPersistedAnswersAndRecovery() {
        Card card = cardRepository.save(Card.builder().front("apple").back("사과").deck(deck).build());
        Long cardId = card.getId();

        assertEquals(LearningStatus.UNKNOWN, cardService.findOne(cardId, user.getId()).getLearningStatus());
        assertAnswerStatus(cardId, true, LearningStatus.UNKNOWN, 1, 0);
        assertAnswerStatus(cardId, true, LearningStatus.UNKNOWN, 2, 0);
        assertAnswerStatus(cardId, true, LearningStatus.KNOWN, 3, 0);
        assertAnswerStatus(cardId, false, LearningStatus.UNKNOWN, 0, 1);
        assertAnswerStatus(cardId, true, LearningStatus.UNKNOWN, 1, 1);
        assertAnswerStatus(cardId, true, LearningStatus.UNKNOWN, 2, 1);
        assertAnswerStatus(cardId, true, LearningStatus.KNOWN, 3, 1);
    }

    @Test
    @DisplayName("별표·카드 수정 응답은 학습 상태를 유지하고 진도를 바꾸지 않는다")
    void starAndUpdate_preserveLearningStatus() {
        Card card = cardRepository.save(Card.builder().front("apple").back("사과").deck(deck).build());
        reviewService.recordAnswer(user.getId(), card.getId(), false);

        CardResponse starred = cardService.toggleStar(card.getId(), user.getId());
        assertTrue(starred.getStarred());
        assertProgress(starred, LearningStatus.UNKNOWN, 0, 1);

        UpdateCardRequest request = new UpdateCardRequest();
        request.setBack("사과(과일)");
        CardResponse updated = cardService.update(card.getId(), user.getId(), request);
        assertEquals("사과(과일)", updated.getBack());
        assertTrue(updated.getStarred());
        assertProgress(updated, LearningStatus.UNKNOWN, 0, 1);

        CardResponse unstarred = cardService.toggleStar(card.getId(), user.getId());
        assertFalse(unstarred.getStarred());
        assertProgress(unstarred, LearningStatus.UNKNOWN, 0, 1);
        entityManager.flush();
        entityManager.clear();
        assertProgress(cardService.findOne(card.getId(), user.getId()), LearningStatus.UNKNOWN, 0, 1);
    }

    @Test
    @DisplayName("페이지별 카드 목록은 본인 진도만 표시하고 새 카드·답변 전 카드를 구분한다")
    void findAll_learningStatusIsPagedAndScopedToCurrentUser() {
        User other = userRepository.save(User.builder().email("progress-other@test.com")
                .password("encoded").nickname("other").build());
        Card fresh = positionedCard("fresh", 0);
        Card unanswered = positionedCard("unanswered", 1);
        Card wrong = positionedCard("wrong", 2);
        Card learning = positionedCard("learning", 3);
        Card known = positionedCard("known", 4);
        // 동일 카드의 타 사용자 진도가 있어도 현재 사용자에게 노출하지 않는다.
        saveProgress(other, fresh, 3, 5, LocalDateTime.now());
        saveProgress(user, unanswered, 0, 0, null);
        saveProgress(user, wrong, 0, 2, LocalDateTime.now());
        saveProgress(user, learning, 2, 1, LocalDateTime.now());
        saveProgress(user, known, 3, 1, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        Page<CardResponse> first = cardService.findAll(deck.getId(), user.getId(), 0, 2, null, null, "position");
        Page<CardResponse> second = cardService.findAll(deck.getId(), user.getId(), 1, 2, null, null, "position");
        Page<CardResponse> third = cardService.findAll(deck.getId(), user.getId(), 2, 2, null, null, "position");
        assertEquals(5, first.getTotalElements());
        assertEquals(List.of(fresh.getId(), unanswered.getId()), first.map(CardResponse::getId).getContent());
        assertProgress(first.getContent().get(0), LearningStatus.UNKNOWN, 0, 0);
        assertProgress(first.getContent().get(1), LearningStatus.UNKNOWN, 0, 0);
        assertProgress(second.getContent().get(0), LearningStatus.UNKNOWN, 0, 2);
        assertProgress(second.getContent().get(1), LearningStatus.UNKNOWN, 2, 1);
        assertProgress(third.getContent().get(0), LearningStatus.KNOWN, 3, 1);
        assertProgress(cardService.findOne(fresh.getId(), user.getId()), LearningStatus.UNKNOWN, 0, 0);
        assertThrows(ForbiddenException.class, () ->
                cardService.findAll(deck.getId(), other.getId(), 0, 2, null, null, "position"));
    }

    private void assertAnswerStatus(Long cardId, boolean correct, LearningStatus status, int streak, int wrongCount) {
        ReviewAnswerResponse result = reviewService.recordAnswer(user.getId(), cardId, correct);
        assertEquals(status, result.getLearningStatus());
        assertEquals(streak, result.getCorrectStreak());
        assertEquals(wrongCount, result.getWrongCount());
        entityManager.flush();
        entityManager.clear();
        assertProgress(cardService.findOne(cardId, user.getId()), status, streak, wrongCount);
    }

    private void assertProgress(CardResponse response, LearningStatus status, int streak, int wrongCount) {
        assertEquals(status, response.getLearningStatus());
        assertEquals(streak, response.getCorrectStreak());
        assertEquals(wrongCount, response.getWrongCount());
    }

    private Card positionedCard(String front, int position) {
        return cardRepository.save(Card.builder().front(front).back(front).position(position).deck(deck).build());
    }

    private void saveProgress(User owner, Card card, int streak, int wrongCount, LocalDateTime lastReviewedAt) {
        cardProgressRepository.save(CardProgress.builder().user(owner).card(card).boxLevel(1)
                .correctStreak(streak).wrongCount(wrongCount).lastReviewedAt(lastReviewedAt)
                .nextReviewAt(LocalDateTime.now()).build());
    }

    private void createCard(String front, String back) {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront(front);
        req.setBack(back);
        cardService.create(deck.getId(), user.getId(), req);
    }

}
