package com.vocamaster.card;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.JwtProvider;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 별표 일괄 설정 API — 경로·인증·입력 검사·응답 모양 (로직은 CardServiceTest) */
@AutoConfigureMockMvc
class CardStarBulkHttpTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    private Deck deck;
    private Card card;
    private String token;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder().email("star-http@test.com").password("encoded").nickname("별표").build());
        deck = deckRepository.save(Deck.builder().title("별표 HTTP").user(user).build());
        card = cardRepository.save(Card.builder().front("感謝").back("감사").deck(deck).build());
        token = jwtProvider.createAccessToken(user.getId(), user.getEmail());
    }

    private String body(String json) {
        return json.replace('\'', '"');
    }

    @Test
    @DisplayName("PATCH /decks/{id}/cards/star — 200, 바뀐 카드 id를 돌려준다")
    void setStarred_ok() throws Exception {
        mvc.perform(patch("/decks/{deckId}/cards/star", deck.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{'cardIds':[" + card.getId() + "],'starred':true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starred").value(true))
                .andExpect(jsonPath("$.changedIds[0]").value(card.getId()));
    }

    @Test
    @DisplayName("빈 목록·값 없음은 400")
    void setStarred_validation() throws Exception {
        mvc.perform(patch("/decks/{deckId}/cards/star", deck.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{'cardIds':[],'starred':true}")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/decks/{deckId}/cards/star", deck.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{'cardIds':[" + card.getId() + "]}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인 없이는 거절")
    void setStarred_requiresLogin() throws Exception {
        mvc.perform(patch("/decks/{deckId}/cards/star", deck.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{'cardIds':[" + card.getId() + "],'starred':true}")))
                .andExpect(status().is4xxClientError());
    }
}
