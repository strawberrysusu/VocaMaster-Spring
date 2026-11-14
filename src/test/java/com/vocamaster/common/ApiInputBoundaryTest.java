package com.vocamaster.common;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.JwtProvider;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입력 경계 — 화면은 막지만 API로는 들어오던 값들 (Codex 검산 2026-11-14).
 * 빈 단어로 카드 수정(200으로 저장됨), 72바이트 넘는 비밀번호(BCrypt 예외 → 500), 답안 목록의 null(NPE → 500).
 * 모두 400(입력 잘못)이어야 한다. 자동 롤백 모드 — MockMvc 요청도 테스트 트랜잭션 안에서 돈다.
 */
@AutoConfigureMockMvc
class ApiInputBoundaryTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password-1234";

    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    private User user;
    private Card card;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().email("boundary_" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD)).nickname("경계").build());
        Deck deck = deckRepository.save(Deck.builder().title("경계 덱").user(user).build());
        card = cardRepository.save(Card.builder().front("meticulous").back("꼼꼼한").deck(deck).build());
        token = jwtProvider.createAccessToken(user.getId(), user.getEmail());
    }

    // ── 카드 수정: 안 보낸 칸(null)은 그대로, 보낸 칸이 비었으면 거부 — 만들 때(@NotBlank)와 같은 규칙

    @Test
    @DisplayName("카드 수정에 빈 단어·공백뿐인 뜻 → 400, 카드는 그대로")
    void updateCard_blankFrontOrBack_400() throws Exception {
        mvc.perform(json(patch("/cards/{id}", card.getId()), "{'front':'','back':'   '}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("front: 비워 둘 수 없습니다")))
                .andExpect(jsonPath("$.message", containsString("back: 비워 둘 수 없습니다")));

        Card after = cardRepository.findById(card.getId()).orElseThrow();
        assertEquals("meticulous", after.getFront());
        assertEquals("꼼꼼한", after.getBack());
    }

    @Test
    @DisplayName("카드 수정에서 설명만 보내면 200 — 안 보낸 단어·뜻은 검사하지 않는다(PATCH)")
    void updateCard_onlyMemo_200() throws Exception {
        mvc.perform(json(patch("/cards/{id}", card.getId()), "{'memo':'아주 세심한'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.front").value("meticulous"))
                .andExpect(jsonPath("$.memo").value("아주 세심한"));
    }

    // ── 비밀번호: BCrypt는 72바이트까지만 받는다 (영문·숫자 1자 = 1바이트, 한글 1자 = 3바이트)

    @Test
    @DisplayName("가입 비밀번호 73바이트(영문 73자) → 400, 72바이트는 가입된다")
    void register_passwordOver72Bytes_400() throws Exception {
        mvc.perform(json(post("/auth/register"), register("long73", "a".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("비밀번호가 너무 깁니다")));
        mvc.perform(json(post("/auth/register"), register("long72", "a".repeat(72))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("가입 비밀번호 한글 25자(75바이트) → 400 — 글자 수가 아니라 바이트로 센다")
    void register_koreanPasswordOver72Bytes_400() throws Exception {
        mvc.perform(json(post("/auth/register"), register("long-ko", "가".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("비밀번호가 너무 깁니다")));
    }

    @Test
    @DisplayName("비밀번호 변경의 새 비밀번호 73바이트 → 400")
    void changePassword_newPasswordOver72Bytes_400() throws Exception {
        mvc.perform(json(patch("/users/me/password"),
                        "{'currentPassword':'" + PASSWORD + "','newPassword':'" + "b".repeat(73) + "'}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("비밀번호가 너무 깁니다")));
        assertEquals(true, passwordEncoder.matches(PASSWORD, userRepository.findById(user.getId()).orElseThrow().getPassword()),
                "거부됐으니 비밀번호는 그대로");
    }

    @Test
    @DisplayName("로그인에 73바이트 비밀번호 → 401 (500 아님)")
    void login_passwordOver72Bytes_401() throws Exception {
        mvc.perform(json(post("/auth/login"), "{'email':'" + user.getEmail() + "','password':'" + "c".repeat(73) + "'}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 일괄 제출: 목록 안의 null 원소

    @Test
    @DisplayName("일괄 제출 answers에 null 원소 → 400 (예전엔 NPE로 500)")
    void batchAnswers_nullItem_400() throws Exception {
        mvc.perform(json(post("/reviews/answers/batch"), "{'submissionId':'null-item','answers':[null]}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace('\'', '"'));
    }

    private static String register(String name, String password) {
        return "{'email':'" + name + "_" + System.nanoTime() + "@test.com','password':'" + password + "','nickname':'" + name + "'}";
    }

    // ── 덱 수정·가입: 같은 종류의 빈칸·길이 구멍 (11/14 검토)

    @Test
    @DisplayName("덱 제목을 공백으로 수정 → 400, 설명만 보내면 200")
    void updateDeck_blankTitle_400() throws Exception {
        Long deckId = card.getDeck().getId();
        mvc.perform(json(patch("/decks/{id}", deckId), "{'title':'   '}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("title: 비워 둘 수 없습니다")));
        mvc.perform(json(patch("/decks/{id}", deckId), "{'description':'설명만'}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("가입 닉네임 31자 → 400 (프로필 수정과 같은 1~30자), 30자는 가입된다")
    void register_nicknameOver30_400() throws Exception {
        mvc.perform(json(post("/auth/register"),
                        "{'email':'nick31_" + System.nanoTime() + "@test.com','password':'" + PASSWORD + "','nickname':'" + "닉".repeat(31) + "'}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("닉네임은 1~30자")));
        mvc.perform(json(post("/auth/register"),
                        "{'email':'nick30_" + System.nanoTime() + "@test.com','password':'" + PASSWORD + "','nickname':'" + "닉".repeat(30) + "'}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("카드 수정에 전각 공백(　)만 → 400 — 일본어 입력기가 넣는 공백도 빈칸 (만들 때·가져오기와 같은 규칙)")
    void updateCard_fullWidthSpaceOnly_400() throws Exception {
        mvc.perform(json(patch("/cards/{id}", card.getId()), "{'front':'\u3000\u3000'}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("front: 비워 둘 수 없습니다")));
    }

    @Test
    @DisplayName("아주 큰 ?page= → 빈 페이지 200 (예전엔 page × size가 int를 넘어 500)")
    void hugePage_isEmptyPageNot500() throws Exception {
        mvc.perform(get("/public/decks").param("page", "200000000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("주소창으로 /auth/login(GET) → 405, 글자 본문으로 POST → 415 — 500 '서버 내부 오류'가 아니다")
    void wrongMethodOrContentType_is4xx() throws Exception {
        mvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청입니다"));
        mvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=a"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("만들 때도 같은 빈칸 규칙 — 전각 공백 제목·단어, 한글 채움 문자(ㅤ) 제목은 400 (탐색에 빈 제목 방지)")
    void create_blankLikeText_400() throws Exception {
        mvc.perform(json(post("/decks"), "{'title':'\u3000'}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("title: 비워 둘 수 없습니다")));
        mvc.perform(json(post("/decks/{deckId}/cards", card.getDeck().getId()), "{'front':'\u3000','back':'뜻'}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("front: 비워 둘 수 없습니다")));
        mvc.perform(json(patch("/decks/{id}", card.getDeck().getId()), "{'title':'\u3164'}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(patch("/decks/{id}", card.getDeck().getId()), "{'title':'\u200B'}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/decks"), "{'title':'N1 단어'}"))
                .andExpect(status().isOk());
    }
}
