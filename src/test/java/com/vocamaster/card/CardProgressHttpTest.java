package com.vocamaster.card;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.JwtProvider;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.CardProgress;
import com.vocamaster.review.CardProgressRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 요청처럼 영속성 컨텍스트가 닫힌 상태에서 진도 조회와 JSON 직렬화까지 검증한다. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CardProgressHttpTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private CardRepository cardRepository;
    @Autowired private CardProgressRepository progressRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txManager;

    private User user;
    private Deck deck;
    private Card known;
    private Card unknown;
    private String accessToken;

    @BeforeEach
    void commitFixture() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            user = userRepository.save(User.builder()
                    .email("card-http-" + System.nanoTime() + "@test.com")
                    .password("encoded").nickname("상태 HTTP 검증").build());
            deck = deckRepository.save(Deck.builder().title("학습 상태 HTTP").user(user).build());
            known = cardRepository.save(Card.builder().front("known").back("아는 단어")
                    .deck(deck).position(0).build());
            unknown = cardRepository.save(Card.builder().front("unknown").back("모르는 단어")
                    .deck(deck).position(1).starred(true).build());
            LocalDateTime reviewedAt = LocalDateTime.of(2026, 9, 25, 12, 0);
            progressRepository.save(CardProgress.builder().user(user).card(known)
                    .boxLevel(4).correctStreak(3).wrongCount(1).lastReviewedAt(reviewedAt)
                    .nextReviewAt(reviewedAt.plusDays(7)).build());
            progressRepository.save(CardProgress.builder().user(user).card(unknown)
                    .boxLevel(1).correctStreak(0).wrongCount(2).lastReviewedAt(reviewedAt)
                    .nextReviewAt(reviewedAt.plusMinutes(10)).build());
        });
        accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
    }

    @AfterEach
    void removeCommittedFixture() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            // DB CASCADE가 덱의 카드와 card_progress까지 정리한다.
            deckRepository.deleteById(deck.getId());
            userRepository.deleteById(user.getId());
        });
    }

    @Test
    @DisplayName("OSIV off — 학습한 카드 목록·상세 HTTP 응답이 상태와 진도를 JSON으로 반환")
    void listAndDetails_serializeProgressOutsideTransaction() throws Exception {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "테스트 트랜잭션이 LAZY 조회 문제를 가리면 안 된다");

        mvc.perform(get("/decks/{deckId}/cards", deck.getId())
                        .param("sort", "position")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(known.getId()))
                .andExpect(jsonPath("$.content[0].learningStatus").value("KNOWN"))
                .andExpect(jsonPath("$.content[0].correctStreak").value(3))
                .andExpect(jsonPath("$.content[0].wrongCount").value(1))
                .andExpect(jsonPath("$.content[1].id").value(unknown.getId()))
                .andExpect(jsonPath("$.content[1].learningStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.content[1].correctStreak").value(0))
                .andExpect(jsonPath("$.content[1].wrongCount").value(2))
                .andExpect(jsonPath("$.content[1].starred").value(true));

        mvc.perform(get("/cards/{id}", known.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(known.getId()))
                .andExpect(jsonPath("$.learningStatus").value("KNOWN"))
                .andExpect(jsonPath("$.correctStreak").value(3))
                .andExpect(jsonPath("$.wrongCount").value(1));

        mvc.perform(get("/cards/{id}", unknown.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learningStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.correctStreak").value(0))
                .andExpect(jsonPath("$.wrongCount").value(2))
                .andExpect(jsonPath("$.starred").value(true));
    }
}
